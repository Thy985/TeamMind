package com.teammind.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JavaProcessSupervisor — ProcessSupervisor 的 Java 原生实现
 *
 * 通过 ProcessBuilder 启动进程，使用独立线程持续读取 stdout/stderr
 * 并缓存到 ConcurrentHashMap 中，供 readStdout / readStderr 非阻塞消费。
 *
 * 线程安全：每个进程对应一个独立的 ReaderTask，所有读写操作通过线程安全结构同步。
 */
@Slf4j
@Component
public class JavaProcessSupervisor implements ProcessSupervisor {

    /**
     * 每进程的输出缓存：ProcessHandle.pid() → 缓存条目
     */
    private final ConcurrentHashMap<Long, ProcessBuffer> buffers = new ConcurrentHashMap<>();

    /**
     * PID → 底层 Process 引用（用于 waitFor / exitValue）
     */
    private final ConcurrentHashMap<Long, Process> processMap = new ConcurrentHashMap<>();

    @Value("${teammind.runtime.process.provider:java}")
    private String provider;

    @Override
    public ProcessHandle spawn(String command, String workDir, Map<String, String> env) throws IOException {
        // command 是空格分隔的原始字符串，按空白分词（不解析引号，调用方负责）
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0) {
            throw new IOException("Empty command");
        }

        ProcessBuilder pb = new ProcessBuilder(parts);
        pb.directory(new File(workDir));
        pb.redirectErrorStream(true); // 合并 stderr 到 stdout，简化消费
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);

        // 注入环境变量
        Map<String, String> currentEnv = pb.environment();
        if (env != null) {
            env.forEach(currentEnv::put);
        }

        Process process = pb.start();
        long pid = process.pid();

        ProcessBuffer buffer = new ProcessBuffer(process);
        buffers.put(pid, buffer);
        processMap.put(pid, process);

        // 启动后台读取线程
        Thread reader = new Thread(buffer::consume, "ps-supervisor-stdout-" + pid);
        reader.setDaemon(true);
        reader.start();

        log.info("[ProcessSupervisor] Spawned PID={} cmd={} dir={}", pid, command, workDir);
        return process.toHandle();
    }

    @Override
    public boolean isAlive(ProcessHandle pid) {
        return pid != null && pid.isAlive();
    }

    @Override
    public void cancel(ProcessHandle pid) {
        if (pid == null) return;
        long p = pid.pid();

        if (pid.isAlive()) {
            log.info("[ProcessSupervisor] Cancelling PID={}", p);
            // 1. SIGTERM（优雅终止）
            pid.destroy();

            // 2. 等待最多 5 秒
            try {
                Process proc = processMap.get(p);
                if (proc != null && !proc.waitFor(5, TimeUnit.SECONDS)) {
                    log.warn("[ProcessSupervisor] PID={} did not exit after SIGTERM, forcing", p);
                    // 3. SIGKILL（强制终止）
                    pid.destroyForcibly();
                    if (proc != null) {
                        proc.waitFor(3, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pid.destroyForcibly();
            }
        }

        buffers.remove(p);
        processMap.remove(p);
        log.info("[ProcessSupervisor] PID={} terminated", p);
    }

    @Override
    public int waitExit(ProcessHandle pid, long timeoutMs) throws InterruptedException {
        if (pid == null) return -1;
        Process proc = processMap.get(pid.pid());
        if (proc == null) return -1;
        boolean finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        return finished ? proc.exitValue() : -1;
    }

    @Override
    public String readStdout(ProcessHandle pid, long timeoutMs) throws InterruptedException {
        if (pid == null) return "";
        ProcessBuffer buf = buffers.get(pid.pid());
        if (buf == null) return "";
        return buf.drainStdout(timeoutMs);
    }

    @Override
    public String readStderr(ProcessHandle pid, long timeoutMs) throws InterruptedException {
        // stderr 已合并到 stdout（redirectErrorStream(true)），返回空
        return "";
    }

    /**
     * 单个进程的输出缓冲区和读取任务
     */
    private static class ProcessBuffer {
        private final Process process;
        private final StringBuilder stdout = new StringBuilder();
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final Object lock = new Object();

        ProcessBuffer(Process process) {
            this.process = process;
        }

        /**
         * 在独立线程中持续读取 stdout，追加到缓冲区
         */
        void consume() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (lock) {
                        stdout.append(line).append('\n');
                    }
                }
            } catch (IOException e) {
                log.debug("[ProcessBuffer] Stream closed for PID={}: {}", process.pid(), e.getMessage());
            } finally {
                done.set(true);
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        }

        /**
         * 非阻塞 draining：最多等待 timeoutMs 毫秒，返回自上次调用以来的新内容
         */
        String drainStdout(long timeoutMs) throws InterruptedException {
            synchronized (lock) {
                if (stdout.length() == 0 && !done.get()) {
                    // 无数据且进程仍在运行，等待新数据
                    lock.wait(timeoutMs);
                }
                String snapshot = stdout.toString();
                stdout.setLength(0);
                return snapshot;
            }
        }
    }
}
