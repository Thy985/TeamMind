package com.teammind.event.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.common.EventType;
import com.teammind.event.EventMapper;
import com.teammind.event.TeamMindEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ACPEventMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private ACPEventMapper mapper;
    private EventMapper.MapContext ctx;

    @BeforeEach
    void setUp() {
        mapper = new ACPEventMapper();
        ctx = new EventMapper.MapContext("task-001", "codex-acp", "EXECUTOR");
    }

    // ─── terminal_output ──────────────────────────────────────────

    @Test
    @DisplayName("terminal_output → AGENT_CHUNK")
    void terminalOutputMapsToAgentChunk() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"terminal_output\",\"text\":\"Writing code...\"}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "terminal_output"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.AGENT_CHUNK, events.get(0).type());
        assertEquals("Writing code...", events.get(0).metadata().get("content"));
        assertFalse((Boolean) events.get(0).metadata().get("is_error"));
    }

    @Test
    @DisplayName("terminal_output with is_error=true → ERROR_RECOVERABLE")
    void terminalOutputErrorMapsToErrorRecoverable() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"terminal_output\",\"text\":\"Error: file not found\",\"is_error\":true}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "terminal_output"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.ERROR_RECOVERABLE, events.get(0).type());
    }

    @Test
    @DisplayName("terminal_output long text → truncated content")
    void terminalOutputLongTextTruncated() throws Exception {
        String longText = "x".repeat(600);
        JsonNode node = MAPPER.readTree("{\"type\":\"terminal_output\",\"text\":\"" + longText + "\"}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "terminal_output"), ctx);

        assertEquals(1, events.size());
        assertTrue((Boolean) events.get(0).metadata().get("content_truncated"));
        assertEquals(600, events.get(0).metadata().get("total_length"));
    }

    // ─── file_change ──────────────────────────────────────────────

    @Test
    @DisplayName("file_change → FILE_CHANGED")
    void fileChangeMapsToFileChanged() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"file_change\",\"path\":\"src/main.java\",\"action\":\"create\"}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "file_change"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.FILE_CHANGED, events.get(0).type());
        assertEquals("src/main.java", events.get(0).metadata().get("path"));
        assertEquals("create", events.get(0).metadata().get("action"));
    }

    // ─── tool_call ────────────────────────────────────────────────

    @Test
    @DisplayName("tool_call → TOOL_CALLED with input")
    void toolCallMapsToToolCalled() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"tool_call\",\"tool_name\":\"Bash\",\"input\":{\"command\":\"npm install\"}}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "tool_call"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.TOOL_CALLED, events.get(0).type());
        assertEquals("Bash", events.get(0).metadata().get("tool"));
        assertNotNull(events.get(0).metadata().get("input"));
    }

    // ─── tool_result ──────────────────────────────────────────────

    @Test
    @DisplayName("tool_result → TOOL_RESULT")
    void toolResultMapsToToolResult() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"tool_result\",\"tool_name\":\"Bash\",\"is_error\":false,\"result\":\"installed\"}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "tool_result"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.TOOL_RESULT, events.get(0).type());
        assertEquals("Bash", events.get(0).metadata().get("tool"));
        assertFalse((Boolean) events.get(0).metadata().get("is_error"));
    }

    @Test
    @DisplayName("tool_result with is_error=true")
    void toolResultWithError() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"tool_result\",\"tool_name\":\"Bash\",\"is_error\":true,\"result\":\"error: command not found\"}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "tool_result"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.TOOL_RESULT, events.get(0).type());
        assertTrue((Boolean) events.get(0).metadata().get("is_error"));
    }

    // ─── completion ───────────────────────────────────────────────

    @Test
    @DisplayName("completion exit_code=0 → TASK_COMPLETED + EVIDENCE_VERIFIED")
    void completionSuccessGeneratesTwoEvents() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"completion\",\"exit_code\":0,\"summary\":\"Task done\"}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "completion"), ctx);

        assertEquals(2, events.size());
        assertEquals(EventType.TASK_COMPLETED, events.get(0).type());
        assertEquals(EventType.EVIDENCE_VERIFIED, events.get(1).type());
        assertEquals(0, events.get(0).metadata().get("exit_code"));
    }

    @Test
    @DisplayName("completion exit_code!=0 → TASK_FAILED (no EVIDENCE_VERIFIED on failure)")
    void completionFailureGeneratesTaskFailed() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"completion\",\"exit_code\":1,\"summary\":\"Failed\"}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "completion"), ctx);

        // Failure only generates TASK_FAILED, not EVIDENCE_VERIFIED
        assertEquals(1, events.size());
        assertEquals(EventType.TASK_FAILED, events.get(0).type());
        assertEquals(1, events.get(0).metadata().get("exit_code"));
    }

    // ─── permission_request ───────────────────────────────────────

    @Test
    @DisplayName("permission_request → DECISION_REQUIRES_APPROVAL")
    void permissionRequestMapsToApprovalRequired() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"permission_request\",\"tool_name\":\"Bash\",\"description\":\"Run npm install\",\"request_id\":\"req-123\"}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "permission_request"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.DECISION_REQUIRES_APPROVAL, events.get(0).type());
        assertEquals("Run npm install", events.get(0).metadata().get("description"));
    }

    // ─── session.started ──────────────────────────────────────────

    @Test
    @DisplayName("session.started → PROCESS_STARTED")
    void sessionStartedMapsToProcessStarted() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"session.started\",\"session_id\":\"sess-abc\",\"agent\":\"codex\"}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "session.started"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.PROCESS_STARTED, events.get(0).type());
        assertEquals("sess-abc", events.get(0).metadata().get("session_id"));
    }

    // ─── subagent.start ───────────────────────────────────────────

    @Test
    @DisplayName("subagent.start → AGENT_STARTED")
    void subagentStartMapsToAgentStarted() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"subagent.start\",\"agent\":\"codex-sub\"}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "subagent.start"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.AGENT_STARTED, events.get(0).type());
        assertEquals("codex-sub", events.get(0).metadata().get("subagent"));
    }

    // ─── error ────────────────────────────────────────────────────

    @Test
    @DisplayName("error critical=true → ERROR_CRITICAL")
    void criticalErrorMapsToErrorCritical() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"error\",\"message\":\"Fatal error\",\"critical\":true}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "error"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.ERROR_CRITICAL, events.get(0).type());
        assertEquals("Fatal error", events.get(0).metadata().get("message"));
    }

    @Test
    @DisplayName("error critical=false → ERROR_RECOVERABLE")
    void nonCriticalErrorMapsToErrorRecoverable() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"error\",\"message\":\"Non-fatal error\",\"critical\":false}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "error"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.ERROR_RECOVERABLE, events.get(0).type());
    }

    // ─── unknown event ────────────────────────────────────────────

    @Test
    @DisplayName("unknown event type → AGENT_CHUNK with raw info")
    void unknownEventMapsToAgentChunk() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"custom_event\",\"data\":\"test\"}");
        var events = mapper.map(new EventMapper.CliEvent(null, node, "custom_event"), ctx);

        assertEquals(1, events.size());
        assertEquals(EventType.AGENT_CHUNK, events.get(0).type());
        assertEquals("custom_event", events.get(0).metadata().get("raw_type"));
    }

    // ─── unsupportedEventTypes ────────────────────────────────────

    @Test
    @DisplayName("supportedEventTypes contains all expected types")
    void supportedEventTypesContainsAllExpected() {
        var types = mapper.supportedEventTypes();
        assertTrue(types.contains(EventType.AGENT_CHUNK));
        assertTrue(types.contains(EventType.TOOL_CALLED));
        assertTrue(types.contains(EventType.TOOL_RESULT));
        assertTrue(types.contains(EventType.FILE_CHANGED));
        assertTrue(types.contains(EventType.DECISION_REQUIRES_APPROVAL));
        assertTrue(types.contains(EventType.TASK_COMPLETED));
        assertTrue(types.contains(EventType.TASK_FAILED));
        assertTrue(types.contains(EventType.ERROR_RECOVERABLE));
        assertTrue(types.contains(EventType.ERROR_CRITICAL));
        assertTrue(types.contains(EventType.EVIDENCE_VERIFIED));
        assertTrue(types.contains(EventType.PROCESS_STARTED));
        assertTrue(types.contains(EventType.AGENT_STARTED));
        assertEquals(12, types.size());
    }

    // ─── Edge cases ───────────────────────────────────────────────

    @Test
    @DisplayName("null parsed → empty events")
    void nullParsedReturnsEmpty() {
        var events = mapper.map(new EventMapper.CliEvent("some line", null, "unknown"), ctx);
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("invalid JSON rawLine with null parsed → empty (requires parsed)")
    void invalidJsonWithNullParsedReturnsEmpty() {
        var events = mapper.map(new EventMapper.CliEvent("not valid json{{{", null, null), ctx);
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("raw JSON line in rawLine with null parsed → empty (requires parsed)")
    void rawJsonWithNullParsedReturnsEmpty() throws Exception {
        JsonNode node = MAPPER.readTree("{\"type\":\"terminal_output\",\"text\":\"hello\"}");
        // When parsed is explicitly set to null, mapper returns empty
        var events = mapper.map(new EventMapper.CliEvent(node.toString(), null, "terminal_output"), ctx);
        assertTrue(events.isEmpty());
    }
}
