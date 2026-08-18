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

    enum TransportType {
        /** 传统 CLI 进程路径：ProcessBuilder + stdout parsing */
        LEGACY,
        /** ACP 协议路径：stdio JSON-RPC via official ACP bridge */
        ACP
    }
}
