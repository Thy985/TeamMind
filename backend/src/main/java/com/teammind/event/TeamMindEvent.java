package com.teammind.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.teammind.common.EventType;

import java.util.Map;

/**
 * TeamMind 统一事件模型
 *
 * 所有 CLI Adapter 把自己的行为映射成这个标准事件。
 * 前端通过 WebSocket 消费这套协议，不需要知道任何 CLI 格式。
 *
 * 协议版本常量：TEAMMIND_EVENT_PROTOCOL_VERSION = 1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamMindEvent(
        /** 事件类型（40+ 种） */
        EventType type,

        /** 事件时间戳（epoch ms） */
        long timestamp,

        /** 任务 ID */
        @JsonProperty("task_id")
        String taskId,

        /** 步骤 ID（子事件时填充） */
        @JsonProperty("step_id")
        String stepId,

        /** 触发事件的 Agent Plugin ID */
        @JsonProperty("plugin_id")
        String pluginId,

        /** Agent ID（兼容别名，同 pluginId） */
        @JsonProperty("agent_id")
        String agentId,

        /** Agent 角色 */
        String role,

        /** 附加元数据（各事件类型不同） */
        Map<String, Object> metadata
) {
    /** 协议版本号 */
    public static final int TEAMMIND_EVENT_PROTOCOL_VERSION = 1;

    /** 创建工具方法：自动生成时间戳 */
    public static TeamMindEvent of(EventType type, String taskId, String pluginId,
                                    String role, Map<String, Object> metadata) {
        return new TeamMindEvent(type, System.currentTimeMillis(), taskId, null,
                pluginId, pluginId, role, metadata);
    }

    /** 快捷构造：无 metadata */
    public static TeamMindEvent of(EventType type, String taskId, String pluginId, String role) {
        return of(type, taskId, pluginId, role, Map.of());
    }
}
