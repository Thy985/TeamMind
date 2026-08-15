package com.teammind.plugin.verifier;

import com.teammind.event.EventBus;
import com.teammind.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GitVerifierTest {

    private GitVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new GitVerifier(new EventBus(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Test
    @DisplayName("插件元数据正确")
    void metadata() {
        assertEquals("git-verifier", verifier.id());
        assertEquals(Plugin.PluginType.VERIFIER, verifier.type());
        assertTrue(verifier.metadata().capabilities().contains("git_diff_verification"));
        assertEquals(0.99, verifier.metadata().reliabilityScore());
        assertEquals(0.0, verifier.metadata().costPerInvocation());
    }

    @Test
    @DisplayName("invoke 返回验证结果（在无 git 目录时可能失败但不应 NPE）")
    void invokeDoesNotNpe() {
        var context = new Plugin.PluginContext(
                "p-1", "t-1",
                Map.of("expected_files", List.of("src/main/java")),
                System.getProperty("user.dir"),
                Map.of(),
                List.of("git_diff_verification")
        );

        assertDoesNotThrow(() -> {
            var result = verifier.invoke(context);
            // 在有 git 仓库的环境中验证通过，否则可能失败但不会抛异常
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("inspect 返回健康状态")
    void inspectReturnsHealth() {
        var health = verifier.inspect();
        assertTrue(health == Plugin.PluginHealth.HEALTHY
                || health == Plugin.PluginHealth.DEGRADED
                || health == Plugin.PluginHealth.UNHEALTHY);
    }

    @Test
    @DisplayName("onLoad 不抛出异常")
    void onLoadDoesNotThrow() {
        assertDoesNotThrow(() -> verifier.onLoad());
    }
}
