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
 * 真实 Atomcode CLI 集成测试
 */
@SpringBootTest(classes = com.teammind.TeamMindApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RealAtomcodeIntegrationTest {

    @Autowired private PluginManager pluginManager;
    @Autowired private com.teammind.runtime.ReadinessManager readinessManager;
    @Autowired private EventBus eventBus;

    @Test
    @DisplayName("[E2E] 真实 Atomcode CLI 调用 — 返回有效输出")
    void realAtomcodeInvocation() throws Exception {
        System.out.println("\n=== Real Atomcode CLI Integration Test ===");

        // 获取插件
        var atomcodeOpt = pluginManager.findById("atomcode");
        Assumptions.assumeTrue(atomcodeOpt.isPresent(), "Atomcode plugin not registered");

        Plugin atomcodePlugin = atomcodeOpt.get();

        com.teammind.common.ReadinessResult readiness = readinessManager.check("atomcode");
        Assumptions.assumeTrue(!readiness.isUnavailable(),
                "Atomcode unavailable: " + readiness.diagnosis());

        // 调用插件
        Plugin.PluginContext ctx = new Plugin.PluginContext(
                "e2e-project", "e2e-task-003",
                Map.of("prompt", "Reply with exactly: TEAMMIND_E2E_OK atomcode"),
                System.getProperty("user.dir"),
                Map.of(),
                List.of("implementation")
        );

        long startMs = System.currentTimeMillis();
        Plugin.PluginResult result = atomcodePlugin.invoke(ctx);
        long elapsedMs = System.currentTimeMillis() - startMs;

        System.out.println("[E2E] Atomcode invocation result:");
        System.out.println("  - success: " + result.success());
        System.out.println("  - elapsed: " + elapsedMs + "ms");
        System.out.println("  - pluginId: " + result.pluginId());
        System.out.println("  - data type: " + (result.data() != null ? result.data().getClass().getSimpleName() : "null"));
        System.out.println("  - error: " + result.error());

        // 验证结果
        assertTrue(result.success(), "Atomcode invocation should succeed");
        assertNotNull(result.data(), "Data should not be null");
        assertTrue(elapsedMs > 0, "Should take some time");
        assertTrue(elapsedMs < 60000, "Should complete within 60 seconds");

        System.out.println("=== Test completed ===\n");
    }
}
