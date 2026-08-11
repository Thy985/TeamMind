package com.teammind.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 事件发布器
 * 
 * 用于向客户端推送实时事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WSEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 发布任务启动事件
     */
    public void publishMissionStarted(String missionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("missionId", missionId);
        publish(WSEvent.of(WSEvent.MISSION_STARTED, missionId, payload));
    }

    /**
     * 发布任务完成事件
     */
    public void publishMissionCompleted(String missionId, Map<String, Object> result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("missionId", missionId);
        payload.put("result", result);
        publish(WSEvent.of(WSEvent.MISSION_COMPLETED, missionId, payload));
    }

    /**
     * 发布任务失败事件
     */
    public void publishMissionFailed(String missionId, String error) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("missionId", missionId);
        payload.put("error", error);
        publish(WSEvent.of(WSEvent.MISSION_FAILED, missionId, payload));
    }

    /**
     * 发布任务失败事件（无错误信息重载）
     */
    public void publishMissionFailed(String missionId) {
        publishMissionFailed(missionId, null);
    }

    /**
     * 发布 Agent 创建事件
     */
    public void publishAgentSpawned(String missionId, String agentId, String agentName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", agentId);
        payload.put("agentName", agentName);
        publish(WSEvent.of(WSEvent.AGENT_SPAWNED, missionId, payload));
    }

    /**
     * 发布 Agent 状态更新事件
     */
    public void publishAgentStatusUpdate(String missionId, String agentId, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", agentId);
        payload.put("status", status);
        publish(WSEvent.of(WSEvent.AGENT_STATUS_UPDATE, missionId, payload));
    }

    /**
     * 发布节点更新事件
     */
    public void publishNodeUpdate(String missionId, String nodeId, Map<String, Object> nodeData) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("data", nodeData);
        publish(WSEvent.of(WSEvent.NODE_UPDATE, missionId, payload));
    }

    /**
     * 发布日志事件
     */
    public void publishLog(String missionId, String type, String agentId, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("agentId", agentId);
        payload.put("message", message);
        publish(WSEvent.of(WSEvent.LOG, missionId, payload));
    }

    /**
     * 发布进化触发事件
     */
    public void publishEvolutionTriggered(String agentId, String evolutionType, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", agentId);
        payload.put("evolutionType", evolutionType);
        payload.put("reason", reason);
        publish(WSEvent.of(WSEvent.EVOLUTION_TRIGGERED, null, payload));
    }

    /**
     * 发布进化完成事件
     */
    public void publishEvolutionCompleted(String agentId, String evolutionType, Map<String, Object> result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("agentId", agentId);
        payload.put("evolutionType", evolutionType);
        payload.put("result", result);
        publish(WSEvent.of(WSEvent.EVOLUTION_COMPLETED, null, payload));
    }

    /**
     * 发布决议投票更新事件
     */
    public void publishResolutionVote(String missionId, String resolutionId, String agentId, String optionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("resolutionId", resolutionId);
        payload.put("agentId", agentId);
        payload.put("optionId", optionId);
        publish(WSEvent.of(WSEvent.RESOLUTION_REQUIRED, missionId, payload));
    }

    /**
     * 发布决议已解决事件
     */
    public void publishResolutionResolved(String missionId, String resolutionId, String optionId, Map<String, Object> votes) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("resolutionId", resolutionId);
        payload.put("optionId", optionId);
        payload.put("votes", votes);
        publish(WSEvent.of(WSEvent.RESOLUTION_RESOLVED, missionId, payload));
    }

    /**
     * 发布事件到所有订阅者
     */
    private void publish(WSEvent event) {
        log.debug("Publishing WebSocket event: {}", event.getType());
        
        // 广播到所有客户端
        messagingTemplate.convertAndSend("/topic/events", event);
        
        // 如果有 missionId，发送到特定任务频道
        if (event.getMissionId() != null) {
            messagingTemplate.convertAndSend("/topic/missions/" + event.getMissionId(), event);
        }
    }
}
