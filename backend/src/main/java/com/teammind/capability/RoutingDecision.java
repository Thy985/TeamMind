package com.teammind.capability;

import com.teammind.common.AgentRole;
import com.teammind.plugin.Plugin;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 路由决策 — Capability Router 的输出
 */
@Data
@Builder
public class RoutingDecision {
    /** 选中的 Plugin ID */
    String selectedPluginId;

    /** 选中的 Plugin 名称 */
    String selectedPluginName;

    /** 分配的角色 */
    AgentRole role;

    /** 所需能力 */
    String capability;

    /** 各因素得分详情（用于调试和日志） */
    Map<String, Double> scoreBreakdown;

    /** 最终总分 */
    double totalScore;

    /** 决策原因 */
    String reason;

    /** 是否需要审批 */
    boolean needsApproval;

    /** 建议的下一步操作 */
    String nextAction;

    /** 被排除的候选及原因 */
    List<RejectedCandidate> rejectedCandidates;

    @Data
    @Builder
    public static class RejectedCandidate {
        String pluginId;
        String reason;
        double score;
    }
}
