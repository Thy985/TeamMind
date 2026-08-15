package com.teammind.runtime;

import com.teammind.common.TaskExecutionState;
import com.teammind.common.TaskState;
import com.teammind.entity.TaskExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PipelineOrchestratorTest {

    private TaskExecutionStateMachine machine;

    @BeforeEach
    void setUp() {
        machine = new TaskExecutionStateMachine();
    }

    @Nested
    @DisplayName("submit → start lifecycle")
    class SubmitStartTests {

        @Test
        void shouldTransitionNewToPendingToRunning() {
            TaskExecution exec = buildExecution(TaskExecutionState.NEW);

            // submit: NEW → PENDING
            machine.transition(exec, "submit");
            assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.PENDING);
            assertThat(exec.getStartedAt()).isNull();

            // start: PENDING → RUNNING
            machine.transition(exec, "start");
            assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.RUNNING);
            assertThat(exec.getStartedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("complete lifecycle")
    class CompleteTests {

        @Test
        void shouldTransitionRunningToDone() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);

            machine.transition(exec, "complete");
            assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.DONE);
            assertThat(exec.getCompletedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("failure lifecycle")
    class FailureTests {

        @Test
        void shouldTransitionRunningToFailed() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);

            machine.transition(exec, "fail");
            assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.FAILED);
        }

        @Test
        void shouldAllowRetryFromFailed() {
            TaskExecution exec = buildExecution(TaskExecutionState.FAILED);

            machine.transition(exec, "retry");
            assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.RETRYING);
        }

        @Test
        void shouldAllowStartRetryFromRetrying() {
            TaskExecution exec = buildExecution(TaskExecutionState.RETRYING);

            machine.transition(exec, "startRetry");
            assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.PENDING);
        }
    }

    @Nested
    @DisplayName("available commands")
    class CommandsTests {

        @Test
        void shouldHaveCorrectCommandsForRunning() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
            Set<String> cmds = machine.getAvailableCommands(exec);
            assertThat(cmds).containsExactlyInAnyOrder(
                    "pauseRequested", "complete", "fail", "needsApproval", "cancel");
        }

        @Test
        void shouldHaveCorrectCommandsForPaused() {
            TaskExecution exec = buildExecution(TaskExecutionState.PAUSED);
            Set<String> cmds = machine.getAvailableCommands(exec);
            assertThat(cmds).containsExactlyInAnyOrder("resume", "cancel");
        }

        @Test
        void shouldHaveEmptyCommandsForDone() {
            TaskExecution exec = buildExecution(TaskExecutionState.DONE);
            assertThat(machine.getAvailableCommands(exec)).isEmpty();
        }
    }

    private TaskExecution buildExecution(TaskExecutionState state) {
        return TaskExecution.builder()
                .id("exec-" + System.nanoTime())
                .projectId("proj-test")
                .state(TaskState.EXECUTING)
                .executionState(state)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
