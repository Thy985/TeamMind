package com.teammind.plugin.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.common.EventType;
import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QwenPawACPTransport — 通过 ACP 协议接入 QwenPaw Agent
 *
 * 架构：
 *   TeamMind (Java)
 *     ↓ ProcessBuilder
 *   qwenpaw-acp-bridge.py (Python, stdin/stdout JSONL)
 *     ↓ ACP protocol (when QwenPaw fully installed)
 *   qwenpaw acp (QwenPaw ACP agent server)
 *
 * POC 阶段使用 mock agent（EchoAgent）验证协议链路。
 * 生产阶段切换为真实 QwenPaw ACP server。
 */
@Slf4j
public class QwenPawACPTransport implements AgentTransport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EventBus eventBus;
    private final String bridgeScript;  // Python bridge 脚本路径
    private final Map<String, QwenPawSession> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(0);

    public QwenPawACPTransport(EventBus eventBus, String bridgeScript) {
        this.eventBus = eventBus;
        this.bridgeScript = bridgeScript;
    }

    @Override
    public TransportType type() {
        return TransportType.ACP;
    }

    @Override
    public AgentSession start(AgentConfig config) {
        String sessionId = "qwenpaw-" + System.currentTimeMillis() + "-" + sessionCounter.incrementAndGet();
        log.info("[QwenPawACP] Starting session: {} bridge={}", sessionId, bridgeScript);

        QwenPawSession session = new QwenPawSession(sessionId, config, bridgeScript, eventBus);
        activeSessions.put(sessionId, session);
        return session;
    }

    @Override
    public void close() {
        log.info("[QwenPawACP] Closing QwenPawACPTransport, {} active sessions", activeSessions.size());
        activeSessions.values().forEach(QwenPawSession::close);
        activeSessions.clear();
    }

    @Override
    public TransportCapabilities capabilities() {
        // QwenPaw supports full ACP: streaming, permissions, tools, session management
        return TransportCapabilities.ACP_FULL;
    }

    /**
     * QwenPaw Session — manages bridge subprocess lifecycle
     */
    private class QwenPawSession implements AgentSession {
        private final String sessionId;
        private final AgentConfig config;
        private final String bridgeScript;
        private final EventBus eventBus;
        private volatile Process bridgeProcess;
        private volatile boolean alive = false;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile String currentSessionId; // QwenPaw ACP session ID

        QwenPawSession(String sessionId, AgentConfig config, String bridgeScript, EventBus eventBus) {
            this.sessionId = sessionId;
            this.config = config;
            this.bridgeScript = bridgeScript;
            this.eventBus = eventBus;
        }

        @Override
        public String submitPrompt(String prompt, Map<String, Object> context) {
            if (isAlive()) {
                log.warn("[QwenPawACP] Session {} already running, cancelling first", sessionId);
                cancel();
            }

            try {
                bridgeProcess = spawnBridge(prompt, context);
                alive = true;
                startReadLoop();
                log.info("[QwenPawACP] Session {} started, PID={}", sessionId, bridgeProcess.pid());
                return sessionId;
            } catch (IOException e) {
                log.error("[QwenPawACP] Failed to start bridge for session {}: {}", sessionId, e.getMessage());
                throw new RuntimeException("Failed to start QwenPaw ACP bridge: " + e.getMessage(), e);
            }
        }

        private Process spawnBridge(String prompt, Map<String, Object> context) throws IOException {
            String python = detectPython();
            String workDir = config.workingDir() != null ? config.workingDir() : ".";

            ProcessBuilder pb = new ProcessBuilder(python, bridgeScript);
            pb.directory(new File(workDir));
            pb.redirectErrorStream(true); // merge stderr into stdout for simplicity
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

            Process proc = pb.start();

            // Send prompt via stdin
            String promptJson = "{\"action\":\"prompt\","
                    + "\"prompt\":\"" + escapeJson(prompt) + "\","
                    + "\"cwd\":\"" + escapeJson(workDir) + "\""
                    + "}";
            try (OutputStream os = proc.getOutputStream();
                 BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                bw.write(promptJson);
                bw.newLine();
                bw.flush();
            }

            return proc;
        }

        private void startReadLoop() {
            Thread readerThread = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(bridgeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (cancelled.get() || bridgeProcess == null || !bridgeProcess.isAlive()) {
                            break;
                        }
                        parseEvent(line);
                    }
                } catch (IOException e) {
                    if (!cancelled.get()) {
                        log.warn("[QwenPawACP] Session {} bridge stream closed: {}", sessionId, e.getMessage());
                    }
                } finally {
                    onSessionComplete();
                }
            }, "qwenpaw-reader-" + sessionId);
            readerThread.setDaemon(true);
            readerThread.start();
        }

        private void parseEvent(String line) {
            if (line == null || line.isBlank() || !line.startsWith("{")) {
                return;
            }
            JsonNode node;
            try {
                node = MAPPER.readTree(line);
            } catch (Exception e) {
                log.debug("[QwenPawACP] Failed to parse event: {}", e.getMessage());
                return;
            }

            String type = node.has("type") ? node.get("type").asText() : null;
            if (type == null) return;

            switch (type) {
                case "ready" -> {
                    String agent = node.has("agent") ? node.get("agent").asText() : "unknown";
                    String mode = node.has("mode") ? node.get("mode").asText() : "unknown";
                    log.info("[QwenPawACP] Bridge ready: agent={} mode={}", agent, mode);
                    eventBus.emit(TeamMindEvent.of(EventType.AGENT_STARTED,
                            sessionId, "qwenpaw", "CONSULTANT",
                            Map.of("agent", agent, "mode", mode)));
                }
                case "chunk" -> {
                    String text = node.has("text") ? node.get("text").asText() : "";
                    eventBus.emit(TeamMindEvent.of(EventType.AGENT_CHUNK,
                            sessionId, "qwenpaw", "CONSULTANT",
                            Map.of("text", text)));
                }
                case "tool" -> {
                    String toolName = node.has("name") ? node.get("name").asText() : "tool";
                    eventBus.emit(TeamMindEvent.of(EventType.TOOL_CALLED,
                            sessionId, "qwenpaw", "CONSULTANT",
                            Map.of("tool", toolName,
                                   "input", node.has("input") ? node.get("input").toString() : "")));
                }
                case "tool_result" -> {
                    String text = node.has("text") ? node.get("text").asText() : "";
                    eventBus.emit(TeamMindEvent.of(EventType.AGENT_CHUNK,
                            sessionId, "qwenpaw", "CONSULTANT",
                            Map.of("tool_result", text)));
                }
                case "permission" -> {
                    // Log permission request (auto-approved in POC)
                    String title = node.has("title") ? node.get("title").asText() : "Permission";
                    log.info("[QwenPawACP] Permission requested: {}", title);
                    eventBus.emit(TeamMindEvent.of(EventType.AGENT_CHUNK,
                            sessionId, "qwenpaw", "CONSULTANT",
                            Map.of("permission", title, "auto_approved", true)));
                    // Auto-approve for POC
                    approvePermission(node.get("request_id").asText());
                }
                case "done" -> {
                    String reason = node.has("stop_reason") ? node.get("stop_reason").asText() : "end_turn";
                    log.info("[QwenPawACP] Session {} done: reason={}", sessionId, reason);
                    eventBus.emit(TeamMindEvent.of(EventType.AGENT_COMPLETED,
                            sessionId, "qwenpaw", "CONSULTANT",
                            Map.of("stop_reason", reason)));
                }
                case "error" -> {
                    String msg = node.has("message") ? node.get("message").asText() : "Unknown error";
                    log.error("[QwenPawACP] Error: {}", msg);
                    eventBus.emit(TeamMindEvent.of(EventType.AGENT_FAILED,
                            sessionId, "qwenpaw", "CONSULTANT",
                            Map.of("error", msg)));
                }
                case "closed" -> onSessionComplete();
                default -> log.debug("[QwenPawACP] Unknown event type: {}", type);
            }
        }

        private void approvePermission(String requestId) {
            // Send approval back to bridge via stdin
            if (bridgeProcess != null && bridgeProcess.isAlive()) {
                try {
                    String approvalJson = "{\"action\":\"permission_response\","
                            + "\"request_id\":\"" + requestId + "\","
                            + "\"option_id\":\"allow_once\""
                            + "}";
                    try (OutputStream os = bridgeProcess.getOutputStream();
                         BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                        bw.write(approvalJson);
                        bw.newLine();
                        bw.flush();
                    }
                } catch (IOException e) {
                    log.warn("[QwenPawACP] Failed to send permission approval: {}", e.getMessage());
                }
            }
        }

        private void onSessionComplete() {
            alive = false;
            activeSessions.remove(sessionId);
            log.info("[QwenPawACP] Session {} completed", sessionId);
        }

        @Override
        public void cancel() {
            log.info("[QwenPawACP] Cancelling session {}", sessionId);
            cancelled.set(true);
            // Send cancel to bridge
            if (bridgeProcess != null && bridgeProcess.isAlive()) {
                try {
                    String cancelJson = "{\"action\":\"cancel\"}";
                    try (OutputStream os = bridgeProcess.getOutputStream();
                         BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                        bw.write(cancelJson);
                        bw.newLine();
                        bw.flush();
                    }
                } catch (IOException e) {
                    log.warn("[QwenPawACP] Failed to send cancel: {}", e.getMessage());
                }
                bridgeProcess.destroyForcibly();
            }
            alive = false;
        }

        @Override
        public void close() {
            cancel();
            activeSessions.remove(sessionId);
        }

        @Override
        public boolean isAlive() {
            return alive && bridgeProcess != null && bridgeProcess.isAlive();
        }

        @Override
        public SessionMetadata metadata() {
            return new SessionMetadata(sessionId, "qwenpaw", TransportType.ACP, java.time.Instant.now());
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private String detectPython() {
        // Try common Python paths
        String[] candidates = {
                "python",
                "python3",
                "py",
        };
        for (String py : candidates) {
            try {
                Process p = new ProcessBuilder(py, "--version")
                        .redirectErrorStream(true).start();
                p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                if (p.exitValue() == 0) return py;
            } catch (Exception e) { /* try next */ }
        }
        throw new RuntimeException("Python not found. Please install Python 3.11+.");
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
