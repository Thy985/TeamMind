package com.teammind.event.mapper;

import com.teammind.common.EventType;
import com.teammind.event.EventMapper;
import com.teammind.event.TeamMindEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodexEventMapperTest {

    private final CodexEventMapper mapper = new CodexEventMapper();
    private final EventMapper.MapContext ctx = new EventMapper.MapContext("t-1", "codex", "LEAD");

    @Test
    @DisplayName(">>> 前缀映射为 TOOL_CALLED")
    void stepLineMapsToToolCalled() {
        var events = mapper.map(new EventMapper.CliEvent(">>> STEP: Read file", null, "step"), ctx);
        assertEquals(1, events.size());
        assertEquals(EventType.TOOL_CALLED, events.get(0).type());
    }

    @Test
    @DisplayName("✓ Done 映射为 EVIDENCE_VERIFIED")
    void doneMapsToEvidenceVerified() {
        var events = mapper.map(new EventMapper.CliEvent("✓ Done", null, "done"), ctx);
        assertEquals(1, events.size());
        assertEquals(EventType.EVIDENCE_VERIFIED, events.get(0).type());
    }

    @Test
    @DisplayName("✗ Error 映射为 TOOL_RESULT")
    void errorMapsToToolResult() {
        var events = mapper.map(new EventMapper.CliEvent("✗ Error: permission denied", null, "error"), ctx);
        assertEquals(1, events.size());
        assertEquals(EventType.TOOL_RESULT, events.get(0).type());
    }

    @Test
    @DisplayName("普通文本映射为 AGENT_CHUNK")
    void plainTextMapsToAgentChunk() {
        var events = mapper.map(new EventMapper.CliEvent("Generating code...", null, "text"), ctx);
        assertEquals(1, events.size());
        assertEquals(EventType.AGENT_CHUNK, events.get(0).type());
    }

    @Test
    @DisplayName("空行返回空列表")
    void emptyLineReturnsEmpty() {
        assertTrue(mapper.map(new EventMapper.CliEvent("", null, null), ctx).isEmpty());
        assertTrue(mapper.map(new EventMapper.CliEvent(null, null, null), ctx).isEmpty());
    }

    @Test
    @DisplayName("supportedEventTypes 包含所有预期类型")
    void supportedEventTypes() {
        var types = mapper.supportedEventTypes();
        assertTrue(types.contains(EventType.AGENT_CHUNK));
        assertTrue(types.contains(EventType.TOOL_CALLED));
        assertTrue(types.contains(EventType.EVIDENCE_VERIFIED));
        assertTrue(types.contains(EventType.TOOL_RESULT));
        assertEquals(4, types.size());
    }
}
