package com.teammind.plugin.adapter;

import com.teammind.common.PluginDependency;
import com.teammind.plugin.Plugin;
import com.teammind.plugin.Plugin.PluginChunkHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * CLIAdapter — 抽象 CLI 适配器接口
 *
 * 所有 CLI Agent（Codex, Claude Code, Atomcode, ...）统一实现此接口。
 * TeamMind 不关心具体是哪个 CLI，只关心它是否符合 CLIAdapter 协议。
 *
 * 核心能力：
 * 1. 标准化输出解析（text / ndjson / structured）
 * 2. 统一的进程生命周期管理
 * 3. 可声明式配置（YAML → CLIConfig → GenericCLIPlugin）
 */
public interface CLIAdapter extends Plugin {

    /**
     * 返回 CLI 的配置描述（命令、参数、环境变量、超时等）
     */
    CLIConfig config();

    /**
     * 启动 CLI 进程执行指定 prompt
     */
    void startProcess(String prompt, String workDir) throws IOException;

    /**
     * 返回当前进程句柄（用于 RecoveryService 检查存活）
     */
    Optional<ProcessHandle> getProcessHandle();

    /**
     * 进程是否存活
     */
    boolean isAlive();

    /**
     * 强制终止进程
     */
    void kill();

    /**
     * 解析一行输出，转换为 TeamMind 事件
     *
     * @param line      一行 stdout/stderr 文本
     * @param taskId    关联任务 ID
     * @param handler   流式 chunk 处理器（可为 null）
     */
    void parseOutput(String line, String taskId, PluginChunkHandler handler);

    // ─── Plugin interface defaults (避免每个实现重写) ──────────

    @Override
    default PluginType type() { return PluginType.AGENT; }

    @Override
    default CompletableFuture<PluginResult> streamInvoke(
            PluginContext context, PluginChunkHandler handler) {
        throw new UnsupportedOperationException(
            "Use invoke() for synchronous execution in " + id());
    }

    @Override
    default List<PluginDependency> dependencies() {
        return config().dependencies();
    }

    @Override
    default boolean attemptRecovery() {
        return false; // GenericCLIPlugin 不自动恢复，需要用户介入
    }

    @Override
    default Map<String, Object> diagnose() {
        Map<String, Object> d = new java.util.HashMap<>();
        d.put("cli_id", config().cliId());
        d.put("command", config().command());
        d.put("is_alive", isAlive());
        if (isAlive()) {
            d.put("pid", getProcessHandle().map(ProcessHandle::pid).orElse(-1L));
        }
        return d;
    }
}
