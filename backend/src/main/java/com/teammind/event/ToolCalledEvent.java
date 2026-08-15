package com.teammind.event;

import com.teammind.common.EventType;
import java.util.Map;

/**
 * ToolCalled 事件 — Agent 调用了某个工具
 */
public class ToolCalledEvent {
    private final TeamMindEvent base;
    private final String toolName;
    private final Map<String, Object> input;
    private final String reason;

    public ToolCalledEvent(String taskId, String pluginId, String role,
                           String toolName, Map<String, Object> input, String reason) {
        this.base = TeamMindEvent.of(EventType.TOOL_CALLED, taskId, pluginId, role,
                Map.of("toolName", toolName, "input", input != null ? input : Map.of(),
                        "reason", reason != null ? reason : ""));
        this.toolName = toolName;
        this.input = input;
        this.reason = reason;
    }

    public TeamMindEvent toEvent() { return base; }
    public String toolName() { return toolName; }
    public Map<String, Object> input() { return input; }
    public String reason() { return reason; }
}
