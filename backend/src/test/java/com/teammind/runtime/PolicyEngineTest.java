package com.teammind.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolicyEngineTest {

    private final PolicyEngine engine = new PolicyEngine();

    @Test
    @DisplayName("null policy 允许所有操作")
    void nullPolicyAllowsAll() {
        assertTrue(engine.isAllowed("any-plugin", "implementation", "fix bug", null));
        assertFalse(engine.needsApproval("any-plugin", "implementation", "fix bug", null));
    }

    @Test
    @DisplayName("allowedPlugins 排除不在列表中的 Plugin")
    void allowedPluginsBlocksOutsidePlugin() {
        var policy = ProjectPolicy.builder()
                .capabilityPolicies(List.of(
                        ProjectPolicy.CapabilityPolicy.builder()
                                .capability("implementation")
                                .allowedPlugins(List.of("codex"))
                                .build()
                ))
                .build();

        assertTrue(engine.isAllowed("codex", "implementation", "fix bug", policy));
        assertFalse(engine.isAllowed("claude-code", "implementation", "fix bug", policy));
    }

    @Test
    @DisplayName("requiresReview 触发审批需求")
    void requiresReviewTriggersApproval() {
        var policy = ProjectPolicy.builder()
                .capabilityPolicies(List.of(
                        ProjectPolicy.CapabilityPolicy.builder()
                                .capability("security_review")
                                .requiresReview(true)
                                .reviewBy(List.of("claude-code"))
                                .build()
                ))
                .build();

        // requiresReview 不阻止执行（仅标记需要审批）
        assertTrue(engine.isAllowed("claude-code", "security_review", "check auth", policy));
        // 但 needsApproval 返回 true
        assertTrue(engine.needsApproval("claude-code", "security_review", "check auth", policy));
    }

    @Test
    @DisplayName("prohibitionRule HARD 级别直接拒绝")
    void hardProhibitionBlocksExecution() {
        var policy = ProjectPolicy.builder()
                .prohibitionRules(List.of(
                        ProjectPolicy.ProhibitionRule.builder()
                                .target("skip_tests")
                                .reason("Tests cannot be skipped")
                                .severity(ProjectPolicy.ProhibitionRule.Severity.HARD)
                                .build()
                ))
                .build();

        assertFalse(engine.isAllowed("codex", "implementation", "skip_tests and deploy", policy));
    }

    @Test
    @DisplayName("prohibitionRule SOFT 级别仅警告不阻止")
    void softProhibitionOnlyWarns() {
        var policy = ProjectPolicy.builder()
                .prohibitionRules(List.of(
                        ProjectPolicy.ProhibitionRule.builder()
                                .target("experimental")
                                .reason("Use with caution")
                                .severity(ProjectPolicy.ProhibitionRule.Severity.SOFT)
                                .build()
                ))
                .build();

        // SOFT 不应阻止执行
        assertTrue(engine.isAllowed("codex", "implementation", "experimental feature", policy));
    }

    @Test
    @DisplayName("fallback 行为默认返回 PAUSE")
    void fallbackDefaultsToPause() {
        var action = engine.getFallbackAction("codex", "implementation", "fix bug", null);
        assertEquals(ProjectPolicy.FallbackAction.PAUSE, action);
    }
}
