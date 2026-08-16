package com.teammind.plugin.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CLIProcessTracker — 统一管理所有 CLI 进程生命周期
 *
 * 职责：
 * 1. 注册/注销 CLI 进程
 * 2. 批量检查存活状态
 * 3. 优雅关闭（服务停止时 kill 所有进程）
 *
 * 供 RecoveryService 和 ShutdownHook 使用。
 */
@Slf4j
@Component
public class CLIProcessTracker {

    private final ConcurrentHashMap<String, ProcessHandle> processMap = new ConcurrentHashMap<>();

    /**
     * 注册一个 CLI 进程
     */
    public void register(String pluginId, ProcessHandle handle) {
        if (handle != null && handle.isAlive()) {
            processMap.put(pluginId, handle);
            log.debug("Registered CLI process: {} PID={}", pluginId, handle.pid());
        }
    }

    /**
     * 注销一个 CLI 进程
     */
    public void unregister(String pluginId) {
        processMap.remove(pluginId);
        log.debug("Unregistered CLI process: {}", pluginId);
    }

    /**
     * 检查指定 CLI 进程是否存活
     */
    public boolean isAlive(String pluginId) {
        ProcessHandle handle = processMap.get(pluginId);
        return handle != null && handle.isAlive();
    }

    /**
     * 获取指定 CLI 的进程句柄
     */
    public Optional<ProcessHandle> getProcess(String pluginId) {
        return Optional.ofNullable(processMap.get(pluginId));
    }

    /**
     * 获取所有存活进程
     */
    public ConcurrentHashMap<String, ProcessHandle> getAllAlive() {
        ConcurrentHashMap<String, ProcessHandle> alive = new ConcurrentHashMap<>();
        processMap.forEach((id, handle) -> {
            if (handle.isAlive()) alive.put(id, handle);
        });
        return alive;
    }

    /**
     * 终止所有 CLI 进程（用于优雅关闭）
     */
    public void killAll() {
        ConcurrentHashMap<String, ProcessHandle> all = new ConcurrentHashMap<>(processMap);
        all.forEach((id, handle) -> {
            if (handle.isAlive()) {
                handle.destroyForcibly();
                log.info("Killed CLI process: {} PID={}", id, handle.pid());
            }
        });
        processMap.clear();
        log.info("All CLI processes terminated");
    }

    /**
     * 进程数量
     */
    public int size() {
        return (int) processMap.values().stream().filter(ProcessHandle::isAlive).count();
    }
}
