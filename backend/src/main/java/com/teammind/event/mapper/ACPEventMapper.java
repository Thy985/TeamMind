package com.teammind.event.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.teammind.common.EventType;
import com.teammind.event.EventMapper;
import com.teammind.event.TeamMindEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ACP Event Mapper — 将 ACP（Agent Client Protocol）结构化事件映射为 TeamMind 事件
 *
 * 支持的事件类型：
 *   terminal_output     → AGENT_CHUNK / ERROR_RECOVERABLE
 *   file_change         → FILE_CHANGED
 *   tool_call           → TOOL_CALLED
 *   tool_result         → TOOL_RESULT
 *   completion          → TASK_COMPLETED / TASK_FAILED
 *   permission_request  → DECISION_REQUIRES_APPROVAL
 *   session.started     → PROCESS_STARTED
 *   subagent.start      → AGENT_STARTED
 *   error               → ERROR_RECOVERABLE / ERROR_CRITICAL
 *
 * 设计原则：
 * - ACP 协议提供结构化事件，避免手动解析 stdout
 * - 每个 ACP 事件可映射为一个或多个 TeamMind 事件
 * - 保持与现有 EventMapper 接口兼容
 */
public class ACPEventMapper implements EventMapper {

    @Override
    public List<TeamMindEvent> map(CliEvent rawEvent, MapContext context) {
        if (rawEvent.parsed() == null || !(rawEvent.parsed() instanceof JsonNode node)) {
            return List.of();
        }

        String type = node.has("type") ? node.get("type").asText() : null;
        if (type == null) return List.of();

        String taskId = context.taskId();
        String pluginId = context.pluginId();
        String role = context.role();

        List<TeamMindEvent> events = new ArrayList<>();

        switch (type) {
            case "terminal_output" -> events.add(mapTerminalOutput(node, taskId, pluginId, role));
            case "file_change" -> events.add(mapFileChange(node, taskId, pluginId, role));
            case "tool_call" -> events.add(mapToolCall(node, taskId, pluginId, role));
            case "tool_result" -> events.add(mapToolResult(node, taskId, pluginId, role));
            case "completion" -> events.addAll(mapCompletion(node, taskId, pluginId, role));
            case "permission_request", "approval_request" ->
                    events.add(mapApprovalRequest(node, taskId, pluginId, role));
            case "session.started", "session_start" ->
                    events.add(mapSessionStarted(node, taskId, pluginId, role));
            case "subagent.start", "subagent_started" ->
                    events.add(mapSubagentStart(node, taskId, pluginId, role));
            case "error" -> events.add(mapError(node, taskId, pluginId, role));
            // ─── Codex --json events ──────────────────────────────
            case "thread.started" ->
                    events.add(mapCodexThreadStarted(node, taskId, pluginId, role));
            case "turn.started" ->
                    events.add(mapCodexTurnStarted(node, taskId, pluginId, role));
            case "turn.completed" ->
                    events.addAll(mapCodexTurnCompleted(node, taskId, pluginId, role));
            case "item.completed" -> events.addAll(mapCodexItemCompleted(node, taskId, pluginId, role));
            // ─── Claude stream-json events ────────────────────────
            case "system" -> events.addAll(mapClaudeSystem(node, taskId, pluginId, role));
            case "assistant" -> events.addAll(mapClaudeAssistant(node, taskId, pluginId, role));
            case "result" -> events.addAll(mapClaudeResult(node, taskId, pluginId, role));
            default -> events.add(mapUnknownEvent(node, taskId, pluginId, role, type));
        }

        return events;
    }

    // ─── Event mapping methods ──────────────────────────────────────

    private TeamMindEvent mapTerminalOutput(JsonNode node, String taskId, String pluginId, String role) {
        String text = node.has("text") ? node.get("text").asText("") : "";
        boolean isError = node.has("is_error") && node.get("is_error").asBoolean(false);
        EventType eventType = isError ? EventType.ERROR_RECOVERABLE : EventType.AGENT_CHUNK;

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("content", text);
        meta.put("is_error", isError);
        meta.put("source", "terminal_output");

        // Split long outputs into chunks
        if (text.length() > 500) {
            meta.put("content_truncated", true);
            meta.put("total_length", text.length());
        }

        return TeamMindEvent.of(eventType, taskId, pluginId, role, meta);
    }

    private TeamMindEvent mapFileChange(JsonNode node, String taskId, String pluginId, String role) {
        String path = node.has("path") ? node.get("path").asText("") : "";
        String action = node.has("action") ? node.get("action").asText("unknown") : "unknown";

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("path", path);
        meta.put("action", action);
        meta.put("source", "file_change");

        return TeamMindEvent.of(EventType.FILE_CHANGED, taskId, pluginId, role, meta);
    }

    private TeamMindEvent mapToolCall(JsonNode node, String taskId, String pluginId, String role) {
        String toolName = node.has("tool_name") ? node.get("tool_name").asText("unknown") : "unknown";

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("tool", toolName);
        meta.put("source", "tool_call");

        if (node.has("input")) {
            try {
                String inputStr = node.get("input").toString();
                meta.put("input", inputStr.length() > 200
                        ? inputStr.substring(0, 200) + "..." : inputStr);
            } catch (Exception e) {
                meta.put("input", "[parse_error]");
            }
        }

        return TeamMindEvent.of(EventType.TOOL_CALLED, taskId, pluginId, role, meta);
    }

    private TeamMindEvent mapToolResult(JsonNode node, String taskId, String pluginId, String role) {
        String toolName = node.has("tool_name") ? node.get("tool_name").asText("unknown") : "unknown";
        boolean isError = node.has("is_error") && node.get("is_error").asBoolean(false);
        String result = node.has("result") ? node.get("result").asText("") : "";

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("tool", toolName);
        meta.put("is_error", isError);
        meta.put("result_length", result.length());
        meta.put("source", "tool_result");

        return TeamMindEvent.of(EventType.TOOL_RESULT, taskId, pluginId, role, meta);
    }

    private List<TeamMindEvent> mapCompletion(JsonNode node, String taskId, String pluginId, String role) {
        List<TeamMindEvent> events = new ArrayList<>();
        int exitCode = node.has("exit_code") ? node.get("exit_code").asInt(0) : 0;

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("exit_code", exitCode);
        meta.put("source", "completion");

        if (node.has("summary")) {
            String summary = node.get("summary").asText("");
            meta.put("summary_length", summary.length());
        }

        // Agent 完成 ≠ Evidence 已验证。
        // 独立的 Verifier（GitVerifier / TestRunner）负责验证，此处只发 TASK_COMPLETED。
        if (exitCode == 0) {
            events.add(TeamMindEvent.of(EventType.TASK_COMPLETED, taskId, pluginId, role, meta));
        } else {
            events.add(TeamMindEvent.of(EventType.TASK_FAILED, taskId, pluginId, role, meta));
        }

        return events;
    }

    private TeamMindEvent mapApprovalRequest(JsonNode node, String taskId, String pluginId, String role) {
        String description = node.has("description") ? node.get("description").asText("") : "";
        String requestId = node.has("request_id") ? node.get("request_id").asText("") : "";
        String toolName = node.has("tool_name") ? node.get("tool_name").asText("unknown") : "unknown";

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("description", description);
        meta.put("request_id", requestId);
        meta.put("tool", toolName);
        meta.put("source", "approval_request");

        return TeamMindEvent.of(EventType.DECISION_REQUIRES_APPROVAL, taskId, pluginId, role, meta);
    }

    private TeamMindEvent mapSessionStarted(JsonNode node, String taskId, String pluginId, String role) {
        String sessionId = node.has("session_id") ? node.get("session_id").asText("") : "";

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("session_id", sessionId);
        meta.put("source", "session_started");

        return TeamMindEvent.of(EventType.PROCESS_STARTED, taskId, pluginId, role, meta);
    }

    private TeamMindEvent mapSubagentStart(JsonNode node, String taskId, String pluginId, String role) {
        String agentId = node.has("agent") ? node.get("agent").asText("unknown") : "unknown";

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("subagent", agentId);
        meta.put("source", "subagent_start");

        return TeamMindEvent.of(EventType.AGENT_STARTED, taskId, pluginId, role, meta);
    }

    private TeamMindEvent mapError(JsonNode node, String taskId, String pluginId, String role) {
        String message = node.has("message") ? node.get("message").asText("") : "";
        boolean isCritical = node.has("critical") && node.get("critical").asBoolean(false);

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("message", message);
        meta.put("critical", isCritical);
        meta.put("source", "error");

        return TeamMindEvent.of(isCritical ? EventType.ERROR_CRITICAL : EventType.ERROR_RECOVERABLE,
                taskId, pluginId, role, meta);
    }

    // ─── Codex --json event mappers ───────────────────────────────

    private TeamMindEvent mapCodexThreadStarted(JsonNode node, String taskId, String pluginId, String role) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("thread_id", node.has("thread_id") ? node.get("thread_id").asText("") : "");
        meta.put("source", "codex_thread_started");
        return TeamMindEvent.of(EventType.PROCESS_STARTED, taskId, pluginId, role, meta);
    }

    private TeamMindEvent mapCodexTurnStarted(JsonNode node, String taskId, String pluginId, String role) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("source", "codex_turn_started");
        return TeamMindEvent.of(EventType.AGENT_THINKING, taskId, pluginId, role, meta);
    }

    private List<TeamMindEvent> mapCodexTurnCompleted(JsonNode node, String taskId, String pluginId, String role) {
        List<TeamMindEvent> events = new ArrayList<>();
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("source", "codex_turn_completed");
        if (node.has("usage")) {
            JsonNode usage = node.get("usage");
            meta.put("input_tokens", usage.has("input_tokens") ? usage.get("input_tokens").asLong() : null);
            meta.put("output_tokens", usage.has("output_tokens") ? usage.get("output_tokens").asLong() : null);
        }
        events.add(TeamMindEvent.of(EventType.TASK_COMPLETED, taskId, pluginId, role, meta));
        return events;
    }

    private List<TeamMindEvent> mapCodexItemCompleted(JsonNode node, String taskId, String pluginId, String role) {
        List<TeamMindEvent> events = new ArrayList<>();
        JsonNode item = node.has("item") ? node.get("item") : null;
        if (item == null) return events;

        String itemType = item.has("type") ? item.get("type").asText("") : "";
        String itemId = item.has("id") ? item.get("id").asText("") : "";

        switch (itemType) {
            case "agent_message" -> {
                String text = item.has("text") ? item.get("text").asText("") : "";
                if (!text.isBlank()) {
                    Map<String, Object> meta = new java.util.LinkedHashMap<>();
                    meta.put("content", text.substring(0, Math.min(200, text.length())));
                    meta.put("item_id", itemId);
                    meta.put("source", "codex_agent_message");
                    events.add(TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, pluginId, role, meta));
                }
            }
            case "error" -> {
                String message = item.has("message") ? item.get("message").asText("") : "";
                Map<String, Object> meta = new java.util.LinkedHashMap<>();
                meta.put("message", message);
                meta.put("item_id", itemId);
                meta.put("source", "codex_error");
                events.add(TeamMindEvent.of(EventType.ERROR_RECOVERABLE, taskId, pluginId, role, meta));
            }
            case "reasoning" -> {
                String text = item.has("text") ? item.get("text").asText("") : "";
                if (!text.isBlank()) {
                    Map<String, Object> meta = new java.util.LinkedHashMap<>();
                    meta.put("content", text.substring(0, Math.min(200, text.length())));
                    meta.put("source", "codex_reasoning");
                    events.add(TeamMindEvent.of(EventType.AGENT_THINKING, taskId, pluginId, role, meta));
                }
            }
            default -> {
                // Other item types (tool_use, etc.) — map to TOOL_CALLED
                Map<String, Object> meta = new java.util.LinkedHashMap<>();
                meta.put("item_type", itemType);
                meta.put("item_id", itemId);
                meta.put("source", "codex_item_completed");
                events.add(TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, pluginId, role, meta));
            }
        }
        return events;
    }

    // ─── Claude stream-json event mappers ─────────────────────────

    private List<TeamMindEvent> mapClaudeSystem(JsonNode node, String taskId, String pluginId, String role) {
        List<TeamMindEvent> events = new ArrayList<>();
        String subtype = node.has("subtype") ? node.get("subtype").asText("") : "";

        switch (subtype) {
            case "init" -> {
                Map<String, Object> meta = new java.util.LinkedHashMap<>();
                meta.put("session_id", node.has("session_id") ? node.get("session_id").asText("") : "");
                meta.put("model", node.has("model") ? node.get("model").asText("") : "");
                meta.put("permission_mode", node.has("permissionMode") ? node.get("permissionMode").asText("") : "");
                meta.put("source", "claude_system_init");
                events.add(TeamMindEvent.of(EventType.PROCESS_STARTED, taskId, pluginId, role, meta));
            }
            case "thinking_tokens" -> {
                Map<String, Object> meta = new java.util.LinkedHashMap<>();
                meta.put("estimated_tokens", node.has("estimated_tokens") ? node.get("estimated_tokens").asInt(0) : 0);
                meta.put("source", "claude_thinking");
                events.add(TeamMindEvent.of(EventType.AGENT_THINKING, taskId, pluginId, role, meta));
            }
            default -> {
                Map<String, Object> meta = new java.util.LinkedHashMap<>();
                meta.put("raw_subtype", subtype);
                meta.put("source", "claude_system");
                events.add(TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, pluginId, role, meta));
            }
        }
        return events;
    }

    private List<TeamMindEvent> mapClaudeAssistant(JsonNode node, String taskId, String pluginId, String role) {
        List<TeamMindEvent> events = new ArrayList<>();
        JsonNode message = node.has("message") ? node.get("message") : null;
        if (message == null) return events;

        JsonNode content = message.has("content") ? message.get("content") : null;
        if (content == null || !content.isArray()) return events;

        for (JsonNode part : content) {
            String partType = part.has("type") ? part.get("type").asText("") : "";
            switch (partType) {
                case "text" -> {
                    String text = part.has("text") ? part.get("text").asText("") : "";
                    if (!text.isBlank()) {
                        Map<String, Object> meta = new java.util.LinkedHashMap<>();
                        meta.put("content", text.substring(0, Math.min(200, text.length())));
                        meta.put("message_id", message.has("id") ? message.get("id").asText("") : "");
                        meta.put("source", "claude_assistant_text");
                        events.add(TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, pluginId, role, meta));
                    }
                }
                case "thinking" -> {
                    String text = part.has("thinking") ? part.get("thinking").asText("") : "";
                    if (!text.isBlank()) {
                        Map<String, Object> meta = new java.util.LinkedHashMap<>();
                        meta.put("content", text.substring(0, Math.min(200, text.length())));
                        meta.put("source", "claude_thinking");
                        events.add(TeamMindEvent.of(EventType.AGENT_THINKING, taskId, pluginId, role, meta));
                    }
                }
                case "tool_use" -> {
                    String name = part.has("name") ? part.get("name").asText("unknown") : "unknown";
                    Map<String, Object> meta = new java.util.LinkedHashMap<>();
                    meta.put("tool", name);
                    meta.put("input", part.has("input") ? part.get("input").toString() : "{}");
                    meta.put("source", "claude_tool_use");
                    events.add(TeamMindEvent.of(EventType.TOOL_CALLED, taskId, pluginId, role, meta));
                }
                default -> {
                    Map<String, Object> meta = new java.util.LinkedHashMap<>();
                    meta.put("part_type", partType);
                    meta.put("source", "claude_assistant");
                    events.add(TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, pluginId, role, meta));
                }
            }
        }
        return events;
    }

    private List<TeamMindEvent> mapClaudeResult(JsonNode node, String taskId, String pluginId, String role) {
        List<TeamMindEvent> events = new ArrayList<>();
        String subtype = node.has("subtype") ? node.get("subtype").asText("") : "";
        boolean isError = "error".equals(subtype);

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("source", "claude_result");
        meta.put("stop_reason", node.has("stop_reason") ? node.get("stop_reason").asText("") : "");
        meta.put("duration_ms", node.has("duration_ms") ? node.get("duration_ms").asLong() : null);

        if (node.has("result")) {
            String result = node.get("result").asText("");
            meta.put("result_length", result.length());
            meta.put("result_truncated", result.length() > 500);
        }

        if (isError) {
            events.add(TeamMindEvent.of(EventType.TASK_FAILED, taskId, pluginId, role, meta));
        } else {
            events.add(TeamMindEvent.of(EventType.TASK_COMPLETED, taskId, pluginId, role, meta));
        }
        return events;
    }

    private TeamMindEvent mapUnknownEvent(JsonNode node, String taskId, String pluginId, String role, String type) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("raw_type", type);
        meta.put("raw_json", node.toString().substring(0, Math.min(200, node.toString().length())));
        meta.put("source", "unknown_acp_event");

        return TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, pluginId, role, meta);
    }

    @Override
    public List<EventType> supportedEventTypes() {
        return List.of(
                EventType.AGENT_CHUNK,
                EventType.TOOL_CALLED,
                EventType.TOOL_RESULT,
                EventType.FILE_CHANGED,
                EventType.DECISION_REQUIRES_APPROVAL,
                EventType.TASK_COMPLETED,
                EventType.TASK_FAILED,
                EventType.ERROR_RECOVERABLE,
                EventType.ERROR_CRITICAL,
                EventType.EVIDENCE_VERIFIED,
                EventType.PROCESS_STARTED,
                EventType.AGENT_STARTED,
                EventType.AGENT_THINKING
        );
    }
}
