package com.teammind.runtime;

import com.teammind.common.ControlMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Policy Engine — 硬约束检查
 *
 * Policy 是硬约束，不符合直接排除，不打分。
 * 审批检查决定是否需要暂停等待用户确认。
 */
@Slf4j
@Component
public class PolicyEngine {

    /**
     * 检查 Plugin 是否被 Policy 允许执行某个能力
     *
     * @return true = 允许，false = 被 Policy 阻止
     */
    public boolean isAllowed(String pluginId, String capability, String taskDescription,
                              ProjectPolicy policy) {
        if (policy == null) return true;

        // 1. 检查 CapabilityPolicy.allowedPlugins
        var capPolicies = policy.getCapabilityPolicies();
        if (capPolicies != null) {
            for (ProjectPolicy.CapabilityPolicy capPolicy : capPolicies) {
                if (capPolicy.getCapability().equals(capability)) {
                    List<String> allowed = capPolicy.getAllowedPlugins();
                    if (allowed != null && !allowed.isEmpty() && !allowed.contains(pluginId)) {
                        log.debug("Plugin '{}' blocked by CapabilityPolicy for '{}'", pluginId, capability);
                        return false;
                    }
                }
            }
        }

        // 2. 检查 TaskPolicy 覆盖
        var taskPolicies = policy.getTaskPolicies();
        if (taskPolicies != null) {
            for (ProjectPolicy.TaskPolicy tp : taskPolicies) {
                if (taskDescription != null && Pattern.compile(tp.getPattern()).matcher(taskDescription).find()) {
                    List<String> allowed = tp.getOverride() != null ? tp.getOverride().getAllowedPlugins() : null;
                    if (allowed != null && !allowed.isEmpty() && !allowed.contains(pluginId)) {
                        log.debug("Plugin '{}' blocked by TaskPolicy (pattern={}) for '{}'",
                                pluginId, tp.getPattern(), capability);
                        return false;
                    }
                }
            }
        }

        // 3. 检查 ProhibitionRule
        var prohibRules = policy.getProhibitionRules();
        if (prohibRules != null) {
            for (ProjectPolicy.ProhibitionRule rule : prohibRules) {
                if (matchesRule(rule.getTarget(), taskDescription)) {
                    if (rule.getSeverity() == ProjectPolicy.ProhibitionRule.Severity.HARD) {
                        log.warn("Plugin '{}' prohibited by rule '{}' for task '{}'",
                                pluginId, rule.getTarget(), taskDescription);
                        return false;
                    }
                    log.warn("Plugin '{}' warning: soft prohibition '{}' matched for task '{}'",
                            pluginId, rule.getTarget(), taskDescription);
                }
            }
        }

        return true;
    }

    /**
     * 检查是否需要审批
     *
     * @return true = 需要审批
     */
    public boolean needsApproval(String pluginId, String capability, String taskDescription,
                                  ProjectPolicy policy) {
        if (policy == null) return false;

        // 1. 检查 ApprovalRule
        var approvalRules = policy.getApprovalRules();
        if (approvalRules != null) {
            for (ProjectPolicy.ApprovalRule rule : approvalRules) {
                if (matchesCondition(rule.getCondition(), pluginId, capability, taskDescription)) {
                    if (rule.getAction() == ProjectPolicy.ApprovalAction.REQUIRED) {
                        return true;
                    }
                }
            }
        }

        // 2. 检查 CapabilityPolicy.requiresReview
        var capPolicies = policy.getCapabilityPolicies();
        if (capPolicies != null) {
            for (ProjectPolicy.CapabilityPolicy capPolicy : capPolicies) {
                if (capPolicy.getCapability().equals(capability) && capPolicy.isRequiresReview()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 获取审批时的 fallback 行为
     */
    public ProjectPolicy.FallbackAction getFallbackAction(String pluginId,
                                                                        String capability,
                                                                        String taskDescription,
                                                                        ProjectPolicy policy) {
        if (policy == null) return ProjectPolicy.FallbackAction.PAUSE;

        for (ProjectPolicy.ApprovalRule rule : policy.getApprovalRules()) {
            if (matchesCondition(rule.getCondition(), pluginId, capability, taskDescription)) {
                return rule.getFallback();
            }
        }
        return ProjectPolicy.FallbackAction.PAUSE;
    }

    /**
     * 检查条件是否匹配
     */
    private boolean matchesCondition(String condition, String pluginId, String capability,
                                      String taskDescription) {
        if (condition == null || condition.isEmpty()) return false;

        // 简单条件解析
        if (condition.contains("CRITICAL") && taskDescription != null) {
            return taskDescription.toLowerCase().contains("critical");
        }
        if (condition.contains("production") && taskDescription != null) {
            return taskDescription.toLowerCase().contains("production");
        }
        if (condition.contains("database") && taskDescription != null) {
            return taskDescription.toLowerCase().contains("database")
                    || taskDescription.toLowerCase().contains("migration");
        }
        if (condition.contains("plugin") && pluginId != null) {
            return taskDescription != null && taskDescription.toLowerCase().contains(condition.replace("plugin:", "").trim());
        }

        // 默认：宽松匹配
        return false;
    }

    /**
     * 检查 prohibition rule 是否匹配
     */
    private boolean matchesRule(String target, String taskDescription) {
        if (target == null || taskDescription == null) return false;
        return taskDescription.toLowerCase().contains(target.toLowerCase());
    }
}
