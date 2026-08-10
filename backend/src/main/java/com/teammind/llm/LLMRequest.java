package com.teammind.llm;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Singular;

import java.util.List;
import java.util.Map;

/**
 * LLM 请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LLMRequest {
    
    private String model;
    
    @Singular("message")
    private List<Message> messages;
    
    @Builder.Default
    private Double temperature = 0.7;
    
    private Integer maxTokens;
    
    private Double topP;
    
    private List<String> stop;
    
    @Builder.Default
    private Boolean stream = false;
    
    private Map<String, Object> extra;

    /**
     * 消息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        private String role;
        private String content;
        private String name;
        
        public static Message system(String content) {
            return Message.builder().role("system").content(content).build();
        }
        
        public static Message user(String content) {
            return Message.builder().role("user").content(content).build();
        }
        
        public static Message assistant(String content) {
            return Message.builder().role("assistant").content(content).build();
        }
    }
}
