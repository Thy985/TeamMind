package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * AgentInvocation 实体 — 一次真实的 CLI 进程调用
 *
 * 每次 ExecutionStep 可能包含多次 Invocation（retry、fallback）。
 * 用于 crash recovery：通过 pid 检查进程是否还活着。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "agent_invocations")
public class AgentInvocation {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String stepId;  // FK → ExecutionStep.id

    @Column(nullable = false)
    private String pluginId;  // "codex" / "claude-code" / "git-verifier"

    @Column(columnDefinition = "TEXT")
    private String command;   // 完整命令行

    /** -1 = 超时/kill, 0 = 成功, >0 = 错误码 */
    private Integer exitCode;

    private Long durationMs;

    @Column(length = 500)
    private String stdoutSummary;

    @Column(length = 500)
    private String stderrSummary;

    /** OS 进程 PID（重启后可通过 ProcessHandle 检查） */
    private Long pid;

    /** 重启时检查：进程是否还活着 */
    private Boolean processAlive;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
