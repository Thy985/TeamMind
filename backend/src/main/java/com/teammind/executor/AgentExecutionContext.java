package com.teammind.executor;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Singular;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行上下文
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentExecutionContext {

    private String executionId;
    private String agentId;
    private String agentName;
    private String missionId;
    private String nodeId;
    private Map<String, Object> input;
    private String userRequest;
    private Map<String, Object> dependencies;
    
    @Builder.Default
    private int maxIterations = 10;
    
    @Builder.Default
    private long timeoutMs = 120000;
    
    @Builder.Default
    private int currentIteration = 0;
    
    private LocalDateTime createdAt;
    
    @Singular
    private List<ToolCall> toolCalls;

    /**
     * 工具调用记录
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ToolCall {
        private String toolName;
        private Map<String, Object> arguments;
        private Object result;
        private boolean success;
        private String error;
        private LocalDateTime timestamp;
    }
}
