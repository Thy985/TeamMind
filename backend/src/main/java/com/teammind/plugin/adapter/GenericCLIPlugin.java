package com.teammind.plugin.adapter;

import com.teammind.common.*;
import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import com.teammind.runtime.ProcessSupervisor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GenericCLIPlugin — 通用 CLI 适配器实现
 *
 * 从 CLIConfig YAML 加载配置，实现 CLIAdapter 接口。
 * 支持三种输出格式：text / ndjson / structured
 *
 * 设计原则：
 * 1. 不硬编码任何特定 CLI 逻辑 — 全部由 config() 描述
 * 2. 进程管理通过 ProcessSupervisor 接口 — 可被 Java/Rust Provider 替换
 * 3. 统一事件发射 — 所有 CLI 的输出都转换为 TeamMind 事件
 */
@Slf4j
public class GenericCLIPlugin implements CLIAdapter {

    private final CLIConfig config;
    private final EventBus eventBus;
    private final ProcessSupervisor processSupervisor;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile ProcessHandle currentProcess;

    public GenericCLIPlugin(CLIConfig config, EventBus eventBus, ProcessSupervisor processSupervisor) {
        this.config = config;
        this.eventBus = eventBus;
        this.processSupervisor = processSupervisor;
    }

    // ─── Plugin interface ─────────────────────────────────────

    @Override public String id() { return config.cliId(); }

    @Override public String description() {
        return config.cliId() + " CLI adapter (generic)";
    }

    @Override public String version() { return "1.0.0-generic"; }

    @Override
    public PluginMetadata metadata() {
        return PluginMetadata.empty(config.cliId());
    }

    // ─── CLIAdapter core ──────────────────────────────────────

    @Override
    public CLIConfig config() { return config; }

    protected EventBus getEventBus() { return eventBus; }

    @Override
    public void startProcess(String prompt, String workDir) throws IOException {
        if (isAlive()) {
            log.warn("[{}] Process already running, killing old one", config.cliId());
            kill();
        }

        List<String> cmd = buildCommand(prompt, workDir);
        log.info("[{}] Starting process: {}", config.cliId(), cmd);

        // 收集环境变量
        Map<String, String> env = new HashMap<>();
        config.env().forEach((k, v) -> env.put(k, expandEnvVars(v)));

        // 通过 ProcessSupervisor 启动（Java 或 Rust Provider）
        currentProcess = processSupervisor.spawn(String.join(" ", cmd), workDir, env);
        log.info("[{}] Process started, PID={}", config.cliId(), currentProcess.pid());
    }

    @Override
    public Optional<ProcessHandle> getProcessHandle() {
        return Optional.ofNullable(currentProcess)
                .filter(ProcessHandle::isAlive);
    }

    @Override
    public boolean isAlive() {
        return currentProcess != null && currentProcess.isAlive();
    }

    @Override
    public void kill() {
        if (currentProcess != null && currentProcess.isAlive()) {
            processSupervisor.cancel(currentProcess);
            log.info("[{}] Killed process PID={}", config.cliId(), currentProcess.pid());
        }
        cancelled.set(false);
    }

    // ─── Plugin invoke ────────────────────────────────────────

    @Override
    public PluginResult invoke(PluginContext context) {
        String prompt = resolvePrompt(context);
        String workDir = context.projectPath() != null ? context.projectPath() : ".";

        log.info("[{}] Invoking: task={}, path={}", config.cliId(), context.taskId(), workDir);

        eventBus.emit(TeamMindEvent.of(EventType.TASK_STARTED,
                context.taskId(), config.cliId(), "LEAD",
                Map.of("plugin_id", config.cliId(), "prompt_length", prompt.length())));

        try {
            startProcess(prompt, workDir);

            StringBuilder fullOutput = new StringBuilder();
            long timeoutMs = TimeUnit.MINUTES.toMillis(config.timeoutMinutes());
            long elapsed = 0;
            long pollInterval = 100;

            while (processSupervisor.isAlive(currentProcess) && elapsed < timeoutMs) {
                if (cancelled.get()) {
                    kill();
                    break;
                }
                String chunk = processSupervisor.readStdout(currentProcess, pollInterval);
                if (!chunk.isBlank()) {
                    fullOutput.append(chunk);
                    // 逐行解析并发射事件
                    for (String line : chunk.split("\n")) {
                        parseOutput(line, context.taskId(), null);
                    }
                }
                elapsed += pollInterval;
            }

            int exitCode = processSupervisor.waitExit(currentProcess, 1000);

            if (cancelled.get()) {
                eventBus.emit(TeamMindEvent.of(EventType.TASK_CANCELLED,
                        context.taskId(), config.cliId(), "LEAD"));
                return PluginResult.failure(config.cliId(), "Cancelled by user");
            } else if (exitCode != 0) {
                eventBus.emit(TeamMindEvent.of(EventType.TASK_FAILED,
                        context.taskId(), config.cliId(), "LEAD",
                        Map.of("exit_code", exitCode, "output", fullOutput.toString().substring(0, Math.min(500, fullOutput.length())))));
                return PluginResult.failure(config.cliId(),
                        "Exit code: " + exitCode + ", output: " + fullOutput.substring(0, Math.min(200, fullOutput.length())));
            } else {
                eventBus.emit(TeamMindEvent.of(EventType.TASK_COMPLETED,
                        context.taskId(), config.cliId(), "LEAD",
                        Map.of("output_length", fullOutput.length())));
                return PluginResult.success(config.cliId(), Map.of(
                        "output_summary", fullOutput.length() > 500
                                ? fullOutput.substring(0, 500) + "..."
                                : fullOutput.toString(),
                        "output_length", fullOutput.length()
                ));
            }
        } catch (Exception e) {
            log.error("[{}] Execution failed: {}", config.cliId(), e.getMessage(), e);
            eventBus.emit(TeamMindEvent.of(EventType.TASK_FAILED,
                    context.taskId(), config.cliId(), "LEAD",
                    Map.of("error", e.getMessage())));
            return PluginResult.failure(config.cliId(), e.getMessage());
        } finally {
            cancelled.set(false);
        }
    }

    // ─── Output parsing ───────────────────────────────────────

    @Override
    public void parseOutput(String line, String taskId, PluginChunkHandler handler) {
        if (line == null || line.isBlank()) return;

        switch (config.outputFormat()) {
            case NDJSON -> parseNDJSON(line, taskId, handler);
            case STRUCTURED -> parseStructured(line, taskId, handler);
            case TEXT -> parseText(line, taskId, handler);
            default -> { if (handler != null) handler.onChunk(line); }
        }
    }

    protected void parseNDJSON(String line, String taskId, PluginChunkHandler handler) {
        try {
            // 简单 JSON 解析（不依赖 Jackson 避免循环依赖）
            String type = extractField(line, "type");
            if (type == null || type.isBlank()) return;

            switch (type) {
                case "assistant" -> {
                    String content = extractField(line, "content");
                    if (content != null && !content.isBlank()) {
                        if (handler != null) handler.onChunk(content);
                        if (taskId != null) {
                            eventBus.emit(TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, config.cliId(), "LEAD",
                                    Map.of("content", content.substring(0, Math.min(200, content.length())))));
                        }
                    }
                }
                case "tool" -> {
                    String toolName = extractField(line, "tool_name");
                    if (toolName != null && taskId != null) {
                        eventBus.emit(TeamMindEvent.of(EventType.TOOL_CALLED, taskId, config.cliId(), "LEAD",
                                Map.of("tool", toolName, "input", extractField(line, "input"))));
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败不中断流程
            if (handler != null) handler.onChunk(line);
        }
    }

    protected void parseStructured(String line, String taskId, PluginChunkHandler handler) {
        // Structured 格式：整体 JSON，逐 key 处理
        try {
            String type = extractField(line, "event_type");
            if (type == null) return;
            if (handler != null) handler.onChunk(line);
        } catch (Exception e) {
            if (handler != null) handler.onChunk(line);
        }
    }

    protected void parseText(String line, String taskId, PluginChunkHandler handler) {
        // Text 格式：逐行都是有效内容
        if (handler != null) handler.onChunk(line);
        if (taskId != null && !line.isBlank()) {
            eventBus.emit(TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, config.cliId(), "LEAD",
                    Map.of("content", line.substring(0, Math.min(200, line.length())))));
        }
    }

    // ─── Internal helpers ─────────────────────────────────────

    private List<String> buildCommand(String prompt, String workDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add(config.command());
        for (String arg : config.args()) {
            if ("<prompt>".equals(arg)) {
                cmd.add(prompt);
            } else {
                cmd.add(arg);
            }
        }
        return WindowsCommandHelper.wrap(cmd);
    }

    private String resolvePrompt(PluginContext context) {
        Object prompt = context.taskConfig().get("prompt");
        if (prompt != null) return prompt.toString();
        return "Complete the following task: " + context.taskId();
    }

    private String expandEnvVars(String value) {
        if (!value.contains("${ENV:")) return value;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\$\\{ENV:(.+?)\\}");
        java.util.regex.Matcher m = p.matcher(value);
        java.lang.StringBuffer sb = new java.lang.StringBuffer();
        while (m.find()) {
            String envVar = m.group(1);
            String envVal = System.getenv(envVar);
            m.appendReplacement(sb, envVal != null ? envVal : m.group(0));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 简单 JSON 字段提取（不依赖外部库）
     * 从 JSON 行中提取 "key": "value" 或 "key": number
     */
    private String extractField(String json, String key) {
        try {
            int keyIdx = json.indexOf("\"" + key + "\"");
            if (keyIdx < 0) return null;
            int colonIdx = json.indexOf(':', keyIdx + key.length() + 2);
            if (colonIdx < 0) return null;
            int valueStart = json.indexOf('"', colonIdx + 1);
            if (valueStart < 0) {
                // 数值类型
                int spaceIdx = json.indexOf(' ', colonIdx + 1);
                if (spaceIdx < 0) spaceIdx = json.length();
                return json.substring(colonIdx + 1, spaceIdx).trim();
            }
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd < 0) return null;
            return json.substring(valueStart + 1, valueEnd);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void cancel() {
        log.info("[{}] Cancelling", config.cliId());
        cancelled.set(true);
        kill();
    }

    @Override
    public PluginHealth inspect() {
        try {
            CLIConfig.HealthCheck hc = config.healthCheck();
            if (hc.command() == null) {
                // 无健康检查配置，用 command --version 尝试
                List<String> hcCmd = WindowsCommandHelper.wrap(
                        List.of(config.command(), "--version"));
                Process p = new ProcessBuilder(hcCmd)
                        .redirectErrorStream(true).start();
                boolean finished = p.waitFor(30, TimeUnit.SECONDS);
                return finished && p.exitValue() == 0
                        ? PluginHealth.HEALTHY : PluginHealth.DEGRADED;
            }
            List<String> hcCmd = WindowsCommandHelper.wrap(
                    List.of(hc.command().split(" ")));
            Process p = new ProcessBuilder(hcCmd)
                    .redirectErrorStream(true).start();
            boolean finished = p.waitFor(30, TimeUnit.SECONDS);
            int exit = finished ? p.exitValue() : -1;
            return exit == hc.expectedExit() ? PluginHealth.HEALTHY : PluginHealth.DEGRADED;
        } catch (Exception e) {
            return PluginHealth.UNHEALTHY;
        }
    }
}
