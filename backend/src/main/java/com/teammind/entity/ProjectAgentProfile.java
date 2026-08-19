package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

/**
 * ProjectAgentProfile — 项目级 Agent 表现档案
 *
 * 聚合多个 AgentPerformanceRecord，形成每个 Agent 在该项目中的能力画像。
 * 用于 CapabilityRouter 的历史表现评分。
 *
 * 注意：不存储"谁更强"的判断，只存储可验证的事实统计。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "project_agent_profiles")
public class ProjectAgentProfile {

    @Id
    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    /** 主要 role（从历史任务推断） */
    private String primaryRole;

    /** 支持的 capabilities 列表 */
    @ElementCollection
    @CollectionTable(name = "project_agent_capabilities",
                     joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "capability")
    private java.util.List<String> capabilities;

    // ─── 统计数据 ───────────────────────────────────────────────
    /** 总任务数 */
    @Builder.Default
    private Integer totalTasks = 0;

    /** 成功任务数 */
    @Builder.Default
    private Integer successfulTasks = 0;

    /** 成功率 */
    private Double successRate;

    /** 平均耗时（ms） */
    private Long avgDurationMs;

    /** 平均返工次数 */
    private Double avgReworkCount;

    /** 人类接受率 */
    private Double humanAcceptanceRate;

    /** 平均 Evidence 质量 */
    private Double avgEvidenceQuality;

    // ─── 按任务类型的细分统计 ────────────────────────────────────
    /**
     * key: taskType, value: 该类型的平均成功率
     */
    @MapKeyColumn(name = "task_type")
    @ElementCollection
    @CollectionTable(name = "project_agent_task_stats",
                     joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "success_rate")
    private Map<String, Double> taskTypeSuccessRates;

    // ─── Provider 状态 ──────────────────────────────────────────
    /** 最后已知 Provider 状态 */
    private String providerState;

    /** Provider 就绪时间（epoch ms） */
    private Long readyAtMs;

    /** 上次更新时间 */
    private Instant updatedAt;

    /** 创建时间 */
    private Instant createdAt;

    // ─── 计算属性 ───────────────────────────────────────────────
    /**
     * 样本量是否足够做出统计推断（>= 5 次任务）
     */
    public boolean hasEnoughSamples() {
        return totalTasks >= 5;
    }

    /**
     * 是否可作为 production 使用（成功率 >= 0.8 且有足够样本）
     */
    public boolean isProductionReady() {
        return hasEnoughSamples() && successRate != null && successRate >= 0.8;
    }
}
