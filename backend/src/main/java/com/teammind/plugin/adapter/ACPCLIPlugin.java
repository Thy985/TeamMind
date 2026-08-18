package com.teammind.plugin.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.common.EventType;
import com.teammind.event.EventBus;
import com.teammind.event.EventMapper;
import com.teammind.event.TeamMindEvent;
import com.teammind.event.mapper.ACPEventMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ACPCLIPlugin — ACP（Agent Client Protocol）结构化事件适配器
 *
 * 继承 GenericCLIPlugin，覆盖 NDJSON 解析逻辑，使用 ACPEventMapper
 * 将 Codex/Claude/ACP 兼容 CLI 的结构化 JSON 事件映射为 TeamMind 事件。
 *
 * 相比 GenericCLIPlugin 的简单 extractField 解析，本类使用 Jackson
 * 完整解析 JSON，支持嵌套结构和复杂事件类型。
 *
 * YAML 配置示例（codex-acp.yaml）：
 *   cli_id: codex-acp
 *   command: "node"
 *   args: ["<codex-path>", "exec", "--json", "--approve-for-me",
 *          "--sandbox", "danger-full-access", "--skip-git-repo-check", "<prompt>"]
 *   output_format: "ndjson"
 */
@Slf4j
public class ACPCLIPlugin extends GenericCLIPlugin {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ACPEventMapper eventMapper = new ACPEventMapper();

    public ACPCLIPlugin(CLIConfig config, EventBus eventBus) {
        super(config, eventBus);
    }

    // ─── Override NDJSON parsing with ACP-aware mapper ────────────

    @Override
    protected void parseNDJSON(String line, String taskId, PluginChunkHandler handler) {
        if (line == null || line.isBlank() || !line.startsWith("{")) {
            // Not JSON — fall back to text handling
            parseText(line, taskId, handler);
            return;
        }

        JsonNode node;
        try {
            node = MAPPER.readTree(line);
        } catch (Exception e) {
            log.debug("[{}] Failed to parse ACP event: {}", config().cliId(), e.getMessage());
            // Fall back to treating as text
            if (handler != null) handler.onChunk(line);
            return;
        }

        String eventType = node.has("type") ? node.get("type").asText() : null;
        if (eventType == null) {
            if (handler != null) handler.onChunk(line);
            return;
        }

        // Map ACP event to TeamMind events via ACPEventMapper
        EventMapper.CliEvent cliEvent = new EventMapper.CliEvent(line, node, eventType);
        EventMapper.MapContext ctx = new EventMapper.MapContext(
                taskId, config().cliId(), "LEAD");

        try {
            var teamMindEvents = eventMapper.map(cliEvent, ctx);
            for (TeamMindEvent tmEvent : teamMindEvents) {
                getEventBus().emit(tmEvent);
            }
        } catch (Exception e) {
            log.warn("[{}] ACP event mapping failed for type={}: {}",
                    config().cliId(), eventType, e.getMessage());
            handleFallbackACPEvent(node, taskId, handler);
        }
    }

    /**
     * Fallback handling when ACPEventMapper fails.
     * Covers common ACP event types with simple logic.
     */
    private void handleFallbackACPEvent(JsonNode node, String taskId, PluginChunkHandler handler) {
        String type = node.has("type") ? node.get("type").asText("") : "";

        switch (type) {
            case "terminal_output" -> {
                String text = node.has("text") ? node.get("text").asText("") : "";
                boolean isError = node.has("is_error") && node.get("is_error").asBoolean(false);
                if (!text.isBlank()) {
                    if (handler != null) handler.onChunk(text);
                    if (taskId != null) {
                        getEventBus().emit(TeamMindEvent.of(
                                isError ? EventType.ERROR_RECOVERABLE : EventType.AGENT_CHUNK,
                                taskId, config().cliId(), "LEAD",
                                Map.of("content", text.substring(0, Math.min(200, text.length())))));
                    }
                }
            }
            case "tool_call" -> {
                String toolName = node.has("tool_name") ? node.get("tool_name").asText("unknown") : "unknown";
                if (taskId != null) {
                    getEventBus().emit(TeamMindEvent.of(EventType.TOOL_CALLED, taskId, config().cliId(), "LEAD",
                            Map.of("tool", toolName, "source", "tool_call")));
                }
            }
            case "completion" -> {
                int exitCode = node.has("exit_code") ? node.get("exit_code").asInt(0) : 0;
                if (taskId != null) {
                    if (exitCode == 0) {
                        getEventBus().emit(TeamMindEvent.of(EventType.TASK_COMPLETED, taskId, config().cliId(), "LEAD",
                                Map.of("exit_code", exitCode, "source", "completion")));
                    } else {
                        getEventBus().emit(TeamMindEvent.of(EventType.TASK_FAILED, taskId, config().cliId(), "LEAD",
                                Map.of("exit_code", exitCode, "source", "completion")));
                    }
                }
            }
            default -> {
                if (handler != null) handler.onChunk(node.toString());
            }
        }
    }
}
