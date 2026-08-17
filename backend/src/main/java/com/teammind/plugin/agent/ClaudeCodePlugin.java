package com.teammind.plugin.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.common.EventType;
import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import com.teammind.plugin.Plugin;
import com.teammind.plugin.adapter.WindowsCommandHelper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
 * Claude Code Plugin — 真实 ProcessBuilder 集成
 *
 * 调用方式：
 *   claude --print "<prompt>" [--dangerously-skip-permissions]
 *
 * 输出格式：NDJSON（newline-delimited JSON），每行一个事件。
 * 通过 EventEmitter 将原始行转换为 TeamMindEvent 并推送到 EventBus。
 */
@Slf4j
public class ClaudeCodePlugin implements Plugin {

    private static final String ID = "claude-code";
    private static final String VERSION = "2.1.215";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventBus eventBus;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Process currentProcess;

    public ClaudeCodePlugin(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    // ─── Plugin interface ──────────────────────────────────────

    @Override public String id() { return ID; }

    @Override public PluginType type() { return PluginType.AGENT; }

    @Override public String description() {
        return "安全导向的 AI 编程助手，强调权限边界和显式审批";
    }

    @Override public String version() { return VERSION; }

    @Override
    public PluginMetadata metadata() {
        return new PluginMetadata(
                ID, "Claude Code", VERSION, description(),
                List.of("implementation", "code_review", "security_review", "architecture_design", "documentation"),
                List.of("safety", "controlled_action", "explicit_permission", "cautious_execution"),
                List.of("security_review", "code_review", "architecture_review"),
                List.of("bulk_refactor", "rapid_iteration"),
                45_000L, 0.92, 0.05
        );
    }

    @Override
    public PluginResult invoke(PluginContext context) {
        String prompt = resolvePrompt(context);
        String projectPath = context.projectPath() != null ? context.projectPath() : ".";

        log.info("[{}] Invoking Claude Code: task={}, path={}", ID, context.taskId(), projectPath);

        // Emit TASK_STARTED
        eventBus.emit(TeamMindEvent.of(EventType.TASK_STARTED,
                context.taskId(), ID, "LEAD", Map.of("plugin_id", ID, "prompt_length", prompt.length())));

        try {
            String output = runClaudeCommand(prompt, projectPath);

            log.info("[{}] Claude Code completed for task {}", ID, context.taskId());
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
            log.error("[{}] Claude Code failed: {}", ID, e.getMessage(), e);
            eventBus.emit(TeamMindEvent.of(EventType.TASK_FAILED,
                    context.taskId(), ID, "LEAD",
                    Map.of("error", e.getMessage())));
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

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (cancelled.get()) {
                            proc.destroy();
                            break;
                        }
                        fullOutput.append(line).append("\n");
                        // 解析 NDJSON 行并逐条发射事件
                        parseAndEmitLine(line, context.taskId(), handler);
                    }
                }

                boolean finished = proc.waitFor(60, TimeUnit.MINUTES);
                int exitCode = finished ? proc.exitValue() : -1;
                if (cancelled.get()) {
                    eventBus.emit(TeamMindEvent.of(EventType.TASK_CANCELLED, context.taskId(), ID, "LEAD"));
                    future.complete(PluginResult.failure(ID, "Cancelled by user"));
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
        }, "claude-code-stream-" + context.taskId());
        t.setDaemon(true);
        t.start();
        return future;
    }

    @Override public void cancel() {
        log.info("[{}] Cancelling current execution", ID);
        cancelled.set(true);
        if (currentProcess != null) {
            currentProcess.destroyForcibly();
        }
    }

    @Override
    public PluginHealth inspect() {
        try {
            Process p = new ProcessBuilder(WindowsCommandHelper.wrap("claude --version"))
                    .redirectErrorStream(true).start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            int exit = finished ? p.exitValue() : -1;
            return exit == 0 ? PluginHealth.HEALTHY : PluginHealth.DEGRADED;
        } catch (Exception e) {
            return PluginHealth.UNHEALTHY;
        }
    }

    // ─── Internal helpers ──────────────────────────────────────

    private Process buildProcess(String prompt, String projectPath) throws java.io.IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("claude");
        cmd.add("--print");
        // 安全：prompt 作为单独参数，避免 shell 注入
        cmd.add(prompt);
        if (projectPath != null && !projectPath.isBlank() && !projectPath.equals(".")) {
            cmd.add("--output-format");
            cmd.add("json");
        }
        List<String> wrapped = WindowsCommandHelper.wrap(cmd);
        log.debug("[{}] Command: {}", ID, wrapped);
        ProcessBuilder pb = new ProcessBuilder(wrapped);
        pb.directory(Path.of(projectPath).toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private String runClaudeCommand(String prompt, String projectPath) throws Exception {
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
            throw new RuntimeException("Claude CLI exited with code " + exitCode);
        }
        return sb.toString();
    }

    /**
     * 解析 NDJSON 行并转换为 TeamMindEvent 发射到总线
     * Claude Code --output-format json 每行一个 JSON 对象
     */
    private void parseAndEmitLine(String line, String taskId, PluginChunkHandler handler) {
        if (line == null || line.isBlank()) return;
        try {
            JsonNode node = objectMapper.readTree(line);
            String type = node.path("type").asText("");
            if (type.isEmpty()) return;

            switch (type) {
                case "assistant" -> {
                    String message = node.path("message").path("content").isArray()
                            ? node.path("message").path("content").get(0).path("text").asText("")
                            : node.path("message").path("content").asText("");
                    if (!message.isBlank() && taskId != null) {
                        eventBus.emit(TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, ID, "LEAD",
                                Map.of("content", message)));
                    }
                    if (handler != null) handler.onChunk(message);
                }
                case "user" -> {
                    String msg = node.path("message").path("content").isArray()
                            ? node.path("message").path("content").get(0).path("text").asText("")
                            : node.path("message").path("content").asText("");
                    if (!msg.isBlank() && taskId != null) {
                        eventBus.emit(TeamMindEvent.of(EventType.TOOL_CALLED, taskId, ID, "LEAD",
                                Map.of("tool", "user_message", "content", msg)));
                    }
                }
                case "tool" -> {
                    String toolName = node.path("tool_name").asText("");
                    String input = node.path("input").toString();
                    if (!toolName.isBlank() && taskId != null) {
                        eventBus.emit(TeamMindEvent.of(EventType.TOOL_CALLED, taskId, ID, "LEAD",
                                Map.of("tool", toolName, "input", input)));
                    }
                    if (handler != null) handler.onChunk(Map.of("tool", toolName, "input", input));
                }
                case "result" -> {
                    boolean isError = node.path("is_error").asBoolean(false);
                    if (taskId != null) {
                        eventBus.emit(TeamMindEvent.of(
                                isError ? EventType.ERROR_RECOVERABLE : EventType.TOOL_RESULT,
                                taskId, ID, "LEAD",
                                Map.of("tool", node.path("tool_name").asText(""))));
                    }
                }
                default -> {}
            }
        } catch (Exception e) {
            // 单行解析失败不影响整体流程
            log.debug("[{}] Ignoring unparseable line: {}", ID, line);
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
