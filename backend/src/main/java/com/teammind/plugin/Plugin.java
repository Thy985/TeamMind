package com.teammind.plugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Plugin 统一接口 — Cordis-like 插件系统核心
 *
 * 核心原则：
 *   ❌ 错误：if (pluginId == "claude-code") { ... }
 *   ✅ 正确：plugin.invoke(context) → result
 *
 * 所有 Agent / Tool / Verifier / Memory 都是 Plugin 的子类。
 * 核心系统只认识 Plugin 接口，不认识任何具体实现。
 */
public interface Plugin {
    /**
     * 插件唯一 ID（如 "claude-code", "codex", "git-verifier"）
     */
    String id();

    /**
     * 插件类型
     */
    PluginType type();

    /**
     * 插件描述
     */
    String description();

    /**
     * 插件版本
     */
    String version();

    /**
     * 插件元数据（能力声明、哲学、成本等）
     */
    PluginMetadata metadata();

    /**
     * 执行插件逻辑
     *
     * @param context 执行上下文（包含 projectPath, task, config 等）
     * @return 执行结果（可能阻塞，非流式）
     */
    PluginResult invoke(PluginContext context);

    /**
     * 流式执行（可选，用于长耗时任务实时输出）
     */
    default CompletableFuture<PluginResult> streamInvoke(
            PluginContext context,
            PluginChunkHandler chunkHandler) {
        throw new UnsupportedOperationException("Streaming not supported for " + id());
    }

    /**
     * 取消执行（可选）
     */
    default void cancel() {}

    /**
     * 健康检查
     */
    PluginHealth inspect();

    /**
     * 声明此 Plugin 依赖的运行时资源（CLI、服务、认证、环境变量等）。
     * 由 Runtime 统一执行检查和恢复，不硬编码到 invoke() 里。
     */
    default List<com.teammind.common.PluginDependency> dependencies() {
        return List.of();
    }

    /**
     * 尝试自动恢复不可用的依赖。
     * 返回 true 表示恢复成功，false 表示需要用户介入。
     */
    default boolean attemptRecovery() {
        return false;
    }

    /**
     * 返回详细诊断信息（谁调用 inspect() 不够详细时用这个）
     */
    default Map<String, Object> diagnose() {
        return Map.of();
    }

    /**
     * 生命周期钩子 — 插件加载时调用
     */
    default void onLoad() {}

    /**
     * 生命周期钩子 — 插件卸载时调用
     */
    default void onUnload() {}

    /**
     * 插件类型枚举
     */
    enum PluginType {
        /** AI Agent（Claude Code, Codex 等 CLI） */
        AGENT,
        /** 工具（Terminal, Browser, Docker 等） */
        TOOL,
        /** 验证器（GitVerifier, TestRunnerVerifier 等） */
        VERIFIER,
        /** 记忆组件（ProjectMemory, TaskMemory 等） */
        MEMORY,
        /** 集成组件（GitHub, GitLab, Linear 等） */
        INTEGRATION
    }

    /**
     * 执行上下文
     */
    record PluginContext(
            String projectId,
            String taskId,
            Map<String, Object> taskConfig,
            String projectPath,
            Map<String, Object> sharedState,
            List<String> requiredCapabilities
    ) {
        public PluginContext withTask(String taskId) {
            return new PluginContext(projectId, taskId, taskConfig, projectPath, sharedState, requiredCapabilities);
        }
    }

    /**
     * 执行结果
     */
    record PluginResult(
            boolean success,
            String pluginId,
            Object data,
            String error,
            Map<String, Object> metadata
    ) {
        public static PluginResult success(String pluginId, Object data) {
            return new PluginResult(true, pluginId, data, null, Map.of());
        }

        public static PluginResult failure(String pluginId, String error) {
            return new PluginResult(false, pluginId, null, error, Map.of());
        }
    }

    /**
     * 流式 chunk 处理器
     */
    @FunctionalInterface
    interface PluginChunkHandler {
        void onChunk(Object chunk);
    }

    /**
     * 插件元数据
     */
    record PluginMetadata(
            String id,
            String name,
            String version,
            String description,
            List<String> capabilities,       // 声明的能力列表
            List<String> philosophies,       // 设计哲学关键词
            List<String> preferredRoles,     // 适合的角色
            List<String> weakRoles,          // 不适合的角色
            Long avgLatencyMs,               // 平均延迟 ms
            Double reliabilityScore,         // 可靠性分数 0-1
            Double costPerInvocation         // 每次调用成本（美元）
    ) {
        public static PluginMetadata empty(String id) {
            return new PluginMetadata(id, id, "0.0.1", "",
                    List.of(), List.of(), List.of(), List.of(),
                    null, null, null);
        }
    }

    /**
     * 插件健康状态
     */
    enum PluginHealth {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        DOWN
    }
}
