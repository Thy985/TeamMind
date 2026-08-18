package com.teammind.runtime;

import java.io.IOException;
import java.util.Map;

/**
 * ProcessSupervisor — 子进程生命周期管理的抽象接口
 *
 * 这是 Java Provider 和 Rust Provider 之间的 Contract 边界。
 * Java 侧有默认实现（JavaProcessSupervisor），Rust 侧通过 Tauri IPC 提供等价能力。
 *
 * 根据 Runtime Contract v1 §2.4：
 *   spawn(command, workDir, env) → ProcessHandle
 *   isAlive(pid) → bool
 *   readStdout(pid, timeoutMs) → string
 *   cancel(pid) → void          // SIGTERM → 等待 → SIGKILL
 *   waitExit(pid, timeoutMs) → ExitStatus
 *
 * 所有 CLI Plugin 应通过此接口操作进程，而非直接使用 ProcessBuilder / ProcessHandle。
 */
public interface ProcessSupervisor {

    /**
     * 在指定工作目录下以给定环境变量启动进程
     *
     * @param command 完整命令（已分词的 argv，不含 shell）
     * @param workDir 工作目录路径
     * @param env     追加/覆盖的环境变量
     * @return 进程句柄，可用于后续操作
     * @throws IOException 进程启动失败
     */
    ProcessHandle spawn(String command, String workDir, Map<String, String> env) throws IOException;

    /**
     * 检查进程是否存活
     */
    boolean isAlive(ProcessHandle pid);

    /**
     * 优雅取消进程：SIGTERM → 等待 → SIGKILL
     *
     * @param pid 目标进程句柄
     */
    void cancel(ProcessHandle pid);

    /**
     * 阻塞等待进程退出，带超时
     *
     * @return 退出码；超时返回 -1
     */
    int waitExit(ProcessHandle pid, long timeoutMs) throws InterruptedException;

    /**
     * 非阻塞读取 stdout 缓冲区（消费式读取）
     *
     * @param timeoutMs 等待新数据的超时毫秒数；0 = 立即返回已有数据
     * @return 已积累的 stdout 文本；无数据返回空字符串
     */
    String readStdout(ProcessHandle pid, long timeoutMs) throws InterruptedException;

    /**
     * 非阻塞读取 stderr 缓冲区（消费式读取）
     *
     * @param timeoutMs 等待新数据的超时毫秒数；0 = 立即返回已有数据
     * @return 已积累的 stderr 文本；无数据返回空字符串
     */
    String readStderr(ProcessHandle pid, long timeoutMs) throws InterruptedException;
}
