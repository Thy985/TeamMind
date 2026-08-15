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

    @Column(columnDefinition = "TEXT")
    private String objective;

    /** 推断的任务类型 */
    private String taskTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.teammind.common.TaskState state;

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
