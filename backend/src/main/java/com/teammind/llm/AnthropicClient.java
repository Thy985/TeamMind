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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Anthropic Claude 客户端
 */
@Slf4j
@Component
public class AnthropicClient implements StreamingLLMClient {

    private static final String PROVIDER = "anthropic";
    private static final String API_VERSION = "2023-06-01";
    private static final List<String> MODELS = List.of(
            "claude-3-opus-20240229",
            "claude-3-sonnet-20240229",
            "claude-3-haiku-20240307",
            "claude-2.1",
            "claude-2.0",
            "claude-instant-1.2"
    );

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${teammind.llm.anthropic.api-key:}")
    private String apiKey;

    @Value("${teammind.llm.anthropic.base-url:https://api.anthropic.com/v1}")
    private String baseUrl;

    @Value("${teammind.llm.anthropic.default-model:claude-3-sonnet-20240229}")
    private String defaultModel;

    @Value("${teammind.llm.timeout:120000}")
    private int timeoutMs;

    public AnthropicClient(ObjectMapper objectMapper) {
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
            return LLMResponse.failure("Anthropic API key not configured", PROVIDER);
        }

        long startTime = System.currentTimeMillis();
        String model = request.getModel() != null ? request.getModel() : defaultModel;

        try {
            ObjectNode requestBody = buildRequestBody(request, model, false);

            log.debug("Sending request to Anthropic: model={}", model);

            String response = webClient.post()
                    .uri(baseUrl + "/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            LLMResponse llmResponse = parseResponse(response, model);
            llmResponse.setLatencyMs(System.currentTimeMillis() - startTime);

            return llmResponse;

        } catch (Exception e) {
            log.error("Anthropic request failed", e);
            return LLMResponse.failure("Request failed: " + e.getMessage(), PROVIDER);
        }
    }

    @Override
    public Flux<String> streamChatFlux(LLMRequest request) {
        if (!isAvailable()) {
            return Flux.just("[ERROR] Anthropic API key not configured");
        }

        String model = request.getModel() != null ? request.getModel() : defaultModel;

        try {
            ObjectNode requestBody = buildRequestBody(request, model, true);

            return webClient.post()
                    .uri(baseUrl + "/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).trim())
                    .filter(data -> !data.isEmpty())
                    .map(this::parseStreamChunk)
                    .filter(Objects::nonNull);

        } catch (Exception e) {
            log.error("Anthropic streaming request failed", e);
            return Flux.just("[ERROR] " + e.getMessage());
        }
    }

    @Override
    public java.util.List<String> listModels() {
        return MODELS;
    }

    /**
     * 构建请求体（Anthropic 格式）
     */
    private ObjectNode buildRequestBody(LLMRequest request, String model, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
        body.put("stream", stream);

        // Anthropic 使用单独的 system 字段
        ArrayNode messages = body.putArray("messages");
        String systemPrompt = null;

        for (LLMRequest.Message msg : request.getMessages()) {
            if ("system".equals(msg.getRole())) {
                systemPrompt = msg.getContent();
            } else {
                ObjectNode msgNode = messages.addObject();
                msgNode.put("role", msg.getRole());
                msgNode.put("content", msg.getContent());
            }
        }

        if (systemPrompt != null) {
            body.put("system", systemPrompt);
        }

        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            body.put("top_p", request.getTopP());
        }
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            ArrayNode stopArray = body.putArray("stop_sequences");
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
            StringBuilder content = new StringBuilder();
            JsonNode contentArray = root.path("content");
            if (contentArray.isArray()) {
                for (JsonNode node : contentArray) {
                    if ("text".equals(node.path("type").asText())) {
                        content.append(node.path("text").asText());
                    }
                }
            }

            String stopReason = root.path("stop_reason").asText();

            // 获取使用量
            JsonNode usageNode = root.path("usage");
            LLMResponse.Usage usage = null;
            if (!usageNode.isMissingNode()) {
                usage = LLMResponse.Usage.builder()
                        .promptTokens(usageNode.path("input_tokens").asInt())
                        .completionTokens(usageNode.path("output_tokens").asInt())
                        .totalTokens(usageNode.path("input_tokens").asInt() + usageNode.path("output_tokens").asInt())
                        .build();
            }

            return LLMResponse.builder()
                    .id(id)
                    .content(content.toString())
                    .model(model)
                    .provider(PROVIDER)
                    .finishReason(stopReason)
                    .usage(usage)
                    .success(true)
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Anthropic response", e);
            return LLMResponse.failure("Failed to parse response: " + e.getMessage(), PROVIDER);
        }
    }

    /**
     * 解析流式响应块
     */
    private String parseStreamChunk(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            String type = root.path("type").asText();

            if ("content_block_delta".equals(type)) {
                return root.path("delta")
                        .path("text")
                        .asText(null);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
