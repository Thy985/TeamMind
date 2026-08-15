package com.teammind.event;

import com.teammind.common.EventType;
import java.util.Map;

/**
 * AgentChunk 事件 — Agent 流式输出片段
 *
 * 使用组合而非继承（TeamMindEvent 是 record，不可被 extends）
 */
public class AgentChunkEvent {
    private final TeamMindEvent base;
    private final String content;
    private final Boolean isFinal;
    private final Integer tokenCount;

    public AgentChunkEvent(String taskId, String pluginId, String role,
                           String content, Boolean isFinal) {
        this.base = TeamMindEvent.of(EventType.AGENT_CHUNK, taskId, pluginId, role,
                Map.of("content", content, "isFinal", isFinal != null ? isFinal : false));
        this.content = content;
        this.isFinal = isFinal;
        this.tokenCount = null;
    }

    public TeamMindEvent toEvent() { return base; }
    public String content() { return content; }
    public Boolean isFinal() { return isFinal; }
    public Integer tokenCount() { return tokenCount; }
}
