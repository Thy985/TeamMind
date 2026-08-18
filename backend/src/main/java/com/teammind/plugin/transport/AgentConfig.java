package com.teammind.plugin.transport;

import java.util.Map;

/**
 * AgentConfig — Agent 配置，用于创建 Transport 和 Session
 */
public record AgentConfig(
    String agentId,              // Agent ID（如 "codex", "claude-code"）
    String transportType,        // "legacy" or "acp"
    String command,              // CLI 命令
    java.util.List<String> args, // 命令参数模板
    Map<String, String> env,     // 环境变量
    String workingDir,           // 工作目录
    int timeoutMinutes,          // 超时
    String acpBridge,            // ACP bridge 名称（如 "codex-acp", "claude-agent-acp"）
    Map<String, Object> acpConfig // ACP 特有配置（sandbox、permission mode 等）
) {
    public static final String TRANSPORT_LEGACY = "legacy";
    public static final String TRANSPORT_ACP = "acp";

    /** 从 YAML config 构建（兼容现有 CLIConfig） */
    public static AgentConfig fromCLIConfig(com.teammind.plugin.adapter.CLIConfig cliConfig) {
        return new AgentConfig(
            cliConfig.cliId(),
            TRANSPORT_LEGACY,  // 默认 legacy
            cliConfig.command(),
            cliConfig.args(),
            cliConfig.env(),
            cliConfig.workingDir(),
            cliConfig.timeoutMinutes(),
            null,              // acpBridge
            Map.of()           // acpConfig
        );
    }

    /** 构建 ACP transport 的配置 */
    public AgentConfig withTransportACP(String acpBridge, Map<String, Object> acpConfig) {
        return new AgentConfig(
            this.agentId(),
            TRANSPORT_ACP,
            this.command(),
            this.args(),
            this.env(),
            this.workingDir(),
            this.timeoutMinutes(),
            acpBridge != null ? acpBridge : "codex-acp",
            acpConfig != null ? acpConfig : Map.of()
        );
    }
}
