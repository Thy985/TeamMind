package com.teammind.common;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TaskActivity — 从 RuntimeEvent 聚合而成的任务活动摘要
 *
 * 这是 Execution Ledger 的核心数据结构。
 * 不是日志，不是 trace，而是「从真实事件提炼出的事实」。
 *
 * 示例：
 * <pre>
 * TaskActivity{
 *   taskId = "e2e-task-001"
 *   commandsExecuted = [
 *     CommandActivity{name="npm install jsonwebtoken", durationMs=6000, exitCode=0}
 *   ]
 *   filesChanged = ["src/auth/jwt.ts", "package.json"]
 *   dependenciesChanged = [
 *     DependencyChange{action=ADDED, name="jsonwebtoken", version="9.0.2"}
 *   ]
 *   incidents = [
 *     IncidentActivity{type="Compilation Error", resolved=true}
 *   ]
 *   verifications = [
 *     VerificationActivity{type="TEST_PASSED", passed=42, failed=0}
 *   ]
 *   agentDecisions = [
 *     DecisionActivity{type="DECISION_MADE", content="Switched to JWT auth"}
 *   ]
 *   extractedAt = 2025-07-16T14:30:00
 * }
 * </pre>
 */
public record TaskActivity(
        String taskId,
        List<CommandActivity> commandsExecuted,
        List<String> filesChanged,
        List<DependencyChange> dependenciesChanged,
        List<EnvironmentChange> environmentChanges,
        List<IncidentActivity> incidents,
        List<VerificationActivity> verifications,
        List<DecisionActivity> agentDecisions,
        LocalDateTime extractedAt
) {

    /** 命令执行记录 */
    public record CommandActivity(
            String command,
            long durationMs,
            int exitCode,
            LocalDateTime startedAt
    ) {}

    /** 依赖变更记录 */
    public record DependencyChange(
            Action action,  // ADDED / REMOVED
            String name,
            String version
    ) {
        public enum Action { ADDED, REMOVED }
    }

    /** 事件/问题记录 */
    public record IncidentActivity(
            String type,           // "Compilation Error", "Runtime Exception"
            String description,    // 从 payload 提取的关键信息
            boolean resolved,      // 是否有 ERROR_RECOVERABLE 紧随其后
            String resolvedBy      // 哪个 agent 解决的
    ) {}

    /** 验证结果记录 */
    public record VerificationActivity(
            String type,           // "TEST_PASSED", "EVIDENCE_VERIFIED"
            int passed,
            int failed
    ) {}

    /** 决策记录 */
    public record DecisionActivity(
            String type,           // "DECISION_MADE", "APPROVAL_GRANTED"
            String content         // 从 payload 提取的决策内容
    ) {}

    /** 环境变更记录（PACKAGE_INSTALLED / ENV_VAR_MODIFIED / PROCESS_STARTED / FILE_DELETED） */
    public record EnvironmentChange(
            Action action,         // ADDED / REMOVED / MODIFIED / STARTED
            String name,
            String detail,         // 版本 / 路径 / PID 等
            String typeLabel       // "Package" / "EnvVar" / "Process" / "File"
    ) {
        public enum Action { ADDED, REMOVED, MODIFIED, STARTED }
    }

    /** 空摘要（无事件时返回） */
    public static TaskActivity empty(String taskId) {
        return new TaskActivity(
                taskId,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                LocalDateTime.now()
        );
    }
}
