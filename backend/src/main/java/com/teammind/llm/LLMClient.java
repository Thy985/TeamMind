package com.teammind.llm;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * LLM 客户端接口
 */
public interface LLMClient {

    String getProvider();

    boolean isAvailable();

    LLMResponse chat(LLMRequest request);

    default LLMResponse chat(String prompt) {
        return chat(LLMRequest.builder()
                .message(LLMRequest.Message.user(prompt))
                .build());
    }

    default LLMResponse chat(String systemPrompt, String userPrompt) {
        return chat(LLMRequest.builder()
                .message(LLMRequest.Message.system(systemPrompt))
                .message(LLMRequest.Message.user(userPrompt))
                .build());
    }

    default Flux<String> streamChat(LLMRequest request) {
        LLMResponse response = chat(request);
        return Flux.just(response.getContent());
    }

    default List<String> listModels() {
        return List.of();
    }

    default int countTokens(String text) {
        return text.length() / 4;
    }
}
