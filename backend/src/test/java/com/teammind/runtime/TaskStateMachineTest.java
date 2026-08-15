package com.teammind.runtime;

import com.teammind.common.TaskState;
import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import com.teammind.common.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskStateMachineTest {

    private TaskStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new TaskStateMachine(new EventBus(new com.fasterxml.jackson.databind.ObjectMapper()),
                new PolicyEngine());
    }

    @Test
    @DisplayName("新任务初始状态应为 SUBMITTED")
    void shouldStartAsSubmitted() {
        sm.initTask("t-1", "refactor auth", null);
        assertEquals(TaskState.SUBMITTED, sm.getState("t-1"));
    }

    @Test
    @DisplayName("task.started → ORCHESTRATING")
    void submittedToOrchestrating() {
        sm.initTask("t-1", "refactor auth", null);
        var result = sm.handleEvent("t-1", EventType.TASK_STARTED, Map.of());
        assertTrue(result.isPresent());
        assertEquals(TaskState.ORCHESTRATING, result.get());
    }

    @Test
    @DisplayName("routing.decided → EXECUTING")
    void orchestratingToExecuting() {
        sm.initTask("t-1", "refactor auth", null);
        sm.handleEvent("t-1", EventType.TASK_STARTED, Map.of());
        var result = sm.handleEvent("t-1", EventType.ROUTING_DECIDED, Map.of());
        assertTrue(result.isPresent());
        assertEquals(TaskState.EXECUTING, result.get());
    }

    @Test
    @DisplayName("task.completed → VERIFYING")
    void executingToVerifying() {
        sm.initTask("t-1", "refactor auth", null);
        sm.handleEvent("t-1", EventType.TASK_STARTED, Map.of());
        sm.handleEvent("t-1", EventType.ROUTING_DECIDED, Map.of());
        var result = sm.handleEvent("t-1", EventType.TASK_COMPLETED, Map.of());
        assertTrue(result.isPresent());
        assertEquals(TaskState.VERIFYING, result.get());
    }

    @Test
    @DisplayName("evidence.verified → DONE")
    void verifyingToDone() {
        sm.initTask("t-1", "refactor auth", null);
        sm.handleEvent("t-1", EventType.TASK_STARTED, Map.of());
        sm.handleEvent("t-1", EventType.ROUTING_DECIDED, Map.of());
        sm.handleEvent("t-1", EventType.TASK_COMPLETED, Map.of());
        var result = sm.handleEvent("t-1", EventType.EVIDENCE_VERIFIED, Map.of());
        assertTrue(result.isPresent());
        assertEquals(TaskState.DONE, result.get());
    }

    @Test
    @DisplayName("evidence.failed → RETRYING（第一次重试）")
    void verifyingToRetrying() {
        sm.initTask("t-1", "refactor auth", null);
        sm.handleEvent("t-1", EventType.TASK_STARTED, Map.of());
        sm.handleEvent("t-1", EventType.ROUTING_DECIDED, Map.of());
        sm.handleEvent("t-1", EventType.TASK_COMPLETED, Map.of());
        var result = sm.handleEvent("t-1", EventType.EVIDENCE_FAILED,
                Map.of("retryCount", 0, "maxRetries", 3));
        assertTrue(result.isPresent());
        assertEquals(TaskState.RETRYING, result.get());
    }

    @Test
    @DisplayName("RETRYING + task.completed → VERIFYING（重试成功）")
    void retryingToVerifyingOnSuccess() {
        sm.initTask("t-1", "refactor auth", null);
        sm.handleEvent("t-1", EventType.TASK_STARTED, Map.of());
        sm.handleEvent("t-1", EventType.ROUTING_DECIDED, Map.of());
        sm.handleEvent("t-1", EventType.TASK_COMPLETED, Map.of());
        sm.handleEvent("t-1", EventType.EVIDENCE_FAILED, Map.of("retryCount", 0, "maxRetries", 3));
        var result = sm.handleEvent("t-1", EventType.TASK_COMPLETED, Map.of());
        assertTrue(result.isPresent());
        assertEquals(TaskState.VERIFYING, result.get());
    }

    @Test
    @DisplayName("RETRYING + task.failed + maxRetries → ABANDONED")
    void retryingToAbandoned() {
        sm.initTask("t-1", "refactor auth", null);
        sm.handleEvent("t-1", EventType.TASK_STARTED, Map.of());
        sm.handleEvent("t-1", EventType.ROUTING_DECIDED, Map.of());
        sm.handleEvent("t-1", EventType.TASK_COMPLETED, Map.of());
        sm.handleEvent("t-1", EventType.EVIDENCE_FAILED, Map.of("retryCount", 2, "maxRetries", 3));
        var result = sm.handleEvent("t-1", EventType.TASK_FAILED, Map.of("retryCount", 3, "maxRetries", 3));
        assertTrue(result.isPresent());
        assertEquals(TaskState.ABANDONED, result.get());
    }

    @Test
    @DisplayName("finding.created (CRITICAL) → NEEDS_APPROVAL")
    void findingCriticalToNeedsApproval() {
        sm.initTask("t-1", "refactor auth", null);
        sm.handleEvent("t-1", EventType.TASK_STARTED, Map.of());
        sm.handleEvent("t-1", EventType.ROUTING_DECIDED, Map.of());
        var result = sm.handleEvent("t-1", EventType.FINDING_CREATED,
                Map.of("severity", "CRITICAL"));
        assertTrue(result.isPresent());
        assertEquals(TaskState.NEEDS_APPROVAL, result.get());
    }

    @Test
    @DisplayName("approval.granted → APPROVED → DONE")
    void approvalGrantedLeadsToDone() {
        sm.initTask("t-1", "refactor auth", null);
        sm.handleEvent("t-1", EventType.TASK_STARTED, Map.of());
        sm.handleEvent("t-1", EventType.ROUTING_DECIDED, Map.of());
        sm.handleEvent("t-1", EventType.FINDING_CREATED, Map.of("severity", "CRITICAL"));
        sm.handleEvent("t-1", EventType.APPROVAL_GRANTED, Map.of());
        var result = sm.handleEvent("t-1", EventType.TASK_COMPLETED, Map.of());
        assertTrue(result.isPresent());
        assertEquals(TaskState.VERIFYING, result.get());

        // 验证通过
        var done = sm.handleEvent("t-1", EventType.EVIDENCE_VERIFIED, Map.of());
        assertTrue(done.isPresent());
        assertEquals(TaskState.DONE, done.get());
    }

    @Test
    @DisplayName("任意状态下 task.cancelled → CANCELLED")
    void canCancelFromAnyState() {
        sm.initTask("t-1", "refactor auth", null);
        sm.handleEvent("t-1", EventType.TASK_STARTED, Map.of());
        sm.handleEvent("t-1", EventType.ROUTING_DECIDED, Map.of());

        sm.cancel("t-1");
        assertEquals(TaskState.CANCELLED, sm.getState("t-1"));
    }

    @Test
    @DisplayName("未知任务ID的handleEvent返回empty")
    void unknownTaskReturnsEmpty() {
        var result = sm.handleEvent("nonexistent", EventType.TASK_STARTED, Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("同一状态重复收到无关事件不改变状态")
    void irrelevantEventDoesNotChangeState() {
        sm.initTask("t-1", "refactor auth", null);
        sm.handleEvent("t-1", EventType.TASK_STARTED, Map.of());

        // 在 ORCHESTRATING 状态下收到 agent.chunk 不应改变状态
        var result = sm.handleEvent("t-1", EventType.AGENT_CHUNK, Map.of());
        assertTrue(result.isEmpty());
        assertEquals(TaskState.ORCHESTRATING, sm.getState("t-1"));
    }
}
