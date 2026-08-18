package com.teammind.runtime;

import com.teammind.capability.CapabilityRouter;
import com.teammind.common.ControlMode;
import com.teammind.plugin.Plugin;
import com.teammind.runtime.ProjectPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityRouterTest {

    private CapabilityRouter router;
    private ProjectPolicy policy;

    @BeforeEach
    void setUp() {
        router = new CapabilityRouter(new PolicyEngine(), null, null, null);
        policy = ProjectPolicy.builder().build();
    }

    @Test
    @DisplayName("无候选时返回需要审批的决策")
    void noCandidateReturnsNeedsApproval() {
        var decision = router.route("nonexistent_capability", List.of(), "p-1",
                "fix bug", policy, ControlMode.SUPERVISED);

        assertNotNull(decision);
        assertTrue(decision.isNeedsApproval());
        assertNull(decision.getSelectedPluginId());
    }

    @Test
    @DisplayName("inferTaskType 正确推断 auth 相关任务")
    void inferAuthTaskType() {
        assertEquals("auth_change", CapabilityRouter.inferTaskType("Implement JWT authentication"));
        assertEquals("auth_change", CapabilityRouter.inferTaskType("Add OAuth2 support"));
    }

    @Test
    @DisplayName("inferTaskType 正确推断数据库迁移任务")
    void inferDbMigrationTaskType() {
        assertEquals("db_migration", CapabilityRouter.inferTaskType("Create database migration for users table"));
        assertEquals("db_migration", CapabilityRouter.inferTaskType("Alter schema add foreign key"));
    }

    @Test
    @DisplayName("inferTaskType 识别测试生成任务")
    void inferTestTaskType() {
        assertEquals("test_generation", CapabilityRouter.inferTaskType("Write unit tests for payment module"));
        assertEquals("test_generation", CapabilityRouter.inferTaskType("Add E2E tests for login flow"));
    }

    @Test
    @DisplayName("inferTaskType 正确处理 null 和未知任务")
    void inferUnknownTaskType() {
        assertEquals("general_purpose", CapabilityRouter.inferTaskType(null));
        assertEquals("general_purpose", CapabilityRouter.inferTaskType("do something cool"));
        assertEquals("general_purpose", CapabilityRouter.inferTaskType(""));
    }

    @Test
    @DisplayName("inferTaskType 识别安全审查任务")
    void inferSecurityReviewTaskType() {
        assertEquals("security_review", CapabilityRouter.inferTaskType("Audit for vulnerabilities"));
        assertEquals("security_review", CapabilityRouter.inferTaskType("Check for safety issues"));
    }

    @Test
    @DisplayName("Policy 过滤：不允许的 Plugin 被排除")
    void policyBlocksNotAllowedPlugin() {
        var allowedPolicy = ProjectPolicy.builder()
                .capabilityPolicies(List.of(
                        ProjectPolicy.CapabilityPolicy.builder()
                                .capability("implementation")
                                .allowedPlugins(List.of("codex"))
                                .build()
                ))
                .build();

        Plugin codex = stubPlugin("codex", List.of("implementation"), 0.90);
        Plugin claude = stubPlugin("claude-code", List.of("implementation"), 0.92);

        var decision = router.route("implementation", List.of(codex, claude),
                "p-1", "fix bug", allowedPolicy, ControlMode.SUPERVISED);

        assertEquals("codex", decision.getSelectedPluginId());
        assertFalse(decision.getRejectedCandidates().isEmpty());
    }

    @Test
    @DisplayName("选择最高分 Plugin")
    void selectsHighestScoredPlugin() {
        // codex: reliability=0.95, claude: reliability=0.80
        Plugin codex = stubPlugin("codex", List.of("implementation"), 0.95);
        Plugin claude = stubPlugin("claude-code", List.of("implementation"), 0.80);

        var decision = router.route("implementation", List.of(codex, claude),
                "p-1", "fix bug", policy, ControlMode.SUPERVISED);

        assertEquals("codex", decision.getSelectedPluginId());
        assertTrue(decision.getTotalScore() > 0);
    }

    private Plugin stubPlugin(String id, List<String> capabilities, double reliability) {
        return new Plugin() {
            @Override public String id() { return id; }
            @Override public PluginType type() { return PluginType.AGENT; }
            @Override public String description() { return id; }
            @Override public String version() { return "1.0.0"; }
            @Override public PluginMetadata metadata() {
                return new PluginMetadata(id, id, "1.0.0", id,
                        capabilities, List.of(), List.of(), List.of(),
                        30000L, reliability, 0.03);
            }
            @Override public PluginResult invoke(PluginContext ctx) {
                return PluginResult.success(id, Map.of());
            }
            @Override public PluginHealth inspect() { return PluginHealth.HEALTHY; }
            @Override public void onLoad() {}
            @Override public void onUnload() {}
        };
    }
}
