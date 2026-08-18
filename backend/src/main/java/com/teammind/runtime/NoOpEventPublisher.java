package com.teammind.runtime;

import com.teammind.common.EventPublisher;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * NoOpEventPublisher — Headless / CLI / Test 实现
 *
 * 不推送任何事件，仅记录 debug 日志。
 * 用于不启动 Spring WebSocket 容器的场景（CLI Host、单元测试、RuntimeLauncher）。
 */
@Slf4j
public class NoOpEventPublisher implements EventPublisher {

    @Override
    public void publishMissionStarted(String missionId) {
        log.debug("[NoOp] mission started: {}", missionId);
    }

    @Override
    public void publishMissionCompleted(String missionId, Map<String, Object> result) {
        log.debug("[NoOp] mission completed: {}", missionId);
    }

    @Override
    public void publishMissionFailed(String missionId, String error) {
        log.debug("[NoOp] mission failed: {} — {}", missionId, error);
    }

    @Override
    public void publishMissionFailed(String missionId) {
        publishMissionFailed(missionId, null);
    }

    @Override
    public void publishAgentSpawned(String missionId, String agentId, String agentName) {
        log.debug("[NoOp] agent spawned: {}/{}", missionId, agentId);
    }

    @Override
    public void publishAgentStatusUpdate(String missionId, String agentId, String status) {
        log.debug("[NoOp] agent status: {}/{} → {}", missionId, agentId, status);
    }

    @Override
    public void publishNodeUpdate(String missionId, String nodeId, Map<String, Object> nodeData) {
        log.debug("[NoOp] node update: {}/{}", missionId, nodeId);
    }

    @Override
    public void publishLog(String missionId, String type, String agentId, String message) {
        log.debug("[NoOp] log: {}/{}/{}/{}", missionId, type, agentId, message);
    }

    @Override
    public void publishEvolutionTriggered(String agentId, String evolutionType, String reason) {
        log.debug("[NoOp] evolution triggered: {}/{}", agentId, evolutionType);
    }

    @Override
    public void publishEvolutionCompleted(String agentId, String evolutionType, Map<String, Object> result) {
        log.debug("[NoOp] evolution completed: {}/{}", agentId, evolutionType);
    }

    @Override
    public void publishStateUpdate(String taskId, Map<String, Object> snapshot) {
        log.debug("[NoOp] state update: {}", taskId);
    }

    @Override
    public void publishApprovalRequired(String taskId, Map<String, Object> approval) {
        log.debug("[NoOp] approval required: {}", taskId);
    }

    @Override
    public void publishStepStarted(String taskId, String stepName, String agentId) {
        log.debug("[NoOp] step started: {}/{} by {}", taskId, stepName, agentId);
    }

    @Override
    public void publishStepCompleted(String taskId, String stepName, String agentId, boolean success) {
        log.debug("[NoOp] step completed: {}/{} success={}", taskId, stepName, success);
    }

    @Override
    public void publishResolutionVote(String missionId, String resolutionId, String agentId, String optionId) {
        log.debug("[NoOp] resolution vote: {}/{}", missionId, resolutionId);
    }

    @Override
    public void publishResolutionResolved(String missionId, String resolutionId, String optionId, Map<String, Object> votes) {
        log.debug("[NoOp] resolution resolved: {}/{}", missionId, resolutionId);
    }
}