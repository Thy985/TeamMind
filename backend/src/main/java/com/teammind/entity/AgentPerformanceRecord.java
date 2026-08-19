package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * AgentPerformanceRecord — Agent 单次任务表现记录
 *
 * 记录单次任务执行的完整数据，用于聚合 Project Agent Profile。
 * 只记录可验证事实，不做"谁更强"判断。
 *
 * 字段说明：
 * - verificationResult: Evidence verifier 的判定结果
 * - reworkCount: 需要返工次数（人类 reviewer 拒绝后重新执行）
 * - evidenceQuality: artifact 质量评分（0-1）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "agent_performance_records")
public class AgentPerformanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 项目 ID */
    @Column(nullable = false)
    private String projectId;

    /** Agent ID（如 "codex", "claude-code", "qwenpaw"） */
    @Column(nullable = false)
    private String agentId;

    /** Transport 类型 */
    private String transport;

    /** 执行的 role（如 "implementer", "reviewer", "researcher"） */
    private String role;

    /** 任务类型（由 CapabilityRouter.inferTaskType() 推断） */
    private String taskType;

    // ─── 时间信息 ───────────────────────────────────────────────
    @Column(nullable = false)
    private Instant startedAt;

    /** 完成时间（null = 未完成） */
    private Instant completedAt;

    /** 实际耗时（ms） */
    private Long durationMs;

    // ─── 结果 ───────────────────────────────────────────────────
    /** 任务是否成功完成 */
    @Builder.Default
    private Boolean result = null;

    /** Evidence verifier 判定结果 */
    private String verificationResult;

    /** 生成的 artifact 路径列表 */
    @ElementCollection
    @CollectionTable(name = "agent_performance_artifacts",
                     joinColumns = @JoinColumn(name = "record_id"))
    @Column(name = "artifact_path")
    private List<String> artifacts;

    // ─── 过程统计 ───────────────────────────────────────────────
    /** 需要返工次数 */
    @Builder.Default
    private Integer reworkCount = 0;

    /** Reviewer 发现的问题数 */
    @Builder.Default
    private Integer reviewFindings = 0;

    /** Reviewer 接受的问题数 */
    @Builder.Default
    private Integer acceptedFindings = 0;

    /** 人类 reviewer 是否接受最终结果 */
    private Boolean humanAccepted;

    // ─── Evidence 质量 ──────────────────────────────────────────
    /** Artifact 质量评分（0.0 - 1.0） */
    private Double evidenceQuality;

    /** 生成时间戳 */
    private Instant createdAt;

    /** 更新时间戳 */
    private Instant updatedAt;

    // ─── 计算属性 ───────────────────────────────────────────────
    /**
     * 完成率（completed / total）
     */
    public double completionRate() {
        if (completedAt == null) return 0.0;
        return result != null && result ? 1.0 : 0.0;
    }

    /**
     * 是否需要人工介入
     */
    public boolean needsHumanReview() {
        return humanAccepted == null;
    }

    /**
     * 是否已验证通过
     */
    public boolean isVerified() {
        return "VERIFIED".equals(verificationResult);
    }
}
