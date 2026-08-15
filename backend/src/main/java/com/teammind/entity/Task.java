package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Task 实体 — 用户的任务意图（一次性创建，核心字段不可变）
 *
 * 一个 Task 可以有多个 TaskExecution（重试、reroute 各产生一次新 Execution）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String objective;

    /** 推断的任务类型（implementation / test / refactor / review 等） */
    private String taskTypeId;

    /** 宏观状态：SUBMITTED / RUNNING / DONE / FAILED / CANCELLED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.teammind.common.TaskState state;

    /** 使用的 Pipeline 定义 ID */
    private String pipelineId;

    /** 初始分配的 Agent ID */
    private String assignedAgentId;

    @Builder.Default
    private Integer retryCount = 0;

    @Builder.Default
    private Integer maxRetries = 3;

    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
