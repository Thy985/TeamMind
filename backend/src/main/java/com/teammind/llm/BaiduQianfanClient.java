package com.teammind.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 百度千帆 LLM 客户端
 * 
 * 支持 OpenAI 协议兼容接口
 * Base URL: https://qianfan.baidubce.com/v2/coding
 */
@Slf4j
@Component
public class BaiduQianfanClient implements StreamingLLMClient {

    private static final String PROVIDER = "qianfan";
    
    // 千帆 Coding Plan 支持的模型
    private static final List<String> MODELS = List.of(
            "deepseek-v3.2",
            "kimi-k2.5",
            "glm-5",
            "minimax-m2.5",
            "qianfan-code-latest"
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${teammind.llm.qianfan.api-key:}")
    private String apiKey;

    @Value("${teammind.llm.qianfan.base-url:https://qianfan.baidubce.com/v2/coding}")
    private String baseUrl;

    @Value("${teammind.llm.qianfan.default-model:ERNIE-4.0-8K}")
    private String defaultModel;

    @Value("${teammind.llm.timeout:120000}")
    private int timeoutMs;

    public BaiduQianfanClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        if (!isAvailable()) {
            return LLMResponse.failure("Baidu Qianfan API key not configured", PROVIDER);
        }

        long startTime = System.currentTimeMillis();
        String model = request.getModel() != null ? request.getModel() : defaultModel;

        try {
            // 构建请求体（OpenAI 兼容格式）
            ObjectNode requestBody = buildRequestBody(request, model);

            log.debug("Sending request to Baidu Qianfan: model={}", model);

            // 发送请求
            String response = webClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            // 解析响应
            LLMResponse llmResponse = parseResponse(response, model);
            llmResponse.setLatencyMs(System.currentTimeMillis() - startTime);

            log.info("Qianfan response: model={}, latency={}ms, tokens={}",
                    model, llmResponse.getLatencyMs(),
                    llmResponse.getUsage() != null ? llmResponse.getUsage().getTotalTokens() : 0);

            return llmResponse;

        } catch (WebClientResponseException e) {
            log.error("Qianfan API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return LLMResponse.failure("API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), PROVIDER);
        } catch (Exception e) {
            log.error("Qianfan request failed", e);
            return LLMResponse.failure("Request failed: " + e.getMessage(), PROVIDER);
        }
    }

    @Override
    public Flux<String> streamChatFlux(LLMRequest request) {
        if (!isAvailable()) {
            return Flux.just("[ERROR] Baidu Qianfan API key not configured");
        }

        String model = request.getModel() != null ? request.getModel() : defaultModel;

        try {
            ObjectNode requestBody = buildRequestBody(request, model);
            requestBody.put("stream", true);

            log.debug("Sending streaming request to Qianfan: model={}", model);

            return webClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).trim())
                    .filter(data -> !data.equals("[DONE]"))
                    .map(this::parseStreamChunk)
                    .filter(content -> content != null && !content.isEmpty());

        } catch (Exception e) {
            log.error("Qianfan streaming request failed", e);
            return Flux.just("[ERROR] " + e.getMessage());
        }
    }

    @Override
    public List<String> listModels() {
        return MODELS;
    }

    /**
     * 构建请求体（OpenAI 兼容格式）
     */
    private ObjectNode buildRequestBody(LLMRequest request, String model) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);

        // 添加消息
        ArrayNode messages = body.putArray("messages");
        for (LLMRequest.Message msg : request.getMessages()) {
            ObjectNode msgNode = messages.addObject();
            msgNode.put("role", msg.getRole());
            msgNode.put("content", msg.getContent());
            if (msg.getName() != null) {
                msgNode.put("name", msg.getName());
            }
        }

        // 添加可选参数
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTopP() != null) {
            body.put("top_p", request.getTopP());
        }
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            ArrayNode stopArray = body.putArray("stop");
            request.getStop().forEach(stopArray::add);
        }

        return body;
    }

    /**
     * 解析响应
     */
    private LLMResponse parseResponse(String response, String model) {
        try {
            JsonNode root = objectMapper.readTree(response);

            String id = root.path("id").asText();

            // 获取内容
            String content = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();

            String finishReason = root.path("choices")
                    .path(0)
                    .path("finish_reason")
                    .asText();

            // 获取使用量
            JsonNode usageNode = root.path("usage");
            LLMResponse.Usage usage = null;
            if (!usageNode.isMissingNode()) {
                usage = LLMResponse.Usage.builder()
                        .promptTokens(usageNode.path("prompt_tokens").asInt())
                        .completionTokens(usageNode.path("completion_tokens").asInt())
                        .totalTokens(usageNode.path("total_tokens").asInt())
                        .build();
            }

            return LLMResponse.builder()
                    .id(id)
                    .content(content)
                    .model(model)
                    .provider(PROVIDER)
                    .finishReason(finishReason)
                    .usage(usage)
                    .success(true)
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Qianfan response", e);
            return LLMResponse.failure("Failed to parse response: " + e.getMessage(), PROVIDER);
        }
    }

    /**
     * 解析流式响应块
     */
    private String parseStreamChunk(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            return root.path("choices")
                    .path(0)
                    .path("delta")
                    .path("content")
                    .asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
