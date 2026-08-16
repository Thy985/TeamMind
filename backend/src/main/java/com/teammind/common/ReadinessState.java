package com.teammind.common;

/**
 * Agent ReadinessState — 插件可用性七态机
 *
 * DISCOVERED → INSTALLED → CONFIGURED → READY → DEGRADED → RECOVERING → BLOCKED / UNAVAILABLE
 *
 * Readiness 是 Capability Routing 的前置开关：
 *   UNAVAILABLE → 不进候选集
 *   DEGRADED    → 进候选集但降权
 *   READY       → 正常参与评分
 */
public enum ReadinessState {
    /** Plugin 被扫描到，尚未加载 */
    DISCOVERED,

    /** 二进制/依赖存在，但未验证配置 */
    INSTALLED,

    /** 配置文件有效，但依赖服务未就绪 */
    CONFIGURED,

    /** 所有依赖可用，可被调度 */
    READY,

    /** 部分能力降级（如 provider 响应慢） */
    DEGRADED,

    /** 系统正在尝试自动恢复 */
    RECOVERING,

    /** 无法自动恢复，需要用户介入 */
    BLOCKED,

    /** 完全不可用（二进制缺失、致命错误） */
    UNAVAILABLE
}
