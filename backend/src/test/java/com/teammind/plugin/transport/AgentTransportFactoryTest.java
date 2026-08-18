package com.teammind.plugin.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.event.EventBus;
import com.teammind.plugin.adapter.CLIConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentTransportFactoryTest {

    private AgentTransportFactory factory;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus(new ObjectMapper());
        factory = new AgentTransportFactory(eventBus);
    }

    @Test
    @DisplayName("LEGACY transport → LegacyTransport")
    void legacyTransportCreatesLegacyInstance() {
        AgentConfig config = new AgentConfig(
            "codex", AgentConfig.TRANSPORT_LEGACY,
            "codex", List.of("exec", "<prompt>"),
            Map.of(), ".", 60, null, Map.of()
        );
        AgentTransport transport = factory.createTransport(config);
        assertInstanceOf(LegacyTransport.class, transport);
        assertEquals(AgentTransport.TransportType.LEGACY, transport.type());
    }

    @Test
    @DisplayName("null transport → fallback to LegacyTransport")
    void nullTransportFallsBackToLegacy() {
        AgentConfig config = new AgentConfig(
            "codex", null,
            "codex", List.of("exec", "<prompt>"),
            Map.of(), ".", 60, null, Map.of()
        );
        AgentTransport transport = factory.createTransport(config);
        assertInstanceOf(LegacyTransport.class, transport);
    }

    @Test
    @DisplayName("ACP transport → ACPTransport with default bridge")
    void acpTransportCreatesACPInstanceWithDefaultBridge() {
        AgentConfig config = new AgentConfig(
            "codex", AgentConfig.TRANSPORT_ACP,
            "node", List.of("exec", "<prompt>"),
            Map.of(), ".", 60, null, Map.of()
        );
        AgentTransport transport = factory.createTransport(config);
        assertInstanceOf(ACPTransport.class, transport);
        assertEquals(AgentTransport.TransportType.ACP, transport.type());
    }

    @Test
    @DisplayName("ACP transport uses explicit bridge override")
    void acpTransportUsesExplicitBridge() {
        AgentConfig config = new AgentConfig(
            "codex", AgentConfig.TRANSPORT_ACP,
            "node", List.of("exec", "<prompt>"),
            Map.of(), ".", 60, "my-custom-bridge", Map.of()
        );
        // Should not throw — ACPTransport accepts any bridge name
        AgentTransport transport = factory.createTransport(config);
        assertInstanceOf(ACPTransport.class, transport);
    }

    @Test
    @DisplayName("unknown transport type → falls back to LegacyTransport")
    void unknownTransportTypeFallsBackToLegacy() {
        AgentConfig config = new AgentConfig(
            "codex", "unknown-transport",
            "codex", List.of("exec", "<prompt>"),
            Map.of(), ".", 60, null, Map.of()
        );
        AgentTransport transport = factory.createTransport(config);
        assertInstanceOf(LegacyTransport.class, transport);
    }

    @Test
    @DisplayName("ACP transport capabilities are full")
    void acpTransportCapabilitiesAreFull() {
        AgentConfig config = new AgentConfig(
            "codex", AgentConfig.TRANSPORT_ACP,
            "node", List.of("exec", "<prompt>"),
            Map.of(), ".", 60, null, Map.of()
        );
        AgentTransport transport = factory.createTransport(config);
        var caps = transport.capabilities();
        assertTrue(caps.permission());
        assertTrue(caps.fileChange());
        assertTrue(caps.sessionResume());
        assertTrue(caps.plan());
        assertTrue(caps.subagent());
    }

    @Test
    @DisplayName("Legacy transport capabilities are minimal")
    void legacyTransportCapabilitiesAreMinimal() {
        AgentConfig config = new AgentConfig(
            "codex", AgentConfig.TRANSPORT_LEGACY,
            "codex", List.of("exec", "<prompt>"),
            Map.of(), ".", 60, null, Map.of()
        );
        AgentTransport transport = factory.createTransport(config);
        var caps = transport.capabilities();
        assertTrue(caps.prompt());
        assertTrue(caps.stream());
        assertTrue(caps.cancel());
        assertFalse(caps.permission());
        assertFalse(caps.fileChange());
    }

    @Test
    @DisplayName("ACP is superior to Legacy")
    void acpIsSuperiorToLegacy() {
        var acpCaps = TransportCapabilities.ACP_FULL;
        var legacyCaps = TransportCapabilities.LEGACY_MINIMAL;
        assertTrue(acpCaps.isSuperiorTo(legacyCaps));
        assertFalse(legacyCaps.isSuperiorTo(acpCaps));
    }

}
