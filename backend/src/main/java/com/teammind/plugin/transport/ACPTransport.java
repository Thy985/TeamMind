package com.teammind.plugin.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.common.EventType;
import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import com.teammind.event.mapper.ACPEventMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ACPTransport — 基于 ACP 协议的 Agent 接入传输
 *
 * P0 实现：通过 Node.js ACP bridge 进程（如 codex-acp）进行通信，
 * bridge 将 ACP 协议事件输出到 stdout 的 JSONL 流，本类读取并映射。
 *
 * 架构路径：
 *   TeamMind (Java)
 *     ↓ ProcessBuilder
 *   ACP Bridge (Node.js, e.g. codex-acp)
 *     ↓ stdio JSON-RPC (ACP protocol)
 *   codex / claude-agent-acp → 实际 Agent
 *     ↓ stdout JSONL (ACP events)
 *   ACPEventMapper → TeamMind Events → EventBus
 *
 * P1 目标：使用官方 ACP SDK 进行真正的 JSON-RPC over stdio 通信，
 *          不再依赖 bridge 的 stdout 解析。
 */
@Slf4j
public class ACPTransport implements AgentTransport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ACPEventMapper eventMapper = new ACPEventMapper();
    private final EventBus eventBus;
    private final String bridgeCommand;     // ACP bridge 可执行文件（如 "codex-acp"）
    private final Map<String, ACPSession> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(0);

    public ACPTransport(EventBus eventBus, String bridgeCommand) {
        this.eventBus = eventBus;
        this.bridgeCommand = bridgeCommand;
    }

    @Override
    public TransportType type() {
        return TransportType.ACP;
    }

    @Override
    public AgentSession start(AgentConfig config) {
        String sessionId = "acp-" + System.currentTimeMillis() + "-" + sessionCounter.incrementAndGet();
        log.info("[ACP] Starting session: {} bridge={}", sessionId, bridgeCommand);

        ACPSession session = new ACPSession(sessionId, config, bridgeCommand, eventBus, eventMapper);
        activeSessions.put(sessionId, session);
        return session;
    }

    @Override
    public void close() {
        log.info("[ACP] Closing ACPTransport, {} active sessions", activeSessions.size());
        activeSessions.values().forEach(ACPSession::close);
        activeSessions.clear();
    }

    @Override
    public TransportCapabilities capabilities() {
        return TransportCapabilities.ACP_FULL;
    }

    /**
     * ACP Session — 管理单次 ACP bridge 进程的生命周期
     */
    private class ACPSession implements AgentSession {
        private final String sessionId;
        private final AgentConfig config;
        private final String bridgeCommand;
        private final EventBus eventBus;
        private final ACPEventMapper eventMapper;
        private volatile Process bridgeProcess;
        private volatile boolean alive = false;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        ACPSession(String sessionId, AgentConfig config, String bridgeCommand,
                   EventBus eventBus, ACPEventMapper eventMapper) {
            this.sessionId = sessionId;
            this.config = config;
            this.bridgeCommand = bridgeCommand;
            this.eventBus = eventBus;
            this.eventMapper = eventMapper;
        }

        @Override
        public String submitPrompt(String prompt, Map<String, Object> context) {
            if (isAlive()) {
                log.warn("[ACP] Session {} already running, cancelling first", sessionId);
                cancel();
            }

            try {
                bridgeProcess = spawnBridge(prompt, context);
                alive = true;
                readOutputLoop();
                log.info("[ACP] Session {} started, PID={}", sessionId, bridgeProcess.pid());
                return sessionId;
            } catch (IOException e) {
                log.error("[ACP] Failed to start bridge for session {}: {}", sessionId, e.getMessage());
                throw new RuntimeException("Failed to start ACP bridge: " + e.getMessage(), e);
            }
        }

        private Process spawnBridge(String prompt, Map<String, Object> context) throws IOException {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(bridgeCommand);

            // ACP bridge 通常接受 prompt 作为最后一个参数或 stdin
            // 具体格式取决于 bridge 实现
            cmd.add("--prompt");
            cmd.add(prompt);

            // 注入工作目录
            String workDir = (String) context.getOrDefault("projectPath", ".");
            log.info("[ACP] Spawning bridge: {} with workDir={}", cmd, workDir);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new java.io.File(workDir));
            pb.redirectErrorStream(false); // stderr 单独处理
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);

            return pb.start();
        }

        /**
         * 后台线程读取 bridge stdout，映射事件
         */
        private void readOutputLoop() {
            Thread readerThread = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(bridgeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (cancelled.get() || bridgeProcess == null || !bridgeProcess.isAlive()) {
                            break;
                        }
                        parseACPEvent(line);
                    }
                } catch (IOException e) {
                    if (!cancelled.get()) {
                        log.warn("[ACP] Session {} output stream closed: {}", sessionId, e.getMessage());
                    }
                } finally {
                    onSessionComplete();
                }
            }, "acp-reader-" + sessionId);
            readerThread.setDaemon(true);
            readerThread.start();
        }

        private void parseACPEvent(String line) {
            if (line == null || line.isBlank() || !line.startsWith("{")) {
                // Non-JSON line — treat as raw output
                if (!line.isBlank()) {
                    eventBus.emit(TeamMindEvent.of(EventType.AGENT_CHUNK,
                            sessionId, bridgeCommand, "EXECUTOR",
                            Map.of("raw_output", line.substring(0, Math.min(200, line.length())))));
                }
                return;
            }

            JsonNode node;
            try {
                node = MAPPER.readTree(line);
            } catch (Exception e) {
                log.debug("[ACP] Failed to parse bridge event: {}", e.getMessage());
                return;
            }

            String eventType = node.has("type") ? node.get("type").asText() : null;
            if (eventType == null) return;

            // Map ACP event to TeamMind events
            var cliEvent = new com.teammind.event.EventMapper.CliEvent(line, node, eventType);
            var mapCtx = new com.teammind.event.EventMapper.MapContext(
                    sessionId, bridgeCommand, "EXECUTOR");

            try {
                var teamMindEvents = eventMapper.map(cliEvent, mapCtx);
                for (TeamMindEvent tmEvent : teamMindEvents) {
                    eventBus.emit(tmEvent);
                }
            } catch (Exception e) {
                log.warn("[ACP] Event mapping failed for type={}: {}", eventType, e.getMessage());
            }
        }

        private void onSessionComplete() {
            alive = false;
            activeSessions.remove(sessionId);
            log.info("[ACP] Session {} completed", sessionId);
        }

        @Override
        public void cancel() {
            log.info("[ACP] Cancelling session {}", sessionId);
            cancelled.set(true);
            if (bridgeProcess != null && bridgeProcess.isAlive()) {
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
            return new SessionMetadata(sessionId, bridgeCommand, TransportType.ACP, java.time.Instant.now());
        }
    }
}
