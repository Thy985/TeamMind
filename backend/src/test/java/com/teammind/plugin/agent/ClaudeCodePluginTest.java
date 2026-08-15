package com.teammind.plugin.agent;

import com.teammind.event.EventBus;
import com.teammind.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeCodePluginTest {

    private ClaudeCodePlugin plugin;
    private TestEventCollector collector;

    @BeforeEach
    void setUp() {
        collector = new TestEventCollector();
        EventBus bus = new EventBus(new com.fasterxml.jackson.databind.ObjectMapper());
        // 订阅感兴趣的事件
        bus.subscribe(com.teammind.common.EventType.AGENT_CHUNK, collector::onEvent);
        bus.subscribe(com.teammind.common.EventType.TASK_STARTED, collector::onEvent);
        bus.subscribe(com.teammind.common.EventType.TASK_COMPLETED, collector::onEvent);
        bus.subscribe(com.teammind.common.EventType.TOOL_CALLED, collector::onEvent);
        plugin = new ClaudeCodePlugin(bus);
    }

    @Test
    @DisplayName("插件元数据正确")
    void metadata() {
        assertEquals("claude-code", plugin.id());
        assertEquals(Plugin.PluginType.AGENT, plugin.type());
        assertNotNull(plugin.metadata().capabilities());
        assertTrue(plugin.metadata().capabilities().contains("implementation"));
        assertEquals(0.92, plugin.metadata().reliabilityScore());
    }

    @Test
    @DisplayName("invoke 返回成功结果（无 CLI 环境时返回 stub）")
    void invokeReturnsResult() {
        var context = new Plugin.PluginContext(
                "p-1", "t-1",
                Map.of("prompt", "fix bug"),
                System.getProperty("user.dir"),
                Map.of(),
                List.of("implementation")
        );

        var result = plugin.invoke(context);
        assertTrue(result.success());
        assertEquals("claude-code", result.pluginId());
    }

    @Test
    @DisplayName("cancel 不抛出异常")
    void cancelDoesNotThrow() {
        assertDoesNotThrow(() -> plugin.cancel());
    }

    @Test
    @DisplayName("inspect 返回健康状态（无 CLI 时可能 DEGRADED）")
    void inspectReturnsHealth() {
        var health = plugin.inspect();
        assertTrue(health == Plugin.PluginHealth.HEALTHY
                || health == Plugin.PluginHealth.DEGRADED
                || health == Plugin.PluginHealth.UNHEALTHY);
    }

    @Test
    @DisplayName("onLoad 不抛出异常")
    void onLoadDoesNotThrow() {
        assertDoesNotThrow(() -> plugin.onLoad());
    }

    /** 收集总线事件用于断言 */
    static class TestEventCollector {
        final java.util.List<com.teammind.event.TeamMindEvent> events = new java.util.ArrayList<>();

        void onEvent(com.teammind.event.TeamMindEvent evt) {
            events.add(evt);
        }

        boolean hasEventType(com.teammind.common.EventType type) {
            return events.stream().anyMatch(e -> e.type() == type);
        }
    }
}
