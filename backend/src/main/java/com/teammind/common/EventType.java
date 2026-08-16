package com.teammind.common;

/**
 * TeamMind 统一事件协议 — 40+ 事件类型
 *
 * 所有 CLI Adapter 把自己的行为映射成这些标准事件。
 * 前端通过 WebSocket 消费这套协议，不需要知道任何 CLI 格式。
 *
 * 事件分类：
 *   生命周期：task.started / task.completed / task.failed / task.cancelled / task.retrying
 *   Agent 状态：agent.started / agent.thinking / agent.idle / agent.completed / agent.failed / agent.handoff
 *   执行细节：agent.chunk / tool.called / tool.result / file.changed / command.running
 *   产物：artifact.created / artifact.updated
 *   验证：evidence.verifying / evidence.verified / evidence.failed / test.started / test.passed / test.failed / test.result
 *   审查：review.requested / review.started / finding.created / finding.resolved / review.completed / review.approved / review.rejected
 *   决策：decision.made / decision.requires_approval / approval.granted / approval.denied / approval.auto_approved
 *   路由：routing.decided / routing.skipped / handoff.requested / handoff.accepted
 *   异常：error.critical / error.recoverable / retry.initiated / fallback.triggered / plugin.unhealthy / plugin.down
 *   进化：profile.updated / drift.detected / recommendation.generated / lesson.learned
 */
public enum EventType {
    // ─── 生命周期 ─────────────────────────────
    TASK_STARTED,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_CANCELLED,
    TASK_RETRYING,

    // ─── Agent 状态 ───────────────────────────
    AGENT_STARTED,
    AGENT_THINKING,
    AGENT_IDLE,
    AGENT_COMPLETED,
    AGENT_FAILED,
    AGENT_HANDOFF,

    // ─── 执行细节 ─────────────────────────────
    AGENT_CHUNK,
    TOOL_CALLED,
    TOOL_RESULT,
    FILE_CHANGED,
    COMMAND_RUNNING,

    // ─── 产物 ─────────────────────────────────
    ARTIFACT_CREATED,
    ARTIFACT_UPDATED,

    // ─── 验证 ─────────────────────────────────
    EVIDENCE_VERIFYING,
    EVIDENCE_VERIFIED,
    EVIDENCE_FAILED,
    TEST_STARTED,
    TEST_PASSED,
    TEST_FAILED,
    TEST_RESULT,

    // ─── 审查 ─────────────────────────────────
    REVIEW_REQUESTED,
    REVIEW_STARTED,
    FINDING_CREATED,
    FINDING_RESOLVED,
    REVIEW_COMPLETED,
    REVIEW_APPROVED,
    REVIEW_REJECTED,

    // ─── 决策 ─────────────────────────────────
    DECISION_MADE,
    DECISION_REQUIRES_APPROVAL,
    APPROVAL_GRANTED,
    APPROVAL_DENIED,
    APPROVAL_AUTO_APPROVED,

    // ─── 路由 ─────────────────────────────────
    ROUTING_DECIDED,
    ROUTING_SKIPPED,
    HANDOFF_REQUESTED,
    HANDOFF_ACCEPTED,

    // ─── 异常 ─────────────────────────────────
    ERROR_CRITICAL,
    ERROR_RECOVERABLE,
    RETRY_INITIATED,
    FALLBACK_TRIGGERED,
    PLUGIN_UNHEALTHY,
    PLUGIN_DOWN,

    // ─── 进化 ─────────────────────────────────
    PROFILE_UPDATED,
    DRIFT_DETECTED,
    RECOMMENDATION_GENERATED,
    LESSON_LEARNED,

    // ─── 状态转移 ─────────────────────────────
    TASK_STATE_CHANGED,

    // ─── 环境变更 ─────────────────────────────
    DEPENDENCY_CHANGED
}
