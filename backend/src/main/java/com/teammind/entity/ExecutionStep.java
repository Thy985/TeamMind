package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * ExecutionStep 实体 — Pipeline 中的一个步骤
 *
 * 每个 TaskExecution 对应一系列 ExecutionStep。
 * 例如：implement → review → verify
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "execution_steps")
public class ExecutionStep {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String executionId;  // FK → TaskExecution.id

    @Column(nullable = false)
    private String stepName;     // "implement" / "review" / "verify"

    @Column(nullable = false)
    private String agentId;      // "codex" / "claude-code" / "git-verifier"

    @Column(nullable = false)
    private String role;         // "LEAD" / "REVIEWER" / "VERIFIER"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.teammind.common.ExecutionStepState state;

    @Column(columnDefinition = "TEXT")
    private String prompt;       // 实际发送给 Agent 的 prompt（含上下文注入）

    private String outputSummary;

    private Long durationMs;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
