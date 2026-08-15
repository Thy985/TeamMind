package com.teammind.plugin.agent;

import com.teammind.event.EventBus;
import com.teammind.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CodexPluginTest {

    private CodexPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new CodexPlugin(new EventBus(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Test
    @DisplayName("插件元数据正确")
    void metadata() {
        assertEquals("codex", plugin.id());
        assertEquals(Plugin.PluginType.AGENT, plugin.type());
        assertTrue(plugin.metadata().capabilities().contains("implementation"));
        assertTrue(plugin.metadata().capabilities().contains("test_generation"));
        assertEquals(0.90, plugin.metadata().reliabilityScore());
    }

    @Test
    @DisplayName("invoke 不抛出异常（无 CLI 时返回失败结果）")
    void invokeDoesNotThrow() {
        var context = new Plugin.PluginContext(
                "p-1", "t-1",
                Map.of("prompt", "add unit test"),
                System.getProperty("user.dir"),
                Map.of(),
                List.of("test_generation")
        );

        // codex CLI 可能未安装，不断言 success，只断言不抛异常
        assertDoesNotThrow(() -> plugin.invoke(context));
    }

    @Test
    @DisplayName("cancel 不抛出异常")
    void cancelDoesNotThrow() {
        assertDoesNotThrow(() -> plugin.cancel());
    }

    @Test
    @DisplayName("inspect 返回健康状态")
    void inspectReturnsHealth() {
        var health = plugin.inspect();
        assertTrue(health == Plugin.PluginHealth.HEALTHY
                || health == Plugin.PluginHealth.DEGRADED
                || health == Plugin.PluginHealth.UNHEALTHY);
    }
}
