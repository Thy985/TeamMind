package com.teammind.dto;

import com.teammind.common.TaskExecutionState;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Task 状态快照 DTO — 供 MissionControl 和 WebSocket 投影使用
 *
 * 一次请求返回完整状态，前端无需自己拼凑。
 */
@Data
@Builder
public class TaskStateSnapshot {
    private String taskId;
    private String executionId;
    private com.teammind.common.TaskState taskState;
    private TaskExecutionState executionState;
    private String currentStepName;
    private String currentAgentId;
    private Integer attemptNumber;
    private Integer retryCount;
    private Integer maxRetries;
    private String summary;
    private String errorReason;
    private Long durationMs;
    private Map<String, Object> evidenceSummary;  // {verified:N, pending:N, invalidated:N}
    private Map<String, Object> artifactSummary;  // {CODE_DIFF:N, REVIEW_FINDINGS:N, ...}
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
