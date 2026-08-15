package com.teammind.runtime;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Project Policy — 项目治理规则
 *
 * 这是 TeamMind 区别于所有其他工具的最后一层：
 * 不是"哪个 Agent 最强就让谁做"，而是"根据项目治理规则，在允许范围内选择最合适的 Agent"。
 */
@Data
@Builder
public class ProjectPolicy {
    /** 能力级别策略 */
    List<CapabilityPolicy> capabilityPolicies;

    /** 任务级别策略（正则匹配任务描述） */
    List<TaskPolicy> taskPolicies;

    /** 审批规则 */
    List<ApprovalRule> approvalRules;

    /** 禁止规则 */
    List<ProhibitionRule> prohibitionRules;

    /** 默认控制模式（未设置时生效） */
    com.teammind.common.ControlMode defaultControlMode;

    @Data
    @Builder
    public static class CapabilityPolicy {
        String capability;
        List<String> allowedPlugins;
        String preferredPlugin;
        boolean requiresReview;
        List<String> reviewBy;
    }

    @Data
    @Builder
    public static class TaskPolicy {
        String pattern;                // 正则表达式
        CapabilityPolicy override;
    }

    @Data
    @Builder
    public static class ApprovalRule {
        String condition;              // 触发条件（自然语言或表达式）
        ApprovalAction action;
        FallbackAction fallback;
    }

    @Data
    @Builder
    public static class ProhibitionRule {
        String target;                 // 禁止的操作类型
        String reason;
        Severity severity;             // HARD = 直接拒绝，SOFT = 警告

        public enum Severity { HARD, SOFT }
    }

    public enum ApprovalAction { REQUIRED, SUGGESTED, SKIP }
    public enum FallbackAction { APPROVE, DENY, PAUSE }
}
