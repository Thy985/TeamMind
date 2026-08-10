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
 * Agent 执行结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentExecutionResult {

    private String executionId;
    private String agentId;
    private boolean success;
    private ExecutionStatus status;
    private Map<String, Object> output;
    private String response;
    private String error;
    
    @Singular
    private List<AgentExecutionContext.ToolCall> toolCalls;
    
    private TokenUsage tokenUsage;
    private long executionTimeMs;
    private int iterations;
    private String finishReason;
    private String humanInputRequired;
    private LocalDateTime completedAt;

    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        PENDING,
        RUNNING,
        WAITING,
        COMPLETED,
        FAILED,
        TIMEOUT,
        CANCELLED
    }

    /**
     * Token 使用量
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

    public static AgentExecutionResult success(String executionId, String agentId, String response) {
        return AgentExecutionResult.builder()
                .executionId(executionId)
                .agentId(agentId)
                .success(true)
                .status(ExecutionStatus.COMPLETED)
                .response(response)
                .completedAt(LocalDateTime.now())
                .build();
    }

    public static AgentExecutionResult failure(String executionId, String agentId, String error) {
        return AgentExecutionResult.builder()
                .executionId(executionId)
                .agentId(agentId)
                .success(false)
                .status(ExecutionStatus.FAILED)
                .error(error)
                .completedAt(LocalDateTime.now())
                .build();
    }
}
