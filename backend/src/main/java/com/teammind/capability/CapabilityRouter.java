package com.teammind.capability;

import com.teammind.common.AgentRole;
import com.teammind.common.ControlMode;
import com.teammind.common.ReadinessResult;
import com.teammind.plugin.Plugin;
import com.teammind.runtime.PolicyEngine;
import com.teammind.runtime.ProjectPolicy;
import com.teammind.runtime.ReadinessManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 能力路由引擎 — 8 因素加权评分 + Team Policy 硬约束
 *
 * 路由流程：
 * 1. Readiness 前置过滤（UNAVAILABLE → 排除）
 * 2. 根据 requiredCapability 找到候选 Plugin
 * 3. Policy Engine 过滤掉违反规则的 Plugin
 * 4. 对剩余候选计算 8 因素评分
 * 5. 返回最高分 Plugin + 完整得分详情
 */
@Slf4j
@Component
public class CapabilityRouter {

    // ─── 权重定义（总和 = 100）───
    private static final double WEIGHT_PROJECT_PERFORMANCE = 0.30;
    private static final double WEIGHT_TASK_TYPE = 0.20;
    private static final double WEIGHT_PHILOSOPHY = 0.15;
    private static final double WEIGHT_CAPABILITY_QUALITY = 0.12;
    private static final double WEIGHT_USER_PREFERENCE = 0.08;
    private static final double WEIGHT_AVAILABILITY = 0.05;
    private static final double MAX_COST_LATENCY_PENALTY = 15.0;
    private static final double ROUTING_LESSON_BONUS_MAX = 0.15;

    private final PolicyEngine policyEngine;
    private final ReadinessManager readinessManager;

    public CapabilityRouter(PolicyEngine policyEngine, ReadinessManager readinessManager) {
        this.policyEngine = policyEngine;
        this.readinessManager = readinessManager;
    }

    /**
     * 路由决策入口
     */
    public RoutingDecision route(String capability, List<Plugin> availablePlugins,
                                   String projectId, String taskDescription,
                                   ProjectPolicy projectPolicy, ControlMode controlMode) {
        // 0. Readiness 前置过滤（开关，不是乘数）
        List<Plugin> readinessFiltered = new ArrayList<>();
        List<RoutingDecision.RejectedCandidate> readinessRejected = new ArrayList<>();
        if (readinessManager != null) {
            for (Plugin p : availablePlugins) {
                ReadinessResult r = readinessManager.check(p.id());
                if (r.isRunnable()) {
                    readinessFiltered.add(p);
                } else {
                    readinessRejected.add(RoutingDecision.RejectedCandidate.builder()
                            .pluginId(p.id())
                            .reason("Readiness: " + r.state() + " - " + r.diagnosis())
                            .score(0.0)
                            .build());
                }
            }
        } else {
            readinessFiltered.addAll(availablePlugins);
        }

        if (readinessFiltered.isEmpty()) {
            log.warn("All candidates filtered by Readiness for capability '{}'", capability);
            return RoutingDecision.builder()
                    .capability(capability)
                    .reason("No plugin with READY/DEGRADED readiness for: " + capability)
                    .needsApproval(true)
                    .rejectedCandidates(readinessRejected)
                    .build();
        }

        // 1. 找到声明了该能力的候选
        List<Plugin> candidates = readinessFiltered.stream()
                .filter(p -> p.metadata().capabilities().contains(capability))
                .toList();

        if (candidates.isEmpty()) {
            log.warn("No plugin declares capability '{}' in project {}", capability, projectId);
            return RoutingDecision.builder()
                    .capability(capability)
                    .reason("No capable plugin found for: " + capability)
                    .needsApproval(true)
                    .rejectedCandidates(readinessRejected)
                    .build();
        }

        // 2. Policy 过滤
        List<Plugin> filtered = new ArrayList<>();
        List<RoutingDecision.RejectedCandidate> rejected = new ArrayList<>();
        for (Plugin plugin : candidates) {
            if (policyEngine.isAllowed(plugin.id(), capability, taskDescription, projectPolicy)) {
                filtered.add(plugin);
            } else {
                rejected.add(RoutingDecision.RejectedCandidate.builder()
                        .pluginId(plugin.id())
                        .reason("Blocked by Project Policy")
                        .score(0.0)
                        .build());
            }
        }

        if (filtered.isEmpty()) {
            log.warn("All candidates blocked by Policy for capability '{}'", capability);
            List<RoutingDecision.RejectedCandidate> allRejected = new ArrayList<>(readinessRejected);
            allRejected.addAll(rejected);
            return RoutingDecision.builder()
                    .capability(capability)
                    .reason("All plugins blocked by Project Policy")
                    .needsApproval(true)
                    .rejectedCandidates(allRejected)
                    .build();
        }

        // 3. 计算 8 因素评分
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Plugin plugin : filtered) {
            double score = calculateScore(plugin, capability, taskDescription, projectPolicy);
            scores.put(plugin.id(), score);
            log.debug("Routing score: plugin={} capability={} score={}", plugin.id(), capability, score);
        }

        // 4. 选最高分
        String bestPluginId = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(filtered.get(0).id());

        Plugin bestPlugin = filtered.stream()
                .filter(p -> p.id().equals(bestPluginId))
                .findFirst().orElse(filtered.get(0));

        // 5. 判断是否需要审批
        boolean needsApproval = policyEngine.needsApproval(bestPluginId, capability, taskDescription, projectPolicy)
                || controlMode == ControlMode.MANUAL;

        // 6. 构建决策
        List<RoutingDecision.RejectedCandidate> allRejected = new ArrayList<>(readinessRejected);
        allRejected.addAll(rejected);

        RoutingDecision.RoutingDecisionBuilder builder = RoutingDecision.builder()
                .selectedPluginId(bestPluginId)
                .selectedPluginName(bestPlugin.metadata().name())
                .capability(capability)
                .scoreBreakdown(scores)
                .totalScore(scores.get(bestPluginId))
                .reason("Selected " + bestPluginId + " (score=" + String.format("%.2f", scores.get(bestPluginId)) + ")");

        if (needsApproval) {
            builder.needsApproval(true)
                    .nextAction("Wait for user approval before executing");
        }

        if (!allRejected.isEmpty()) {
            builder.rejectedCandidates(allRejected);
        }

        log.info("Routing decision: capability={} -> plugin={} (score={})",
                capability, bestPluginId, scores.get(bestPluginId));

        return builder.build();
    }

    /**
     * 8 因素评分
     */
    private double calculateScore(Plugin plugin, String capability,
                                    String taskDescription, ProjectPolicy projectPolicy) {
        double total = 0.0;

        // 因素 1：项目级历史表现（30%）
        total += projectPerformanceScore(plugin, capability) * WEIGHT_PROJECT_PERFORMANCE * 100;

        // 因素 2：任务类型级表现（20%）
        String taskType = inferTaskType(taskDescription);
        total += taskTypeScore(plugin, taskType) * WEIGHT_TASK_TYPE * 100;

        // 因素 3：哲学匹配（15%）
        total += philosophyScore(plugin, taskDescription) * WEIGHT_PHILOSOPHY * 100;

        // 因素 4：能力声明质量（12%）
        total += capabilityQualityScore(plugin, capability) * WEIGHT_CAPABILITY_QUALITY * 100;

        // 因素 5：用户偏好（8%）
        total += userPreferenceScore(plugin) * WEIGHT_USER_PREFERENCE * 100;

        // 因素 6：可用性（5%）
        total += availabilityScore(plugin) * WEIGHT_AVAILABILITY * 100;

        // 扣分项：成本与延迟（最多 -15 分）
        total -= costLatencyPenalty(plugin.metadata());

        // Routing Lesson 加成（最多 +15%）
        total += routingLessonBonus(plugin, capability, taskDescription) * 100;

        return Math.max(0, total);
    }

    /** 因素 1：项目级历史表现 */
    private double projectPerformanceScore(Plugin plugin, String capability) {
        Double reliability = plugin.metadata().reliabilityScore();
        return reliability != null ? reliability : 0.5;
    }

    /** 因素 2：任务类型级表现 */
    private double taskTypeScore(Plugin plugin, String taskType) {
        return 0.5;
    }

    /** 因素 3：哲学匹配 */
    private double philosophyScore(Plugin plugin, String taskDescription) {
        List<String> philosophies = plugin.metadata().philosophies();
        if (philosophies.isEmpty()) return 0.75;
        List<String> hints = inferPhilosophyHints(taskDescription);
        long matches = hints.stream()
                .filter(philosophies::contains)
                .count();
        return hints.isEmpty() ? 0.75 : (double) matches / hints.size();
    }

    /** 因素 4：能力声明质量 */
    private double capabilityQualityScore(Plugin plugin, String capability) {
        return plugin.metadata().capabilities().contains(capability) ? 1.0 : 0.0;
    }

    /** 因素 5：用户偏好 */
    private double userPreferenceScore(Plugin plugin) {
        return 0.0;
    }

    /** 因素 6：可用性（基于 ReadinessState） */
    private double availabilityScore(Plugin plugin) {
        try {
            if (readinessManager != null) {
                ReadinessResult r = readinessManager.check(plugin.id());
                return switch (r.state()) {
                    case READY -> 1.0;
                    case DEGRADED -> 0.5;
                    default -> 0.0;
                };
            }
            return plugin.inspect() != Plugin.PluginHealth.DOWN ? 1.0 : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** 扣分项 */
    private double costLatencyPenalty(Plugin.PluginMetadata meta) {
        double penalty = 0;
        Long latency = meta.avgLatencyMs();
        if (latency != null) {
            penalty += latency / 1000.0;
        }
        Double cost = meta.costPerInvocation();
        if (cost != null) {
            penalty += cost * 5;
        }
        return Math.min(penalty, MAX_COST_LATENCY_PENALTY);
    }

    /** Routing Lesson 加成 */
    private double routingLessonBonus(Plugin plugin, String capability, String taskDescription) {
        return 0.0;
    }

    /**
     * 从任务描述推断任务类型
     */
    public static String inferTaskType(String objective) {
        if (objective == null) return "general_purpose";
        String lower = objective.toLowerCase();
        if (lower.contains("parser") || lower.contains("grammar") || lower.contains("syntax")) return "parser_refactor";
        if (lower.contains("auth") || lower.contains("jwt") || lower.contains("oauth") || lower.contains("permission")) return "auth_change";
        if (lower.contains("database") || lower.contains("migration") || lower.contains("schema")) return "db_migration";
        if (lower.contains("refactor") || lower.contains("restructure")) return "large_refactor";
        if (lower.contains("security") || lower.contains("vulnerab") || lower.contains("safety")) return "security_review";
        if (lower.contains("test") || lower.contains("e2e") || lower.contains("unit")) return "test_generation";
        if (lower.contains("api") || lower.contains("endpoint") || lower.contains("route")) return "api_design";
        if (lower.contains("doc") || lower.contains("readme")) return "documentation";
        if (lower.contains("bug") || lower.contains("fix")) return "bug_fix";
        return "general_purpose";
    }

    /**
     * 从任务描述推断哲学提示
     */
    private List<String> inferPhilosophyHints(String taskDescription) {
        if (taskDescription == null) return List.of();
        String lower = taskDescription.toLowerCase();
        List<String> hints = new ArrayList<>();
        if (lower.contains("security") || lower.contains("safe") || lower.contains("review")) {
            hints.add("safety");
            hints.add("controlled_action");
        }
        if (lower.contains("implement") || lower.contains("build") || lower.contains("create")) {
            hints.add("execution");
            hints.add("iterative_build");
        }
        if (lower.contains("refactor") || lower.contains("redesign")) {
            hints.add("broad_refactor");
        }
        if (lower.contains("test") || lower.contains("verify")) {
            hints.add("verification");
        }
        return hints.isEmpty() ? List.of("general") : hints;
    }
}
