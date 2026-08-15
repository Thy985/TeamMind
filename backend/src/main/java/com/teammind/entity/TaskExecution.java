package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * TaskExecution 实体 — 任务执行记录（替代旧 Mission 的核心功能）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "task_executions")
public class TaskExecution {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String projectId;

    /** FK → Task.id (Phase 1A: 关联到 Task 以支持多 Execution per Task) */
    private String taskId;

    @Column(columnDefinition = "TEXT")
    private String objective;

    /** 推断的任务类型 */
    private String taskTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.teammind.common.TaskState state;

    /**
     * 细粒度内部执行状态（Phase 1A Runtime Contract）
     * 支持 Pause / NeedsApproval / Recovering 等中间态
     * nullable=true：兼容旧代码路径（老测试/legacy 代码不设置此字段）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_state")
    private com.teammind.common.TaskExecutionState executionState;

    /** Pipeline 步骤级别的状态 */
    @Column(length = 64)
    private String currentStepName;

    /** 当前负责的 Agent（与 currentAgentId 保持一致，新增字段） */
    @Column(length = 64)
    private String agentId;

    /** 失败原因（FINALIZED state 时有值） */
    @Column(length = 500)
    private String errorReason;

    /** 重试次数（与 Task 同步，Execution 维度的独立计数） */
    @Builder.Default
    private Integer attemptNumber = 1;

    /** 当前负责的 Agent */
    private String currentAgentId;

    /** 当前负责的 Role */
    private String currentRole;

    /** 重试次数 */
    @Builder.Default
    private Integer retryCount = 0;

    /** 最大重试次数 */
    @Builder.Default
    private Integer maxRetries = 3;

    /** 团队配置快照 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> teamSnapshot;

    /** 路由决策记录 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> routingHistory;

    /** 产物 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> artifacts;

    /** 证据 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> evidence;

    /** 评分 */
    private Double finalScore;

    /** 完成摘要 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    private Long durationMs;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
