package com.teammind.capability;

import com.teammind.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityRegistryTest {

    private CapabilityRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CapabilityRegistry();
    }

    @Test
    @DisplayName("注册后按能力可查")
    void findByCapability() {
        registry.register(stubPlugin("codex", List.of("implementation", "test")));

        var found = registry.findByCapability("implementation");
        assertEquals(1, found.size());
        assertEquals("codex", found.get(0).id());
    }

    @Test
    @DisplayName("注销后能力消失")
    void unregisterRemovesCapability() {
        Plugin p = stubPlugin("codex", List.of("implementation"));
        registry.register(p);
        registry.unregister(p);

        assertTrue(registry.findByCapability("implementation").isEmpty());
    }

    @Test
    @DisplayName("findBest 返回可靠性最高的")
    void findBestByCapability() {
        registry.register(stubPlugin("a", List.of("review"), 0.80));
        registry.register(stubPlugin("b", List.of("review"), 0.95));
        registry.register(stubPlugin("c", List.of("review"), 0.70));

        var best = registry.findBestByCapability("review");
        assertTrue(best.isPresent());
        assertEquals("b", best.get().id());
    }

    @Test
    @DisplayName("不存在的 capability 返回空列表")
    void emptyForUnknownCapability() {
        assertTrue(registry.findByCapability("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("allCapabilities 列出所有已注册能力")
    void allCapabilities() {
        registry.register(stubPlugin("a", List.of("impl", "review")));
        registry.register(stubPlugin("b", List.of("test", "review")));

        var caps = registry.allCapabilities();
        assertTrue(caps.contains("impl"));
        assertTrue(caps.contains("review"));
        assertTrue(caps.contains("test"));
        assertEquals(3, caps.size());
    }

    @Test
    @DisplayName("findByCapabilityAndQuality 过滤低质量")
    void filterByQuality() {
        registry.register(stubPlugin("good", List.of("impl"), 0.90));
        registry.register(stubPlugin("bad", List.of("impl"), 0.40));

        var excellent = registry.findByCapabilityAndQuality("impl", Capability.Quality.EXCELLENT);
        var fair = registry.findByCapabilityAndQuality("impl", Capability.Quality.FAIR);

        assertEquals(1, excellent.size());
        assertEquals("good", excellent.get(0).id());
        // reliability 0.40 < 0.5 (FAIR threshold), so only "good" qualifies
        assertEquals(1, fair.size());
    }

    @Test
    @DisplayName("hasCapability 检查能力是否注册")
    void hasCapability() {
        registry.register(stubPlugin("a", List.of("impl")));
        assertTrue(registry.hasCapability("impl"));
        assertFalse(registry.hasCapability("missing"));
    }

    private Plugin stubPlugin(String id, List<String> caps) {
        return stubPlugin(id, caps, 0.85);
    }

    private Plugin stubPlugin(String id, List<String> caps, double reliability) {
        return new Plugin() {
            @Override public String id() { return id; }
            @Override public PluginType type() { return PluginType.AGENT; }
            @Override public String description() { return id; }
            @Override public String version() { return "1.0.0"; }
            @Override public PluginMetadata metadata() {
                return new PluginMetadata(id, id, "1.0.0", id,
                        caps, List.of(), List.of(), List.of(),
                        30000L, reliability, 0.03);
            }
            @Override public PluginResult invoke(PluginContext ctx) {
                return PluginResult.success(id, null);
            }
            @Override public PluginHealth inspect() { return PluginHealth.HEALTHY; }
            @Override public void onLoad() {}
            @Override public void onUnload() {}
        };
    }
}
