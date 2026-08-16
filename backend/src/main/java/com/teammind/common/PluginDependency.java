package com.teammind.common;

import lombok.Builder;

/**
 * PluginDependency — 声明式依赖模型
 *
 * 每个 Plugin 声明自己依赖什么，Runtime 统一检查 + 恢复。
 * 不硬编码到 Plugin 实现里。
 */
@Builder
public record PluginDependency(
        DependencyType type,
        String name,
        String checkCommand,          // 检查命令（可 null，表示无需检查）
        String endpoint,              // HTTP endpoint（SERVICE 类型）
        String recoveryProcess,       // 恢复时启动的进程（如 codex-plus-plus.exe）
        String[] recoveryArgs,        // 恢复进程参数
        Integer healthCheckTimeoutMs, // HTTP health check 超时
        String minVersion             // 最小版本要求（EXECUTABLE 类型）
) {
    /**
     * 判断此依赖是否可自动恢复
     */
    public boolean hasAutoRecovery() {
        return recoveryProcess != null && !recoveryProcess.isBlank();
    }

    /**
     * 判断此依赖是否有明确的检查逻辑
     */
    public boolean hasCheck() {
        return checkCommand != null && !checkCommand.isBlank()
                || endpoint != null && !endpoint.isBlank();
    }
}
