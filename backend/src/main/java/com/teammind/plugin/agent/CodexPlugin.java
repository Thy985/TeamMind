package com.teammind.plugin.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.common.DependencyType;
import com.teammind.common.EventType;
import com.teammind.common.PluginDependency;
import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import com.teammind.plugin.Plugin;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Codex CLI Plugin — 真实 ProcessBuilder 集成
 *
 * 调用方式：
 *   codex <prompt>
 *
 * 输出格式：Codex 输出到 stdout，以 ">>> " 前缀标识步骤，
 * 其他行为按流式 chunk 处理。
 */
@Slf4j
public class CodexPlugin implements Plugin {

    private static final String ID = "codex";
    private static final String VERSION = "0.144.5";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventBus eventBus;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Process currentProcess;

    public CodexPlugin(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    // ─── Plugin interface ──────────────────────────────────────

    @Override public String id() { return ID; }

    @Override public PluginType type() { return PluginType.AGENT; }

    @Override public String description() {
        return "执行导向的 AI 编程助手，强调迭代构建和测试闭环";
    }

    @Override public String version() { return VERSION; }

    @Override
    public PluginMetadata metadata() {
        return new PluginMetadata(
                ID, "Codex CLI", VERSION, description(),
                List.of("implementation", "test_generation", "refactoring", "api_design"),
                List.of("execution", "iterative_build", "test_driven", "rapid_iteration"),
                List.of("implementation", "test_generation", "refactoring"),
                List.of("security_review", "architecture_review"),
                30000L, 0.90, 0.03
        );
    }

    @Override
    public PluginResult invoke(PluginContext context) {
        String prompt = resolvePrompt(context);
        String projectPath = context.projectPath() != null ? context.projectPath() : ".";

        log.info("[{}] Invoking Codex: task={}, path={}", ID, context.taskId(), projectPath);

        eventBus.emit(TeamMindEvent.of(EventType.TASK_STARTED,
                context.taskId(), ID, "LEAD", Map.of("plugin_id", ID, "prompt_length", prompt.length())));

        try {
            String output = runCodexCommand(prompt, projectPath);

            log.info("[{}] Codex completed for task {}", ID, context.taskId());
            eventBus.emit(TeamMindEvent.of(EventType.TASK_COMPLETED,
                    context.taskId(), ID, "LEAD",
                    Map.of("exit_code", 0, "output_length", output.length())));

            Map<String, Object> result = new HashMap<>();
            result.put("plugin_id", ID);
            result.put("task_id", context.taskId());
            result.put("output_summary", output.length() > 500 ? output.substring(0, 500) + "..." : output);
            result.put("exit_code", 0);
            return PluginResult.success(ID, result);
        } catch (Exception e) {
            log.error("[{}] Codex failed: {}", ID, e.getMessage(), e);
            eventBus.emit(TeamMindEvent.of(EventType.TASK_FAILED,
                    context.taskId(), ID, "LEAD", Map.of("error", e.getMessage())));
            return PluginResult.failure(ID, e.getMessage());
        }
    }

    @Override
    public CompletableFuture<PluginResult> streamInvoke(PluginContext context, PluginChunkHandler handler) {
        String prompt = resolvePrompt(context);
        String projectPath = context.projectPath() != null ? context.projectPath() : ".";
        CompletableFuture<PluginResult> future = new CompletableFuture<>();

        eventBus.emit(TeamMindEvent.of(EventType.TASK_STARTED,
                context.taskId(), ID, "LEAD", Map.of("streaming", true)));

        Thread t = new Thread(() -> {
            try {
                StringBuilder fullOutput = new StringBuilder();
                Process proc = buildProcess(prompt, projectPath);
                currentProcess = proc;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (cancelled.get()) {
                            proc.destroy();
                            break;
                        }
                        fullOutput.append(line).append("\n");
                        parseAndEmitLine(line, context.taskId(), handler);
                    }
                }

                boolean finished = proc.waitFor(60, TimeUnit.MINUTES);
                int exitCode = finished ? proc.exitValue() : -1;
                if (cancelled.get()) {
                    eventBus.emit(TeamMindEvent.of(EventType.TASK_CANCELLED, context.taskId(), ID, "LEAD"));
                    future.complete(PluginResult.failure(ID, "Cancelled"));
                } else if (exitCode != 0) {
                    eventBus.emit(TeamMindEvent.of(EventType.TASK_FAILED, context.taskId(), ID, "LEAD",
                            Map.of("exit_code", exitCode)));
                    future.complete(PluginResult.failure(ID, "Exit code: " + exitCode));
                } else {
                    eventBus.emit(TeamMindEvent.of(EventType.TASK_COMPLETED, context.taskId(), ID, "LEAD",
                            Map.of("output_length", fullOutput.length())));
                    Map<String, Object> data = new HashMap<>();
                    data.put("plugin_id", ID);
                    data.put("task_id", context.taskId());
                    data.put("output_length", fullOutput.length());
                    future.complete(PluginResult.success(ID, data));
                }
            } catch (Exception e) {
                eventBus.emit(TeamMindEvent.of(EventType.TASK_FAILED, context.taskId(), ID, "LEAD",
                        Map.of("error", e.getMessage())));
                future.completeExceptionally(e);
            }
        }, "codex-stream-" + context.taskId());
        t.setDaemon(true);
        t.start();
        return future;
    }

    @Override public void cancel() {
        log.info("[{}] Cancelling Codex execution", ID);
        cancelled.set(true);
        if (currentProcess != null) {
            currentProcess.destroyForcibly();
        }
    }

    @Override
    public PluginHealth inspect() {
        try {
            Process p = new ProcessBuilder("codex", "--version").redirectErrorStream(true).start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            int exit = finished ? p.exitValue() : -1;
            return exit == 0 ? PluginHealth.HEALTHY : PluginHealth.DEGRADED;
        } catch (Exception e) {
            return PluginHealth.UNHEALTHY;
        }
    }

    @Override
    public List<PluginDependency> dependencies() {
        return List.of(
                // Codex CLI 本身
                PluginDependency.builder()
                        .type(DependencyType.EXECUTABLE)
                        .name("codex-cli")
                        .checkCommand("codex --version")
                        .minVersion("0.144.5")
                        .build(),
                // 本地 LLM provider（由 Codex++ 提供）
                PluginDependency.builder()
                        .type(DependencyType.SERVICE)
                        .name("local-provider")
                        .endpoint("http://127.0.0.1:57321/v1/models")
                        .healthCheckTimeoutMs(5000)
                        .recoveryProcess("D:\\ProgramFiles\\Codex++\\codex-plus-plus.exe")
                        .recoveryArgs(new String[]{"--minimized"})
                        .build(),
                // 认证配置
                PluginDependency.builder()
                        .type(DependencyType.AUTH)
                        .name("codex-auth")
                        .checkCommand("test -f ~/.codex/config.toml")
                        .build()
        );
    }

    @Override
    public boolean attemptRecovery() {
        log.info("[{}] Attempting recovery...", ID);
        try {
            Process p = new ProcessBuilder(
                    "D:\\ProgramFiles\\Codex++\\codex-plus-plus.exe", "--minimized")
                    .start();
            log.info("[{}] Launched Codex++ (PID={})", ID, p.pid());

            // Wait up to 30s for provider to become ready
            for (int i = 0; i < 6; i++) {
                Thread.sleep(5000);
                if (checkProviderReady()) {
                    log.info("[{}] Provider recovered successfully", ID);
                    return true;
                }
            }
            log.warn("[{}] Provider did not become ready after 30s", ID);
            return false;
        } catch (Exception e) {
            log.error("[{}] Recovery failed: {}", ID, e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> diagnose() {
        Map<String, Object> details = new HashMap<>();
        details.put("cli_version", VERSION);
        details.put("provider_endpoint", "http://127.0.0.1:57321/v1/models");
        details.put("config_path", System.getProperty("user.home") + "/.codex/config.toml");

        try {
            java.net.URL url = new java.net.URL("http://127.0.0.1:57321/v1/models");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int status = conn.getResponseCode();
            conn.disconnect();
            details.put("provider_status", status == 200 ? "reachable" : "unreachable (" + status + ")");
        } catch (Exception e) {
            details.put("provider_status", "unreachable: " + e.getMessage());
        }

        return details;
    }

    private boolean checkProviderReady() {
        try {
            java.net.URL url = new java.net.URL("http://127.0.0.1:57321/v1/models");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int status = conn.getResponseCode();
            conn.disconnect();
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Internal helpers ──────────────────────────────────────

    private Process buildProcess(String prompt, String projectPath) throws java.io.IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("codex");
        cmd.add(prompt);
        if (projectPath != null && !projectPath.isBlank() && !projectPath.equals(".")) {
            cmd.add("--cwd");
            cmd.add(projectPath);
        }
        log.debug("[{}] Command: {}", ID, cmd);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(Path.of(projectPath).toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private String runCodexCommand(String prompt, String projectPath) throws Exception {
        Process proc = buildProcess(prompt, projectPath);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                parseAndEmitLine(line, null, null);
            }
        }
        boolean finished = proc.waitFor(10, TimeUnit.MINUTES);
        int exitCode = finished ? proc.exitValue() : -1;
        if (exitCode != 0) {
            throw new RuntimeException("Codex CLI exited with code " + exitCode);
        }
        return sb.toString();
    }

    /**
     * 解析 Codex 输出行并转换为 TeamMindEvent
     * Codex 输出格式：
     *   - ">>> STEP ..." → tool call / step start
     *   - 纯文本行       → agent chunk
     *   - "✓ Done"       → completion
     *   - "✗ Error"      → failure
     */
    private void parseAndEmitLine(String line, String taskId, PluginChunkHandler handler) {
        if (line == null || line.isBlank()) return;

        if (line.startsWith(">>>")) {
            // Step 开始
            if (taskId != null) {
                eventBus.emit(TeamMindEvent.of(EventType.TOOL_CALLED, taskId, ID, "LEAD",
                        Map.of("step", line.trim())));
            }
            if (handler != null) handler.onChunk(Map.of("type", "step_start", "line", line));
        } else if (line.startsWith("✓") || line.contains("Done")) {
            if (taskId != null) {
                eventBus.emit(TeamMindEvent.of(EventType.EVIDENCE_VERIFIED, taskId, ID, "LEAD",
                        Map.of("status", "completed")));
            }
            if (handler != null) handler.onChunk(Map.of("type", "done"));
        } else if (line.startsWith("✗") || line.contains("Error") || line.contains("error")) {
            if (taskId != null) {
                eventBus.emit(TeamMindEvent.of(EventType.ERROR_RECOVERABLE, taskId, ID, "LEAD",
                        Map.of("error", line)));
            }
            if (handler != null) handler.onChunk(Map.of("type", "error", "line", line));
        } else if (!line.startsWith("  ") && !line.trim().isEmpty()) {
            // 普通文本输出
            if (taskId != null) {
                eventBus.emit(TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, ID, "LEAD",
                        Map.of("content", line)));
            }
            if (handler != null) handler.onChunk(line);
        }
    }

    private String resolvePrompt(PluginContext context) {
        Object prompt = context.taskConfig().get("prompt");
        if (prompt instanceof String s) return s;
        Object objective = context.taskConfig().get("objective");
        if (objective instanceof String s) return s;
        return "Complete the task: " + context.taskId();
    }

    @Override public void onLoad() {
        log.info("[{}] Plugin loaded v{}", ID, VERSION);
    }

    @Override public void onUnload() {
        cancel();
        log.info("[{}] Plugin unloaded", ID);
    }
}
