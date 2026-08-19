package com.teammind.plugin.transport;

/**
 * AgentTransport — Agent 接入抽象层
 *
 * 统一 Plugin 调用不同 Agent 的方式：
 * - LegacyTransport: ProcessBuilder + stdout parsing（现有路径）
 * - ACPTransport: ACP protocol via stdio JSON-RPC（新路径）
 *
 * 设计原则：
 * 1. Transport 是调用方式，不是 Agent 身份
 * 2. 同一个 AgentPlugin 可以配置不同的 Transport
 * 3. ACPTransport 通过 ACP SDK/bridge 与 Agent 通信
 * 4. LegacyTransport 保持现有行为不变
 * 5. Provider 内部复杂度对 TeamMind Runtime 透明
 */
public interface AgentTransport {

    /** 传输类型标识 */
    TransportType type();

    /** 初始化并启动 Agent session */
    AgentSession start(AgentConfig config);

    /** 关闭 transport */
    void close();

    /** 传输能力声明 */
    TransportCapabilities capabilities();

    /**
     * 检查 Provider 就绪状态。
     * 对于启动慢的 Provider（如 QwenPaw workspace >180s），
     * 返回 STARTING 而不是阻塞等待。
     *
     * @return ProviderStatus 当前状态快照
     */
    default ProviderStatus readiness() {
        return ProviderStatus.discovered("unknown");
    }

    /**
     * Pre-warm Provider：预启动 session，避免首次 prompt 时的冷启动延迟。
     * 对于 QwenPaw 这类 workspace 启动慢的 Provider，
     * 应在 TeamMind startup 时调用此方法。
     *
     * @param config Provider 配置
     * @return 预热耗时（ms），-1 表示不支持预热
     */
    default long prewarm(AgentConfig config) {
        return -1;
    }

    enum TransportType {
        /** 传统 CLI 进程路径：ProcessBuilder + stdout parsing */
        LEGACY,
        /** ACP 协议路径：stdio JSON-RPC via official ACP bridge */
        ACP
    }
}
