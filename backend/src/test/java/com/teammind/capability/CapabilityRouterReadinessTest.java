package com.teammind.capability;

import com.teammind.common.ReadinessResult;
import com.teammind.common.ReadinessState;
import com.teammind.plugin.Plugin;
import com.teammind.runtime.PolicyEngine;
import com.teammind.runtime.ProjectPolicy;
import com.teammind.runtime.ReadinessManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CapabilityRouterReadinessTest {

    private ReadinessManager readinessManager;
    private PolicyEngine policyEngine;
    private CapabilityRouter router;

    @BeforeEach
    void setUp() {
        readinessManager = mock(ReadinessManager.class);
        policyEngine = mock(PolicyEngine.class);
        lenient().when(policyEngine.isAllowed(any(), any(), any(), any())).thenReturn(true);
        router = new CapabilityRouter(policyEngine, readinessManager, null, null);
    }

    @Nested
    @DisplayName("Readiness as gate")
    class ReadinessGateTests {

        @Test
        void shouldExcludeUnavailablePluginsFromCandidates() {
            Plugin ready = mockReadyPlugin("codex", List.of("implementation"));
            Plugin unavailable = mockUnavailablePlugin("broken", List.of("implementation"));

            when(readinessManager.check("codex")).thenReturn(ReadinessResult.ready("codex"));
            when(readinessManager.check("broken")).thenReturn(
                    ReadinessResult.unavailable("broken", "dependency failed", List.of("broken-cli")));

            RoutingDecision decision = router.route(
                    "implementation", List.of(ready, unavailable),
                    "proj-1", "add feature",
                    ProjectPolicy.builder().build(), com.teammind.common.ControlMode.SUPERVISED);

            // broken should be in rejectedCandidates, not selected
            assertThat(decision.getRejectedCandidates())
                    .extracting(RoutingDecision.RejectedCandidate::getPluginId)
                    .contains("broken");
            assertThat(decision.getSelectedPluginId()).isEqualTo("codex");
        }

        @Test
        void shouldReturnNeedsApproval_whenAllPluginsUnavailable() {
            Plugin onlyPlugin = mockUnavailablePlugin("broken", List.of("implementation"));
            when(readinessManager.check("broken")).thenReturn(
                    ReadinessResult.unavailable("broken", "no provider", List.of("provider-down")));

            RoutingDecision decision = router.route(
                    "implementation", List.of(onlyPlugin),
                    "proj-1", "add feature",
                    ProjectPolicy.builder().build(), com.teammind.common.ControlMode.SUPERVISED);

            assertThat(decision.isNeedsApproval()).isTrue();
        }

        @Test
        void shouldIncludeDegradedPluginsWithLowerScore() {
            Plugin ready = mockReadyPlugin("codex", List.of("implementation"));
            Plugin degraded = mockDegradedPlugin("slow-codex", List.of("implementation"));

            when(readinessManager.check("codex")).thenReturn(ReadinessResult.ready("codex"));
            when(readinessManager.check("slow-codex")).thenReturn(
                    ReadinessResult.degraded("slow-codex", "high latency",
                            java.util.Map.of("latency_ms", 5000)));

            RoutingDecision decision = router.route(
                    "implementation", List.of(ready, degraded),
                    "proj-1", "add feature",
                    ProjectPolicy.builder().build(), com.teammind.common.ControlMode.SUPERVISED);

            // Both should be candidates, ready plugin should win on availability score
            assertThat(decision.getSelectedPluginId()).isEqualTo("codex");
        }
    }

    private Plugin mockReadyPlugin(String id, List<String> capabilities) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.id()).thenReturn(id);
        com.teammind.plugin.Plugin.PluginMetadata meta = new com.teammind.plugin.Plugin.PluginMetadata(
                id, id, "1.0", "", capabilities, List.of(), List.of(), List.of(),
                30000L, 0.9, 0.03);
        when(plugin.metadata()).thenReturn(meta);
        return plugin;
    }

    private Plugin mockUnavailablePlugin(String id, List<String> capabilities) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.id()).thenReturn(id);
        com.teammind.plugin.Plugin.PluginMetadata meta = new com.teammind.plugin.Plugin.PluginMetadata(
                id, id, "1.0", "", capabilities, List.of(), List.of(), List.of(),
                null, null, null);
        when(plugin.metadata()).thenReturn(meta);
        return plugin;
    }

    private Plugin mockDegradedPlugin(String id, List<String> capabilities) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.id()).thenReturn(id);
        com.teammind.plugin.Plugin.PluginMetadata meta = new com.teammind.plugin.Plugin.PluginMetadata(
                id, id, "1.0", "", capabilities, List.of(), List.of(), List.of(),
                80000L, 0.7, 0.05);
        when(plugin.metadata()).thenReturn(meta);
        return plugin;
    }
}



