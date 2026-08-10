package com.teammind.llm;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * LLM 响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LLMResponse {
    
    private String id;
    private String content;
    private String model;
    private Usage usage;
    private String finishReason;
    private Long latencyMs;
    private String provider;
    private Map<String, Object> extra;
    private LocalDateTime createdAt;
    private boolean success;
    private String error;

    /**
     * Token 使用量
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

    public static LLMResponse success(String content, String model, String provider) {
        return LLMResponse.builder()
                .content(content)
                .model(model)
                .provider(provider)
                .success(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static LLMResponse failure(String error, String provider) {
        return LLMResponse.builder()
                .success(false)
                .error(error)
                .provider(provider)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
