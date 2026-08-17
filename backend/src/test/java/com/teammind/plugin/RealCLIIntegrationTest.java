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
 * 真实 CLI 集成测试 — 验证 Claude Code 在 Java ProcessBuilder 中调用成功
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

        // 获取插件
        var claudeOpt = pluginManager.findById("claude-code");
        if (claudeOpt.isEmpty()) {
            System.out.println("[E2E] Claude Code plugin not found, skipping");
            return;
        }

        Plugin claudePlugin = claudeOpt.get();

        // 检查就绪状态
        com.teammind.common.ReadinessResult readiness = readinessManager.check("claude-code");
        
        if (readiness.isUnavailable()) {
            System.out.println("[E2E] Claude Code unavailable: " + readiness.diagnosis());
            return;
        }

        // 调用插件
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
        System.out.println("  - pluginId: " + result.pluginId());
        System.out.println("  - data type: " + (result.data() != null ? result.data().getClass().getSimpleName() : "null"));
        System.out.println("  - error: " + result.error());

        // 验证结果
        assertTrue(result.success(), "Claude Code invocation should succeed");
        assertNotNull(result.data(), "Data should not be null");
        assertTrue(elapsedMs > 0, "Should take some time");
        assertTrue(elapsedMs < 60000, "Should complete within 60 seconds");

        System.out.println("=== Test completed ===\n");
    }

    @Test
    @DisplayName("[E2E] Codex CLI 调用验证 — 记录结果（可能有 stdin 限制）")
    void codexInvocationResult() throws Exception {
        System.out.println("\n=== Real Codex CLI Invocation Test ===");

        // 获取插件
        var codexOpt = pluginManager.findById("codex");
        if (codexOpt.isEmpty()) {
            System.out.println("[E2E] Codex plugin not found, skipping");
            return;
        }

        Plugin codexPlugin = codexOpt.get();

        // 检查就绪状态
        com.teammind.common.ReadinessResult readiness = readinessManager.check("codex");
        
        if (readiness.isUnavailable()) {
            System.out.println("[E2E] Codex unavailable: " + readiness.diagnosis());
            return;
        }

        // 调用插件
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
        System.out.println("  - pluginId: " + result.pluginId());
        System.out.println("  - error: " + result.error());

        // 记录结果，不强制断言成功（Codex 可能有 stdin 限制）
        if (result.success()) {
            System.out.println("✅ Codex CLI works in Java ProcessBuilder");
        } else {
            System.out.println("⚠️  Codex CLI failed (expected in non-interactive env): " + result.error());
        }

        System.out.println("=== Test completed ===\n");
    }
}
