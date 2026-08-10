package com.teammind.websocket;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebSocket 事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WSEvent {

    /**
     * 事件类型
     */
    private String type;

    /**
     * 关联的任务 ID
     */
    private String missionId;

    /**
     * 时间戳
     */
    private String timestamp;

    /**
     * 事件载荷
     */
    private Map<String, Object> payload;

    /**
     * 事件类型常量
     */
    public static final String MISSION_STARTED = "mission_started";
    public static final String MISSION_COMPLETED = "mission_completed";
    public static final String MISSION_FAILED = "mission_failed";
    public static final String AGENT_SPAWNED = "agent_spawned";
    public static final String AGENT_STATUS_UPDATE = "agent_status_update";
    public static final String NODE_UPDATE = "node_update";
    public static final String LOG = "log";
    public static final String RESOLUTION_REQUIRED = "resolution_required";
    public static final String RESOLUTION_RESOLVED = "resolution_resolved";
    public static final String EVOLUTION_TRIGGERED = "evolution_triggered";
    public static final String EVOLUTION_COMPLETED = "evolution_completed";

    /**
     * 创建事件
     */
    public static WSEvent of(String type, String missionId, Map<String, Object> payload) {
        return WSEvent.builder()
                .type(type)
                .missionId(missionId)
                .timestamp(LocalDateTime.now().toString())
                .payload(payload)
                .build();
    }
}
