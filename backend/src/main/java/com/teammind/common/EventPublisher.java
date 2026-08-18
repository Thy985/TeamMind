package com.teammind.common;

import java.util.Map;

/**
 * Runtime 事件发布器 SPI
 *
 * 架构不变量：Runtime Core 依赖此接口，不依赖任何 Web/WebSocket 实现。
 * Host（Spring Boot / CLI / Tauri）提供具体实现。
 *
 * 已知实现：
 *   - WSEventPublisher（Spring WebSocket STOMP）
 *   - NoOpEventPublisher（Headless / CLI / Test）
 *   - 未来：ConsoleEventPublisher, FileEventPublisher, RemoteEventPublisher
 */
public interface EventPublisher {

    void publishMissionStarted(String missionId);

    void publishMissionCompleted(String missionId, Map<String, Object> result);

    void publishMissionFailed(String missionId, String error);

    void publishMissionFailed(String missionId);

    void publishAgentSpawned(String missionId, String agentId, String agentName);

    void publishAgentStatusUpdate(String missionId, String agentId, String status);

    void publishNodeUpdate(String missionId, String nodeId, Map<String, Object> nodeData);

    void publishLog(String missionId, String type, String agentId, String message);

    void publishEvolutionTriggered(String agentId, String evolutionType, String reason);

    void publishEvolutionCompleted(String agentId, String evolutionType, Map<String, Object> result);

    void publishStateUpdate(String taskId, Map<String, Object> snapshot);

    void publishApprovalRequired(String taskId, Map<String, Object> approval);

    void publishStepStarted(String taskId, String stepName, String agentId);

    void publishStepCompleted(String taskId, String stepName, String agentId, boolean success);

    void publishResolutionVote(String missionId, String resolutionId, String agentId, String optionId);

    void publishResolutionResolved(String missionId, String resolutionId, String optionId, Map<String, Object> votes);
}