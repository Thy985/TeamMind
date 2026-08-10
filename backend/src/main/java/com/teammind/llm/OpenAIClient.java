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
 * OpenAI 客户端
 * 
 * 支持 GPT-4, GPT-3.5-turbo 等模型
 * 也支持兼容 OpenAI API 的其他服务（如 Azure OpenAI, 本地模型等）
 */
@Slf4j
@Component
public class OpenAIClient implements LLMClient {

    private static final String PROVIDER = "openai";
    private static final List<String> DEFAULT_MODELS = List.of(
            "gpt-4-turbo-preview",
            "gpt-4",
            "gpt-4-32k",
            "gpt-3.5-turbo",
            "gpt-3.5-turbo-16k"
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${teammind.llm.openai.api-key:}")
    private String apiKey;
    
    @Value("${teammind.llm.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;
    
    @Value("${teammind.llm.openai.default-model:gpt-4-turbo-preview}")
    private String defaultModel;
    
    @Value("${teammind.llm.timeout:120000}")
    private int timeoutMs;

    public OpenAIClient(ObjectMapper objectMapper) {
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
            return LLMResponse.failure("OpenAI API key not configured", PROVIDER);
        }

        long startTime = System.currentTimeMillis();
        String model = request.getModel() != null ? request.getModel() : defaultModel;

        try {
            // 构建请求体
            ObjectNode requestBody = buildRequestBody(request, model);
            
            log.debug("Sending request to OpenAI: model={}, messages={}", 
                    model, request.getMessages().size());

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
            
            log.debug("OpenAI response received: latency={}ms, tokens={}", 
                    llmResponse.getLatencyMs(), 
                    llmResponse.getUsage() != null ? llmResponse.getUsage().getTotalTokens() : 0);

            return llmResponse;

        } catch (WebClientResponseException e) {
            log.error("OpenAI API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return LLMResponse.failure("API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), PROVIDER);
        } catch (Exception e) {
            log.error("OpenAI request failed", e);
            return LLMResponse.failure("Request failed: " + e.getMessage(), PROVIDER);
        }
    }

    @Override
    public Flux<String> streamChat(LLMRequest request) {
        if (!isAvailable()) {
            return Flux.just("[ERROR] OpenAI API key not configured");
        }

        String model = request.getModel() != null ? request.getModel() : defaultModel;
        
        try {
            // 构建请求体（启用流式）
            ObjectNode requestBody = buildRequestBody(request, model);
            requestBody.put("stream", true);

            log.debug("Sending streaming request to OpenAI: model={}", model);

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
            log.error("OpenAI streaming request failed", e);
            return Flux.just("[ERROR] " + e.getMessage());
        }
    }

    @Override
    public List<String> listModels() {
        return DEFAULT_MODELS;
    }

    /**
     * 构建请求体
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
            log.error("Failed to parse OpenAI response", e);
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
