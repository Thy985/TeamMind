package com.teammind.plugin.transport;

import com.teammind.event.EventBus;
import com.teammind.plugin.adapter.CLIConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentTransportFactory — 根据 AgentConfig 创建对应的 AgentTransport
 *
 * 选择逻辑：
 * - transport = "acp"     → ACPTransport（结构化 ACP 协议）
 * - transport = "legacy"  → LegacyTransport（现有 ProcessBuilder 路径）
 * - 未指定/null          → 默认 legacy（向后兼容）
 *
 * 使用示例（YAML）：
 *   agent: codex
 *   transport: acp          ← 使用 ACPTransport
 *
 *   agent: claude-code
 *   transport: legacy       ← 使用 LegacyTransport（默认）
 */
@Slf4j
public class AgentTransportFactory {

    private final EventBus eventBus;

    public AgentTransportFactory(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 根据 AgentConfig 创建 Transport
     */
    public AgentTransport createTransport(AgentConfig config) {
        String tt = config.transportType();
        if (AgentConfig.TRANSPORT_ACP.equals(tt)) {
            String bridge = config.acpBridge() != null
                    ? config.acpBridge()
                    : defaultBridgeForAgent(config.agentId());
            log.info("Creating ACPTransport for agent={} bridge={}", config.agentId(), bridge);
            return new ACPTransport(eventBus, bridge);
        }
        // null or TRANSPORT_LEGACY → fallback to legacy
        CLIConfig cliConfig = buildCLIConfig(config);
        log.info("Creating LegacyTransport for agent={} transport={}", config.agentId(), tt);
        return new LegacyTransport(cliConfig, eventBus);
    }

    /**
     * 为已知 Agent 提供默认 ACP bridge 名称
     */
    private String defaultBridgeForAgent(String agentId) {
        return switch (agentId) {
            case "codex" -> "codex-acp";
            case "claude-code" -> "claude-agent-acp";
            default -> agentId + "-acp";
        };
    }

    /**
     * 将 AgentConfig 转换为 CLIConfig（用于 LegacyTransport）
     */
    private CLIConfig buildCLIConfig(AgentConfig config) {
        CLIConfig.OutputFormat format = CLIConfig.OutputFormat.TEXT;
        if (config.args().stream().anyMatch(a -> a.contains("json") || a.contains("ndjson"))) {
            format = CLIConfig.OutputFormat.NDJSON;
        }

        return new CLIConfig(
                config.agentId(),
                config.command(),
                config.args(),
                config.env(),
                config.workingDir(),
                config.timeoutMinutes(),
                format,
                CLIConfig.HealthCheck.NONE,
                java.util.List.of()
        );
    }
}
