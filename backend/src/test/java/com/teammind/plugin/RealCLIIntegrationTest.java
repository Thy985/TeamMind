package com.teammind.plugin;

import com.teammind.event.EventBus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 CLI 集成测试 — 验证 Claude Code / Codex 在 Java ProcessBuilder 中调用成功
 *
 * 原则：CLI 不在 PATH → assumeTrue 跳过（标记 SKIPPED 而非静默 PASS）
 *       CLI 在 PATH → 必须真实调用并断言成功
 */
@SpringBootTest(classes = com.teammind.TeamMindApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RealCLIIntegrationTest {

    @Autowired private PluginManager pluginManager;
    @Autowired private com.teammind.runtime.ReadinessManager readinessManager;
    @Autowired private EventBus eventBus;

    @Test
    @DisplayName("[E2E] 真实 Claude Code CLI 调用 — 返回有效输出")
    void realClaudeInvocation() throws Exception {
        System.out.println("\n=== Real Claude Code CLI Integration Test ===");

        var claudeOpt = pluginManager.findById("claude-code");
        Assumptions.assumeTrue(claudeOpt.isPresent(), "Claude Code plugin not registered");

        Plugin claudePlugin = claudeOpt.get();

        com.teammind.common.ReadinessResult readiness = readinessManager.check("claude-code");
        Assumptions.assumeTrue(!readiness.isUnavailable(),
                "Claude Code unavailable: " + readiness.diagnosis());

        Plugin.PluginContext ctx = new Plugin.PluginContext(
                "e2e-project", "e2e-task-001",
                Map.of("prompt", "Reply with exactly: TEAMMIND_E2E_OK claude"),
                System.getProperty("user.dir"),
                Map.of(),
                List.of("review")
        );

        long startMs = System.currentTimeMillis();
        Plugin.PluginResult result = claudePlugin.invoke(ctx);
        long elapsedMs = System.currentTimeMillis() - startMs;

        System.out.println("[E2E] Claude Code invocation result:");
        System.out.println("  - success: " + result.success());
        System.out.println("  - elapsed: " + elapsedMs + "ms");
        System.out.println("  - error: " + result.error());

        assertTrue(result.success(), "Claude Code invocation should succeed");
        assertNotNull(result.data(), "Data should not be null");
        assertTrue(elapsedMs > 0, "Should take some time");
        assertTrue(elapsedMs < 60000, "Should complete within 60 seconds");

        System.out.println("=== Test completed ===\n");
    }

    @Test
    @DisplayName("[E2E] Codex CLI 调用验证 — 真实调用并断言")
    void codexInvocationResult() throws Exception {
        System.out.println("\n=== Real Codex CLI Invocation Test ===");

        var codexOpt = pluginManager.findById("codex");
        Assumptions.assumeTrue(codexOpt.isPresent(), "Codex plugin not registered");

        Plugin codexPlugin = codexOpt.get();

        com.teammind.common.ReadinessResult readiness = readinessManager.check("codex");
        Assumptions.assumeTrue(!readiness.isUnavailable(),
                "Codex unavailable: " + readiness.diagnosis());

        Plugin.PluginContext ctx = new Plugin.PluginContext(
                "e2e-project", "e2e-task-002",
                Map.of("prompt", "Reply with exactly: TEAMMIND_E2E_OK codex"),
                System.getProperty("user.dir"),
                Map.of(),
                List.of("implementation")
        );

        long startMs = System.currentTimeMillis();
        Plugin.PluginResult result = codexPlugin.invoke(ctx);
        long elapsedMs = System.currentTimeMillis() - startMs;

        System.out.println("[E2E] Codex invocation result:");
        System.out.println("  - success: " + result.success());
        System.out.println("  - elapsed: " + elapsedMs + "ms");
        System.out.println("  - error: " + result.error());

        assertTrue(result.success(), "Codex CLI invocation should succeed");
        assertNotNull(result.data(), "Data should not be null");

        System.out.println("=== Test completed ===\n");
    }
}
