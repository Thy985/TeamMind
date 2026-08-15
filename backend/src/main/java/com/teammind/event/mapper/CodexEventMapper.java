package com.teammind.event.mapper;

import com.teammind.common.EventType;
import com.teammind.event.EventMapper;
import com.teammind.event.TeamMindEvent;

import java.util.List;
import java.util.Map;

/**
 * Codex CLI Event Mapper
 *
 * Codex 输出格式（stdout）：
 *   >>> STEP: Read file src/main.java
 *   >>> STEP: Write file src/main.java
 *   ✓ Done
 *   ✗ Error: ...
 *
 * 映射规则：
 *   >>> ...       → TOOL_CALL_START
 *   ✓ Done        → EVIDENCE_VERIFIED
 *   ✗ Error       → TOOL_RESULT_ERROR
 *   其他文本      → AGENT_CHUNK
 */
public class CodexEventMapper implements EventMapper {

    @Override
    public List<TeamMindEvent> map(CliEvent rawEvent, MapContext context) {
        if (rawEvent.rawLine() == null || rawEvent.rawLine().isBlank()) {
            return List.of();
        }

        String line = rawEvent.rawLine().trim();
        String taskId = context.taskId();
        String pluginId = context.pluginId();

        if (line.startsWith(">>>")) {
            // Step 开始
            return List.of(TeamMindEvent.of(EventType.TOOL_CALLED, taskId, pluginId, "LEAD",
                    Map.of("step", line, "raw", rawEvent.rawLine())));
        }

        if (line.contains("Done") || line.equals("✓")) {
            return List.of(TeamMindEvent.of(EventType.EVIDENCE_VERIFIED, taskId, pluginId, "LEAD",
                    Map.of("status", "completed")));
        }

        if (line.startsWith("✗") || line.toLowerCase().contains("error")) {
            return List.of(TeamMindEvent.of(EventType.TOOL_RESULT, taskId, pluginId, "LEAD",
                    Map.of("error", line)));
        }

        if (!line.isEmpty()) {
            return List.of(TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, pluginId, "LEAD",
                    Map.of("content", line)));
        }

        return List.of();
    }

    @Override
    public List<EventType> supportedEventTypes() {
        return List.of(
                EventType.AGENT_CHUNK,
                EventType.TOOL_CALLED,
                EventType.EVIDENCE_VERIFIED,
                EventType.TOOL_RESULT
        );
    }
}
