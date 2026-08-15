package com.teammind.plugin;

import com.teammind.event.EventBus;
import com.teammind.plugin.agent.ClaudeCodePlugin;
import com.teammind.plugin.agent.CodexPlugin;
import com.teammind.plugin.verifier.GitVerifier;
import com.teammind.plugin.verifier.TestRunnerVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PluginRegistryTest {

    private PluginManager manager;
    private PluginRegistry registry;

    @BeforeEach
    void setUp() {
        com.teammind.event.EventBus bus = new com.teammind.event.EventBus(new com.fasterxml.jackson.databind.ObjectMapper());
        manager = new PluginManager(bus);
        registry = new PluginRegistry(bus, manager);
    }

    @Test
    @DisplayName("registerAll 注册所有内置插件")
    void registerAll() {
        registry.registerAll();

        var all = manager.getAll();
        assertEquals(4, all.size());

        var ids = all.stream().map(Plugin::id).toList();
        assertTrue(ids.contains("claude-code"));
        assertTrue(ids.contains("codex"));
        assertTrue(ids.contains("git-verifier"));
        assertTrue(ids.contains("test-runner-verifier"));
    }

    @Test
    @DisplayName("registeredIds 返回所有插件 ID")
    void registeredIds() {
        registry.registerAll();
        var ids = registry.registeredIds();
        assertEquals(4, ids.size());
        assertTrue(ids.contains("claude-code"));
        assertTrue(ids.contains("codex"));
    }

    @Test
    @DisplayName("每个插件类型正确")
    void correctTypes() {
        registry.registerAll();
        var byId = manager.getAll().stream()
                .collect(java.util.stream.Collectors.toMap(Plugin::id, p -> p));

        assertEquals(Plugin.PluginType.AGENT, byId.get("claude-code").type());
        assertEquals(Plugin.PluginType.AGENT, byId.get("codex").type());
        assertEquals(Plugin.PluginType.VERIFIER, byId.get("git-verifier").type());
        assertEquals(Plugin.PluginType.VERIFIER, byId.get("test-runner-verifier").type());
    }
}
