package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * KnowledgeEntry — 用户从 Execution Ledger 晋升的 ADR / Lesson
 *
 * 生命周期：
 *   1. ActivityExtractor 检测到模式 → 生成 KnowledgeCandidate
 *   2. 用户点击 [Create ADR] / [Save Lesson] → 持久化到此表
 *   3. 用户点击 [Ignore] → dismissed = true
 *
 * 与 RoutingLesson 的区别：
 *   RoutingLesson 是自动提炼的路由经验（系统内部使用）
 *   KnowledgeEntry 是用户决策的长期知识（ADR/Lesson，面向人）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "knowledge_entries", indexes = {
        @Index(name = "idx_ke_task", columnList = "task_id"),
        @Index(name = "idx_ke_project", columnList = "project_id"),
        @Index(name = "idx_ke_type", columnList = "type"),
        @Index(name = "idx_ke_dismissed", columnList = "dismissed")
})
public class KnowledgeEntry {

    @Id
    @Column(length = 36)
    private String id;

    private String taskId;
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KnowledgeType type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 来源：INCIDENT / DEPENDENCY / DECISION / VERIFICATION */
    @Column(length = 32)
    private String source;

    @Builder.Default
    private Double confidence = 0.5;

    @Builder.Default
    private Boolean dismissed = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum KnowledgeType {
        ADR,      // Architecture Decision Record
        LESSON    // 项目经验教训
    }
}
