package com.teammind.plugin.transport;

import java.time.Instant;
import java.util.Map;

/**
 * AgentSession — 单次 Agent 执行会话
 *
 * 统一 prompt/cancel/event stream 接口，
 * 无论底层是 ACP 还是 Legacy 进程。
 */
public interface AgentSession {

    /**
     * 发送 prompt，返回 session ID
     * @param prompt 用户任务描述
     * @param context 执行上下文（project path, agent config, etc.）
     * @return session ID
     */
    String submitPrompt(String prompt, Map<String, Object> context);

    /** 取消当前执行 */
    void cancel();

    /** 关闭 session */
    void close();

    /** 是否活跃 */
    boolean isAlive();

    /** 获取 session 元数据 */
    SessionMetadata metadata();

    /** Session 元数据记录 */
    record SessionMetadata(
        String sessionId,
        String agentId,
        AgentTransport.TransportType transportType,
        Instant startedAt
    ) {}
}
