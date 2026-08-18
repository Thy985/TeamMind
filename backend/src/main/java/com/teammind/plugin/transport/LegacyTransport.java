package com.teammind.plugin.transport;

import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import com.teammind.plugin.adapter.CLIConfig;
import com.teammind.plugin.adapter.GenericCLIPlugin;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LegacyTransport — 基于 ProcessBuilder 的传统 CLI 路径
 *
 * 封装现有 GenericCLIPlugin 行为：
 * - 通过 ProcessBuilder 启动 CLI
 * - 读取 stdout，通过 EventMapper 解析为 TeamMind 事件
 * - 支持 text / NDJSON / structured 输出格式
 *
 * 这是现有 CodexPlugin/ClaudeCodePlugin 的底层 transport，
 * 不改变任何现有行为。
 */
@Slf4j
public class LegacyTransport implements AgentTransport {

    private final CLIConfig config;
    private final EventBus eventBus;
    private final GenericCLIPlugin plugin;
    private final AtomicInteger sessionIdCounter = new AtomicInteger(0);
    private final Map<String, GenericCLIPlugin> activeSessions = new ConcurrentHashMap<>();

    public LegacyTransport(CLIConfig config, EventBus eventBus) {
        this.config = config;
        this.eventBus = eventBus;
        this.plugin = new GenericCLIPlugin(config, eventBus);
    }

    @Override
    public TransportType type() {
        return TransportType.LEGACY;
    }

    @Override
    public AgentSession start(AgentConfig agentConfig) {
        String sessionId = "legacy-" + System.currentTimeMillis() + "-" + sessionIdCounter.incrementAndGet();
        log.info("[{}] Starting LegacyTransport session: {}", config.cliId(), sessionId);

        // 复用现有 plugin 实例（每个 session 一个 plugin 实例）
        GenericCLIPlugin sessionPlugin = new GenericCLIPlugin(config, eventBus);
        activeSessions.put(sessionId, sessionPlugin);

        return new LegacyAgentSession(sessionId, config.cliId(), sessionPlugin);
    }

    @Override
    public void close() {
        log.info("[{}] Closing LegacyTransport, {} active sessions", config.cliId(), activeSessions.size());
        activeSessions.values().forEach(GenericCLIPlugin::kill);
        activeSessions.clear();
    }

    @Override
    public TransportCapabilities capabilities() {
        return TransportCapabilities.LEGACY_MINIMAL;
    }

    /**
     * Legacy Agent Session — 包装 GenericCLIPlugin 为 AgentSession 接口
     */
    private class LegacyAgentSession implements AgentSession {
        private final String sessionId;
        private final String agentId;
        private final GenericCLIPlugin pluginInstance;
        private volatile boolean alive = false;

        LegacyAgentSession(String sessionId, String agentId, GenericCLIPlugin pluginInstance) {
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.pluginInstance = pluginInstance;
        }

        @Override
        public String submitPrompt(String prompt, Map<String, Object> context) {
            String workDir = (String) context.getOrDefault("projectPath", ".");
            try {
                pluginInstance.startProcess(prompt, workDir);
                alive = true;
                log.info("[{}] Session {} started, PID={}", agentId, sessionId, pluginInstance.getProcessHandle().orElse(null));
            } catch (Exception e) {
                log.error("[{}] Failed to start session {}: {}", agentId, sessionId, e.getMessage());
                throw new RuntimeException("Failed to start LegacyAgentSession: " + e.getMessage(), e);
            }
            return sessionId;
        }

        @Override
        public void cancel() {
            if (alive) {
                pluginInstance.cancel();
                alive = false;
                log.info("[{}] Session {} cancelled", agentId, sessionId);
            }
        }

        @Override
        public void close() {
            cancel();
            activeSessions.remove(sessionId);
            alive = false;
        }

        @Override
        public boolean isAlive() {
            return alive && pluginInstance.isAlive();
        }

        @Override
        public SessionMetadata metadata() {
            return new SessionMetadata(sessionId, agentId, TransportType.LEGACY, java.time.Instant.now());
        }
    }
}
