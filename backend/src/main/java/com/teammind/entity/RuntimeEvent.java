package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * RuntimeEvent 实体 — 持久化事件存储（Phase 1C-3: 分级存储）
 *
 * 所有事件先写 DB 再广播，保证 crash 后不丢失。
 * 支持 event replay（断线重连补全）。
 *
 * 事件分级：
 *   HOT  (永久): TASK_STARTED, TASK_COMPLETED, EVIDENCE_VERIFIED, APPROVAL_*
 *   WARM (7天):  ARTIFACT_CREATED, FINDING_CREATED
 *   COLD (30天): AGENT_CHUNK, TOOL_CALLED → 归档到文件系统
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "runtime_events", indexes = {
        @Index(name = "idx_event_task", columnList = "task_id"),
        @Index(name = "idx_event_type", columnList = "type"),
        @Index(name = "idx_event_tier", columnList = "tier"),
        @Index(name = "idx_event_created", columnList = "created_at")
})
public class RuntimeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.teammind.common.EventType type;

    @Column(nullable = false)
    private String taskId;

    private String executionId;
    private String stepId;
    private String pluginId;
    private String role;

    @Column(columnDefinition = "TEXT")
    private String payload;  // JSON

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 事件级别：HOT / WARM / COLD / TRASH */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    @Builder.Default
    private EventTier tier = EventTier.HOT;

    /** 归档路径（COLD 事件写入文件系统后的路径） */
    private String archivedPath;

    /**
     * 根据 EventType 推断默认 tier
     */
    public static EventTier inferTier(com.teammind.common.EventType type) {
        return switch (type) {
            case TASK_STARTED, TASK_COMPLETED, TASK_FAILED, TASK_CANCELLED, TASK_RETRYING -> EventTier.HOT;
            case EVIDENCE_VERIFIED, EVIDENCE_VERIFYING, EVIDENCE_FAILED -> EventTier.HOT;
            case APPROVAL_GRANTED, APPROVAL_DENIED, APPROVAL_AUTO_APPROVED,
                 DECISION_MADE, DECISION_REQUIRES_APPROVAL -> EventTier.HOT;
            case ARTIFACT_CREATED, ARTIFACT_UPDATED, FINDING_CREATED, FINDING_RESOLVED -> EventTier.WARM;
            case AGENT_CHUNK, TOOL_CALLED, TOOL_RESULT, FILE_CHANGED, COMMAND_RUNNING -> EventTier.COLD;
            case AGENT_STARTED, AGENT_COMPLETED, AGENT_FAILED, AGENT_HANDOFF, AGENT_THINKING, AGENT_IDLE -> EventTier.WARM;
            case TEST_STARTED, TEST_PASSED, TEST_FAILED, TEST_RESULT -> EventTier.WARM;
            case REVIEW_REQUESTED, REVIEW_STARTED, REVIEW_COMPLETED, REVIEW_APPROVED, REVIEW_REJECTED -> EventTier.WARM;
            case ROUTING_DECIDED, ROUTING_SKIPPED, HANDOFF_REQUESTED, HANDOFF_ACCEPTED -> EventTier.WARM;
            case ERROR_CRITICAL, ERROR_RECOVERABLE, RETRY_INITIATED, FALLBACK_TRIGGERED,
                 PLUGIN_UNHEALTHY, PLUGIN_DOWN -> EventTier.HOT;
            case PROFILE_UPDATED, DRIFT_DETECTED, RECOMMENDATION_GENERATED, LESSON_LEARNED -> EventTier.WARM;
            case TASK_STATE_CHANGED -> EventTier.HOT;
        };
    }

    public enum EventTier {
        HOT, WARM, COLD, TRASH
    }
}
