package com.teammind.llm;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LLM 流式响应支持
 */
public interface StreamingLLMClient extends LLMClient {

    /**
     * 流式聊天 - 返回 Flux
     */
    Flux<String> streamChatFlux(LLMRequest request);

    /**
     * 流式聊天 - 返回 SSE Emitter
     */
    default SseEmitter streamChatSSE(LLMRequest request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                Flux<String> flux = streamChatFlux(request);
                flux.doOnNext(chunk -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(chunk, MediaType.TEXT_PLAIN));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .doOnComplete(() -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data("[DONE]"));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(emitter::completeWithError)
                .subscribe();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(executor::shutdown);
        emitter.onTimeout(() -> {
            executor.shutdown();
            emitter.complete();
        });

        return emitter;
    }
}
