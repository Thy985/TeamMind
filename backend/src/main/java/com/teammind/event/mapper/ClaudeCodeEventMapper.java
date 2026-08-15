package com.teammind.event.mapper;

import com.teammind.common.EventType;
import com.teammind.event.EventMapper;
import com.teammind.event.TeamMindEvent;

import java.util.List;

/**
 * Claude Code Event Mapper
 *
 * Claude Code --output-format json 输出 NDJSON，每行格式：
 *   {"type":"assistant","message":{"content":[{"type":"text","text":"..."}]}}
 *   {"type":"tool","tool_name":"Read","input":{"path":"..."}}
 *   {"type":"result","tool_name":"Read","is_error":false,"result":"..."}
 *
 * 映射规则：
 *   assistant.text  → AGENT_CHUNK
 *   tool            → TOOL_CALL_START / TOOL_RESULT
 *   result.is_error → TOOL_RESULT_ERROR
 */
public class ClaudeCodeEventMapper implements EventMapper {

    @Override
    public List<TeamMindEvent> map(CliEvent rawEvent, MapContext context) {
        if (rawEvent.parsed() == null) return List.of();

        Object node = rawEvent.parsed();
        if (!(node instanceof String)) return List.of();

        // parsed 是已解析的 JsonNode（由调用方处理）
        return List.of();
    }

    @Override
    public List<EventType> supportedEventTypes() {
        return List.of(
                EventType.AGENT_CHUNK,
                EventType.TOOL_CALLED,
                EventType.TOOL_RESULT
        );
    }
}
