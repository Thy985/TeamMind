package com.teammind.runtime;

import com.teammind.common.TaskExecutionState;
import com.teammind.entity.TaskExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskExecutionStateMachineTest {

    private TaskExecutionStateMachine machine;

    @BeforeEach
    void setUp() {
        machine = new TaskExecutionStateMachine();
    }

    private TaskExecution buildExecution(TaskExecutionState state) {
        return TaskExecution.builder()
                .id("exec-001")
                .projectId("proj-1")
                .state(com.teammind.common.TaskState.EXECUTING)
                .executionState(state)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ─── submit ────────────────────────────────────────────────

    @Nested
    @DisplayName("submit: NEW → PENDING")
    class SubmitTests {

        @Test
        void shouldTransitionNewToPending() {
            TaskExecution exec = buildExecution(TaskExecutionState.NEW);
            TaskExecutionState result = machine.transition(exec, "submit");
            assertThat(result).isEqualTo(TaskExecutionState.PENDING);
        }

        @Test
        void shouldRejectSubmitFromRunning() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
            assertThatThrownBy(() -> machine.transition(exec, "submit"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─── start ─────────────────────────────────────────────────

    @Nested
    @DisplayName("start: PENDING → RUNNING")
    class StartTests {

        @Test
        void shouldTransitionPendingToRunning() {
            TaskExecution exec = buildExecution(TaskExecutionState.PENDING);
            TaskExecutionState result = machine.transition(exec, "start");
            assertThat(result).isEqualTo(TaskExecutionState.RUNNING);
            assertThat(exec.getStartedAt()).isNotNull();
        }

        @Test
        void shouldNotOverwriteStartedAt() {
            TaskExecution exec = buildExecution(TaskExecutionState.PENDING);
            LocalDateTime original = LocalDateTime.now().minusHours(1);
            exec.setStartedAt(original);

            machine.transition(exec, "start");
            assertThat(exec.getStartedAt()).isEqualTo(original);
        }
    }

    // ─── pauseRequested ────────────────────────────────────────

    @Nested
    @DisplayName("pauseRequested: RUNNING → PAUSE_REQUESTED")
    class PauseRequestedTests {

        @Test
        void shouldTransitionRunningToPauseRequested() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
            TaskExecutionState result = machine.transition(exec, "pauseRequested");
            assertThat(result).isEqualTo(TaskExecutionState.PAUSE_REQUESTED);
        }

        @Test
        void shouldRejectPauseFromDone() {
            TaskExecution exec = buildExecution(TaskExecutionState.DONE);
            assertThatThrownBy(() -> machine.transition(exec, "pauseRequested"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─── pauseComplete ─────────────────────────────────────────

    @Nested
    @DisplayName("pauseComplete: PAUSE_REQUESTED → PAUSED")
    class PauseCompleteTests {

        @Test
        void shouldTransitionPauseRequestedToPaused() {
            TaskExecution exec = buildExecution(TaskExecutionState.PAUSE_REQUESTED);
            TaskExecutionState result = machine.transition(exec, "pauseComplete");
            assertThat(result).isEqualTo(TaskExecutionState.PAUSED);
        }
    }

    // ─── resume ────────────────────────────────────────────────

    @Nested
    @DisplayName("resume: PAUSED → RUNNING")
    class ResumeTests {

        @Test
        void shouldTransitionPausedToRunning() {
            TaskExecution exec = buildExecution(TaskExecutionState.PAUSED);
            TaskExecutionState result = machine.transition(exec, "resume");
            assertThat(result).isEqualTo(TaskExecutionState.RUNNING);
        }
    }

    // ─── complete ──────────────────────────────────────────────

    @Nested
    @DisplayName("complete: RUNNING → DONE")
    class CompleteTests {

        @Test
        void shouldTransitionRunningToDone() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
            TaskExecutionState result = machine.transition(exec, "complete");
            assertThat(result).isEqualTo(TaskExecutionState.DONE);
            assertThat(exec.getCompletedAt()).isNotNull();
        }
    }

    // ─── fail ──────────────────────────────────────────────────

    @Nested
    @DisplayName("fail: RUNNING → FAILED")
    class FailTests {

        @Test
        void shouldTransitionRunningToFailed() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
            TaskExecutionState result = machine.transition(exec, "fail");
            assertThat(result).isEqualTo(TaskExecutionState.FAILED);
        }
    }

    // ─── needsApproval ─────────────────────────────────────────

    @Nested
    @DisplayName("needsApproval: RUNNING → NEEDS_APPROVAL")
    class NeedsApprovalTests {

        @Test
        void shouldTransitionRunningToNeedsApproval() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
            TaskExecutionState result = machine.transition(exec, "needsApproval");
            assertThat(result).isEqualTo(TaskExecutionState.NEEDS_APPROVAL);
        }
    }

    // ─── approve / deny ────────────────────────────────────────

    @Nested
    @DisplayName("approve/deny: NEEDS_APPROVAL branch")
    class ApprovalTests {

        @Test
        void shouldTransitionNeedsApprovalToApproving() {
            TaskExecution exec = buildExecution(TaskExecutionState.NEEDS_APPROVAL);
            TaskExecutionState result = machine.transition(exec, "approve");
            assertThat(result).isEqualTo(TaskExecutionState.APPROVING);
        }

        @Test
        void shouldTransitionApprovingToRunning() {
            TaskExecution exec = buildExecution(TaskExecutionState.APPROVING);
            TaskExecutionState result = machine.transition(exec, "approvalProceed");
            assertThat(result).isEqualTo(TaskExecutionState.RUNNING);
        }

        @Test
        void shouldTransitionNeedsApprovalToAbandoned() {
            TaskExecution exec = buildExecution(TaskExecutionState.NEEDS_APPROVAL);
            TaskExecutionState result = machine.transition(exec, "deny");
            assertThat(result).isEqualTo(TaskExecutionState.ABANDONED);
        }
    }

    // ─── retry ─────────────────────────────────────────────────

    @Nested
    @DisplayName("retry: FAILED → RETRYING → PENDING")
    class RetryTests {

        @Test
        void shouldTransitionFailedToRetrying() {
            TaskExecution exec = buildExecution(TaskExecutionState.FAILED);
            TaskExecutionState result = machine.transition(exec, "retry");
            assertThat(result).isEqualTo(TaskExecutionState.RETRYING);
        }

        @Test
        void shouldTransitionRetryingToPending() {
            TaskExecution exec = buildExecution(TaskExecutionState.RETRYING);
            TaskExecutionState result = machine.transition(exec, "startRetry");
            assertThat(result).isEqualTo(TaskExecutionState.PENDING);
        }

        @Test
        void shouldNotAllowRetryFromDone() {
            TaskExecution exec = buildExecution(TaskExecutionState.DONE);
            assertThatThrownBy(() -> machine.transition(exec, "retry"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─── cancel ────────────────────────────────────────────────

    @Nested
    @DisplayName("cancel: ANY → CANCELLED")
    class CancelTests {

        @Test
        void shouldCancelFromRunning() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
            TaskExecutionState result = machine.transition(exec, "cancel");
            assertThat(result).isEqualTo(TaskExecutionState.CANCELLED);
        }

        @Test
        void shouldCancelFromPending() {
            TaskExecution exec = buildExecution(TaskExecutionState.PENDING);
            TaskExecutionState result = machine.transition(exec, "cancel");
            assertThat(result).isEqualTo(TaskExecutionState.CANCELLED);
        }

        @Test
        void shouldCancelFromPauseRequested() {
            TaskExecution exec = buildExecution(TaskExecutionState.PAUSE_REQUESTED);
            TaskExecutionState result = machine.transition(exec, "cancel");
            assertThat(result).isEqualTo(TaskExecutionState.CANCELLED);
        }

        @Test
        void shouldCancelFromPaused() {
            TaskExecution exec = buildExecution(TaskExecutionState.PAUSED);
            TaskExecutionState result = machine.transition(exec, "cancel");
            assertThat(result).isEqualTo(TaskExecutionState.CANCELLED);
        }

        @Test
        void shouldCancelFromNeedsApproval() {
            TaskExecution exec = buildExecution(TaskExecutionState.NEEDS_APPROVAL);
            TaskExecutionState result = machine.transition(exec, "cancel");
            assertThat(result).isEqualTo(TaskExecutionState.CANCELLED);
        }

        @Test
        void shouldCancelFromRetrying() {
            TaskExecution exec = buildExecution(TaskExecutionState.RETRYING);
            TaskExecutionState result = machine.transition(exec, "cancel");
            assertThat(result).isEqualTo(TaskExecutionState.CANCELLED);
        }

        @Test
        void shouldNotCancelTerminalStates() {
            for (TaskExecutionState terminal : List.of(
                    TaskExecutionState.DONE, TaskExecutionState.CANCELLED, TaskExecutionState.ABANDONED)) {
                TaskExecution exec = buildExecution(terminal);
                assertThatThrownBy(() -> machine.transition(exec, "cancel"))
                        .isInstanceOf(IllegalStateException.class)
                        .withFailMessage("Cancel should be rejected from terminal state: %s", terminal);
            }
        }
    }

    // ─── canTransition ─────────────────────────────────────────

    @Nested
    @DisplayName("canTransition checks")
    class CanTransitionTests {

        @Test
        void shouldAllowStartFromPending() {
            TaskExecution exec = buildExecution(TaskExecutionState.PENDING);
            assertThat(machine.canTransition(exec, "start")).isTrue();
        }

        @Test
        void shouldRejectInvalidCommand() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
            assertThat(machine.canTransition(exec, "invalidCommand")).isFalse();
        }
    }

    // ─── getAvailableCommands ──────────────────────────────────

    @Nested
    @DisplayName("getAvailableCommands")
    class AvailableCommandsTests {

        @Test
        void shouldReturnCommandsForRunning() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
            Set<String> cmds = machine.getAvailableCommands(exec);
            assertThat(cmds).containsExactlyInAnyOrder(
                    "pauseRequested", "complete", "fail", "needsApproval", "cancel");
        }

        @Test
        void shouldReturnCommandsForPaused() {
            TaskExecution exec = buildExecution(TaskExecutionState.PAUSED);
            Set<String> cmds = machine.getAvailableCommands(exec);
            assertThat(cmds).containsExactlyInAnyOrder("resume", "cancel");
        }

        @Test
        void shouldReturnEmptyCommandsForDone() {
            TaskExecution exec = buildExecution(TaskExecutionState.DONE);
            assertThat(machine.getAvailableCommands(exec)).isEmpty();
        }
    }
}
