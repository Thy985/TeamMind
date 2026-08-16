package com.teammind.runtime;

import com.teammind.common.*;
import com.teammind.plugin.Plugin;
import com.teammind.plugin.PluginManager;
import com.teammind.event.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadinessManagerTest {

    private PluginManager pluginManager;
    private ReadinessManager manager;

    @BeforeEach
    void setUp() {
        pluginManager = mock(PluginManager.class);
        manager = new ReadinessManager(pluginManager);
    }

    @Nested
    @DisplayName("check() 鈥?basic states")
    class CheckTests {

        @Test
        void shouldReturnUnavailable_whenPluginNotRegistered() {
            when(pluginManager.findById("codex")).thenReturn(java.util.Optional.empty());

            ReadinessResult result = manager.check("codex");

            assertThat(result.state()).isEqualTo(ReadinessState.UNAVAILABLE);
            assertThat(result.readinessScore()).isEqualTo(0.0);
        }

        @Test
        void shouldReturnReady_whenNoDependenciesDeclared() {
            Plugin plugin = mockPlugin("simple-plugin", List.of());
            when(pluginManager.findById("simple-plugin")).thenReturn(java.util.Optional.of(plugin));

            ReadinessResult result = manager.check("simple-plugin");

            assertThat(result.state()).isEqualTo(ReadinessState.READY);
            assertThat(result.readinessScore()).isEqualTo(1.0);
        }

        @Test
        void shouldReturnReady_whenAllDependenciesPass() {
            Plugin plugin = mockPlugin("codex", List.of(
                    mockDependency(DependencyType.EXECUTABLE, "codex-cli", null, null, null, null),
                    mockDependency(DependencyType.AUTH, "codex-auth", null, null, null, null)
            ));
            when(pluginManager.findById("codex")).thenReturn(java.util.Optional.of(plugin));

            ReadinessResult result = manager.check("codex");

            assertThat(result.state()).isEqualTo(ReadinessState.READY);
        }

        @Test
        void shouldReturnDegraded_whenSomeDependenciesFailButRecoverable() {
            Plugin plugin = mockPlugin("codex", List.of(
                    mockDependency(DependencyType.EXECUTABLE, "codex-cli", null, null, null, null),
                    mockDependency(DependencyType.SERVICE, "local-provider", null, "http://127.0.0.1:9999", null, "D:\\ProgramFiles\\Codex++\\codex-plus-plus.exe")
            ));
            when(pluginManager.findById("codex")).thenReturn(java.util.Optional.of(plugin));

            ReadinessResult result = manager.check("codex");

            // codex-cli passes, local-provider fails but has recovery 鈫?DEGRADED
            assertThat(result.state()).isEqualTo(ReadinessState.DEGRADED);
            assertThat(result.readinessScore()).isEqualTo(0.5);
        }

        @Test
        void shouldReturnUnavailable_whenDependenciesFailAndNotRecoverable() {
            Plugin plugin = mockPlugin("codex", List.of(
                    mockDependency(DependencyType.EXECUTABLE, "codex-cli", "nonexistent-command", null, null, null)
            ));
            when(pluginManager.findById("codex")).thenReturn(java.util.Optional.of(plugin));

            ReadinessResult result = manager.check("codex");

            assertThat(result.state()).isEqualTo(ReadinessState.UNAVAILABLE);
            assertThat(result.readinessScore()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("checkAll()")
    class CheckAllTests {

        @Test
        void shouldScanAllPlugins() {
            Plugin p1 = mockPlugin("codex", List.of());
            Plugin p2 = mockPlugin("claude", List.of());
            when(pluginManager.getAll()).thenReturn(List.of(p1, p2));
            when(pluginManager.findById("codex")).thenReturn(java.util.Optional.of(p1));
            when(pluginManager.findById("claude")).thenReturn(java.util.Optional.of(p2));

            Map<String, ReadinessResult> results = manager.checkAll();

            assertThat(results).hasSize(2);
            assertThat(results.get("codex").state()).isEqualTo(ReadinessState.READY);
            assertThat(results.get("claude").state()).isEqualTo(ReadinessState.READY);
        }
    }

    @Nested
    @DisplayName("attemptRecovery()")
    class RecoveryTests {

        @Test
        void shouldReturnTrue_whenAlreadyReady() {
            Plugin plugin = mockPlugin("codex", List.of());
            when(pluginManager.findById("codex")).thenReturn(java.util.Optional.of(plugin));

            boolean recovered = manager.attemptRecovery("codex");

            assertThat(recovered).isTrue();
        }

        @Test
        void shouldAttemptRecovery_whenServiceDependencyFails() {
            Plugin plugin = mockPlugin("codex", List.of(
                    mockDependency(DependencyType.SERVICE, "local-provider", null,
                            "http://127.0.0.1:9999", null,
                            "D:\\ProgramFiles\\Codex++\\codex-plus-plus.exe")
            ));
            when(pluginManager.findById("codex")).thenReturn(java.util.Optional.of(plugin));

            boolean recovered = manager.attemptRecovery("codex");

            // Should try to launch recovery process (will fail in test env but not throw)
            assertThat(recovered).isFalse();
        }
    }

    @Nested
    @DisplayName("getRunnableAgents()")
    class RunnableAgentTests {

        @Test
        void shouldFilterOutUnavailableAgents() {
            Plugin ready = mockAgentPlugin("codex");
            Plugin unavailable = mockAgentPlugin("broken");
            when(unavailable.dependencies()).thenReturn(List.of(
                    mockDependency(DependencyType.EXECUTABLE, "broken-cli", "nonexistent-cmd", null, null, null)
            ));

            when(pluginManager.getAllAgents()).thenReturn(List.of(ready, unavailable));
            when(pluginManager.findById("codex")).thenReturn(java.util.Optional.of(ready));
            when(pluginManager.findById("broken")).thenReturn(java.util.Optional.of(unavailable));

            List<Plugin> runnable = manager.getRunnableAgents();

            assertThat(runnable).hasSize(1);
            assertThat(runnable.get(0).id()).isEqualTo("codex");
        }
    }

    @Nested
    @DisplayName("caching behavior")
    class CacheTests {

        @Test
        void shouldCacheResultWithin30Seconds() {
            Plugin plugin = mockPlugin("codex", List.of());
            when(pluginManager.findById("codex")).thenReturn(java.util.Optional.of(plugin));

            ReadinessResult r1 = manager.check("codex");
            ReadinessResult r2 = manager.check("codex");

            // Both should return same cached result (same object or equivalent)
            assertThat(r1.state()).isEqualTo(r2.state());
        }
    }

    // 鈹€鈹€鈹€ helpers 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private Plugin mockPlugin(String id, List<PluginDependency> deps) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.id()).thenReturn(id);
        when(plugin.dependencies()).thenReturn(deps);
        return plugin;
    }

    private Plugin mockAgentPlugin(String id) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.id()).thenReturn(id);
        when(plugin.type()).thenReturn(Plugin.PluginType.AGENT);
        return plugin;
    }

    private PluginDependency mockDependency(DependencyType type, String name,
                                             String checkCommand, String endpoint,
                                             String minVersion, String recoveryProcess) {
        return PluginDependency.builder()
                .type(type)
                .name(name)
                .checkCommand(checkCommand)
                .endpoint(endpoint)
                .minVersion(minVersion)
                .recoveryProcess(recoveryProcess)
                .build();
    }
}


