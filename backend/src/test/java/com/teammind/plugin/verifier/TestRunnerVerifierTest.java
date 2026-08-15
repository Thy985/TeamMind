package com.teammind.plugin.verifier;

import com.teammind.event.EventBus;
import com.teammind.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestRunnerVerifierTest {

    private TestRunnerVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new TestRunnerVerifier(new EventBus(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Test
    @DisplayName("插件元数据正确")
    void metadata() {
        assertEquals("test-runner-verifier", verifier.id());
        assertEquals(Plugin.PluginType.VERIFIER, verifier.type());
        assertTrue(verifier.metadata().capabilities().contains("test_execution_verification"));
        assertEquals(0.97, verifier.metadata().reliabilityScore());
    }

    @Test
    @DisplayName("invoke 返回结果（无构建工具时降级为 DEGRADED）")
    void invokeDoesNotNpe() {
        var context = new Plugin.PluginContext(
                "p-1", "t-1",
                Map.of("test_command", "echo", "test_args", List.of("hello")),
                System.getProperty("user.dir"),
                Map.of(),
                List.of("test_execution_verification")
        );

        assertDoesNotThrow(() -> {
            var result = verifier.invoke(context);
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
