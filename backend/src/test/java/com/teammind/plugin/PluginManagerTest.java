package com.teammind.plugin;

import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PluginManagerTest {

    private PluginManager manager;

    @BeforeEach
    void setUp() {
        manager = new PluginManager(new EventBus(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Test
    @DisplayName("注册后可以通过 ID 查找")
    void findAfterRegister() {
        Plugin p = stubPlugin("codex");
        manager.register(p);

        assertTrue(manager.findById("codex").isPresent());
        assertEquals("codex", manager.findById("codex").get().id());
    }

    @Test
    @DisplayName("注销后无法查找")
    void notFoundAfterUnregister() {
        Plugin p = stubPlugin("claude");
        manager.register(p);
        manager.unregister("claude");

        assertFalse(manager.findById("claude").isPresent());
    }

    @Test
    @DisplayName("按类型查找返回正确结果")
    void findByType() {
        manager.register(stubPlugin("codex"));
        manager.register(stubPlugin("claude"));

        List<Plugin> agents = manager.findByType(Plugin.PluginType.AGENT);
        List<Plugin> verifiers = manager.findByType(Plugin.PluginType.VERIFIER);

        assertEquals(2, agents.size());
        assertEquals(0, verifiers.size());
    }

    @Test
    @DisplayName("按能力查找")
    void findByCapability() {
        Plugin p1 = stubPluginWithCapabilities("codex", List.of("implementation", "test"));
        Plugin p2 = stubPluginWithCapabilities("claude", List.of("code_review", "security_review"));
        manager.register(p1);
        manager.register(p2);

        List<Plugin> implPlugins = manager.findByCapability("implementation");
        List<Plugin> reviewPlugins = manager.findByCapability("code_review");

        assertEquals(1, implPlugins.size());
        assertEquals("codex", implPlugins.get(0).id());
        assertEquals(1, reviewPlugins.size());
        assertEquals("claude", reviewPlugins.get(0).id());
    }

    @Test
    @DisplayName("重复注册会替换旧实例")
    void replaceOnDuplicateRegister() {
        manager.register(stubPlugin("codex"));
        manager.register(stubPluginWithVersion("codex", "2.0"));

        assertEquals(1, manager.getAll().size());
        assertEquals("2.0", manager.findById("codex").get().version());
    }

    @Test
    @DisplayName("健康检查返回所有插件状态")
    void healthCheck() {
        manager.register(stubPlugin("codex"));
        manager.register(stubPlugin("claude"));

        var health = manager.checkAllHealth();
        assertEquals(2, health.size());
        assertTrue(health.values().stream().allMatch(h -> h == Plugin.PluginHealth.HEALTHY));
    }

    @Test
    @DisplayName("getAll 返回所有注册插件")
    void getAll() {
        manager.register(stubPlugin("a"));
        manager.register(stubPlugin("b"));
        manager.register(stubPlugin("c"));

        assertEquals(3, manager.getAll().size());
    }

    private Plugin stubPlugin(String id) {
        return new Plugin() {
            @Override public String id() { return id; }
            @Override public PluginType type() { return PluginType.AGENT; }
            @Override public String description() { return id; }
            @Override public String version() { return "1.0.0"; }
            @Override public PluginMetadata metadata() {
                return PluginMetadata.empty(id);
            }
            @Override public PluginResult invoke(PluginContext ctx) {
                return PluginResult.success(id, Map.of());
            }
            @Override public PluginHealth inspect() { return PluginHealth.HEALTHY; }
            @Override public void onLoad() {}
            @Override public void onUnload() {}
        };
    }

    private Plugin stubPluginWithVersion(String id, String version) {
        return new Plugin() {
            @Override public String id() { return id; }
            @Override public PluginType type() { return PluginType.AGENT; }
            @Override public String description() { return id; }
            @Override public String version() { return version; }
            @Override public PluginMetadata metadata() {
                return PluginMetadata.empty(id);
            }
            @Override public PluginResult invoke(PluginContext ctx) {
                return PluginResult.success(id, Map.of());
            }
            @Override public PluginHealth inspect() { return PluginHealth.HEALTHY; }
            @Override public void onLoad() {}
            @Override public void onUnload() {}
        };
    }

    private Plugin stubPluginWithCapabilities(String id, List<String> caps) {
        return new Plugin() {
            @Override public String id() { return id; }
            @Override public PluginType type() { return PluginType.AGENT; }
            @Override public String description() { return id; }
            @Override public String version() { return "1.0.0"; }
            @Override public PluginMetadata metadata() {
                return new PluginMetadata(id, id, "1.0.0", id,
                        caps, List.of(), List.of(), List.of(),
                        null, null, null);
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
