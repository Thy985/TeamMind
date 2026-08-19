package com.teammind.plugin.transport;

/**
 * ProviderState — Agent Provider 就绪状态
 *
 * TeamMind 不关心 Provider 内部复杂度，只关心它是否可用。
 *
 * 状态转移：
 *   DISCOVERED → CONFIGURED → STARTING → READY
 *                                   ↓        ↓
 *                               DEGRADED  UNAVAILABLE
 *                                   ↑        ↓
 *                              FAILED ← ──────┘
 *
 * Provider 内部启动慢（如 QwenPaw workspace >180s）属于 STARTING，
 * 不阻塞其他 Provider 执行任务。
 */
public enum ProviderState {
    /** Provider 已被发现，但尚未配置 */
    DISCOVERED,

    /** Provider 已配置，等待启动 */
    CONFIGURED,

    /** Provider 正在启动（workspace 初始化、服务加载等） */
    STARTING,

    /** Provider 已就绪，可接受任务 */
    READY,

    /** Provider 部分功能降级（如某些 tool unavailable） */
    DEGRADED,

    /** Provider 不可用（启动失败、崩溃等） */
    UNAVAILABLE,

    /** Provider 已停止 */
    STOPPED
}
