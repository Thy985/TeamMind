package com.teammind.event;

import com.teammind.common.EventType;

import java.util.List;

/**
 * 事件映射器接口
 *
 * 每个 CLI Adapter 实现此接口，将自己的原始输出转换成 TeamMind 标准事件。
 * 前端只认识 TeamMindEvent，不知道任何 CLI 格式。
 *
 * 示例：
 *   ClaudeCodeEventMapper 把 {"type":"assistant","message":...} 转换成 agent.chunk
 *   CodexEventMapper      把 {"type":"item.completed",...} 转换成 tool.called
 */
public interface EventMapper {
    /**
     * 将 CLI 原始事件映射为 TeamMind 标准事件列表
     * 一次原始事件可能产生多个标准事件（如流式 chunk 序列）
     *
     * @param rawEvent 原始 CLI 事件（文本行或已解析的 JSON）
     * @param context  映射上下文（taskId, pluginId, role 等）
     * @return 转换后的 TeamMind 事件列表（可能为空）
     */
    List<TeamMindEvent> map(CliEvent rawEvent, MapContext context);

    /**
     * 返回该 mapper 支持的事件类型集合
     */
    List<EventType> supportedEventTypes();

    /**
     * CLI 原始事件（每个 Adapter 定义自己的格式）
     */
    record CliEvent(String rawLine, Object parsed, String eventTypeHint) {}

    /**
     * 映射上下文 — 提供 taskId / pluginId 等公共字段
     */
    record MapContext(String taskId, String pluginId, String role) {}
}
