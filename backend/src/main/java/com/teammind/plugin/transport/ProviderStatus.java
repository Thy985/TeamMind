package com.teammind.plugin.transport;

import java.time.Instant;

/**
 * ProviderStatus — Provider 完整状态快照
 *
 * 包含状态、能力、启动耗时等元数据，
 * 供 Router 决策和 UI 展示。
 */
public record ProviderStatus(
    /** Provider ID（如 "qwenpaw", "claude-code", "codex"） */
    String providerId,

    /** 当前状态 */
    ProviderState state,

    /** 状态变更时间 */
    Instant stateChangedAt,

    /** 启动耗时（ms），仅 STARTING/READY 时有意义 */
    long startupMs,

    /** 最后错误信息 */
    String lastError,

    /** 支持的能力列表 */
    String[] capabilities
) {
    public static ProviderStatus discovered(String id) {
        return new ProviderStatus(id, ProviderState.DISCOVERED, Instant.now(), 0, null, new String[0]);
    }

    public static ProviderStatus starting(String id) {
        return new ProviderStatus(id, ProviderState.STARTING, Instant.now(), 0, null, new String[0]);
    }

    public static ProviderStatus ready(String id, long startupMs, String... caps) {
        return new ProviderStatus(id, ProviderState.READY, Instant.now(), startupMs, null, caps);
    }

    public static ProviderStatus degraded(String id, String error, String... caps) {
        return new ProviderStatus(id, ProviderState.DEGRADED, Instant.now(), 0, error, caps);
    }

    public static ProviderStatus unavailable(String id, String error) {
        return new ProviderStatus(id, ProviderState.UNAVAILABLE, Instant.now(), 0, error, new String[0]);
    }

    /** 是否可接受新任务 */
    public boolean isRunnable() {
        return state == ProviderState.READY || state == ProviderState.DEGRADED;
    }

    /** 是否正在启动中 */
    public boolean isStarting() {
        return state == ProviderState.STARTING;
    }
}
