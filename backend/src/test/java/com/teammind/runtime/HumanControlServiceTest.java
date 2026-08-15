package com.teammind.runtime;

import com.teammind.common.TaskExecutionState;
import com.teammind.common.TaskState;
import com.teammind.entity.TaskExecution;
import com.teammind.repository.TaskExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class HumanControlServiceTest {

    private TaskExecutionStateMachine stateMachine;
    private HumanControlService service;

    @BeforeEach
    void setUp() {
        stateMachine = new TaskExecutionStateMachine();
        // HumanControlService needs repos — use minimal mock approach
        // We'll test via stateMachine directly since repos are Spring beans
    }

    @Test
    @DisplayName("pause: RUNNING → PAUSE_REQUESTED")
    void testPauseTransition() {
        TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
        boolean canPause = stateMachine.canTransition(exec, "pauseRequested");
        assertThat(canPause).isTrue();

        stateMachine.transition(exec, "pauseRequested");
        assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.PAUSE_REQUESTED);
    }

    @Test
    @DisplayName("pauseComplete: PAUSE_REQUESTED → PAUSED")
    void testPauseCompleteTransition() {
        TaskExecution exec = buildExecution(TaskExecutionState.PAUSE_REQUESTED);
        stateMachine.transition(exec, "pauseComplete");
        assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.PAUSED);
    }

    @Test
    @DisplayName("resume: PAUSED → RUNNING")
    void testResumeTransition() {
        TaskExecution exec = buildExecution(TaskExecutionState.PAUSED);
        stateMachine.transition(exec, "resume");
        assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.RUNNING);
    }

    @Test
    @DisplayName("cancel: RUNNING → CANCELLED")
    void testCancelTransition() {
        TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
        stateMachine.transition(exec, "cancel");
        assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.CANCELLED);
    }

    @Test
    @DisplayName("retry: FAILED → RETRYING")
    void testRetryTransition() {
        TaskExecution exec = buildExecution(TaskExecutionState.FAILED);
        stateMachine.transition(exec, "retry");
        assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.RETRYING);
    }

    @Test
    @DisplayName("approve: NEEDS_APPROVAL → APPROVING → RUNNING")
    void testApproveTransition() {
        TaskExecution exec = buildExecution(TaskExecutionState.NEEDS_APPROVAL);
        stateMachine.transition(exec, "approve");
        assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.APPROVING);

        stateMachine.transition(exec, "approvalProceed");
        assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.RUNNING);
    }

    @Test
    @DisplayName("deny: NEEDS_APPROVAL → ABANDONED")
    void testDenyTransition() {
        TaskExecution exec = buildExecution(TaskExecutionState.NEEDS_APPROVAL);
        stateMachine.transition(exec, "deny");
        assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.ABANDONED);
    }

    @Test
    @DisplayName("reject pause from terminal states")
    void testPauseRejectedFromDone() {
        TaskExecution exec = buildExecution(TaskExecutionState.DONE);
        assertThatThrownBy(() -> stateMachine.transition(exec, "pauseRequested"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reject cancel from terminal states")
    void testCancelRejectedFromDone() {
        TaskExecution exec = buildExecution(TaskExecutionState.DONE);
        assertThatThrownBy(() -> stateMachine.transition(exec, "cancel"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reject retry from non-failed state")
    void testRetryRejectedFromRunning() {
        TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
        assertThatThrownBy(() -> stateMachine.transition(exec, "retry"))
                .isInstanceOf(IllegalStateException.class);
    }

    private TaskExecution buildExecution(TaskExecutionState state) {
        return TaskExecution.builder()
                .id("exec-test")
                .projectId("proj-1")
                .state(TaskState.EXECUTING)
                .executionState(state)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
