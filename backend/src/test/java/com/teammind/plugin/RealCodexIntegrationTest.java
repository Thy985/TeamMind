package com.teammind.plugin;

import com.teammind.event.EventBus;
import com.teammind.plugin.agent.CodexPlugin;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 Codex CLI 集成测试
 */
@SpringBootTest(classes = com.teammind.TeamMindApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RealCodexIntegrationTest {

    @Autowired private CodexPlugin codexPlugin;
    @Autowired private EventBus eventBus;

    @Test
    @DisplayName("[E2E] 真实 Codex CLI 调用 — 返回有效输出")
    void realCodexInvocation() throws Exception {
        System.out.println("\n=== Real Codex CLI Integration Test ===");

        // 直接调用插件
        Plugin.PluginContext ctx = new Plugin.PluginContext(
                "e2e-project", "e2e-task-001",
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
        System.out.println("  - data type: " + (result.data() != null ? result.data().getClass().getSimpleName() : "null"));
        System.out.println("  - error: " + result.error());

        // 验证结果
        assertTrue(result.success(), "Codex invocation should succeed");
        assertNotNull(result.data(), "Data should not be null");
        assertTrue(elapsedMs > 0, "Should take some time");

        // 检查事件总线
        System.out.println("[E2E] Event bus active: " + (eventBus != null));
        System.out.println("=== Test completed ===\n");
    }
}
