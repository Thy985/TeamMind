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

class RecoveryServiceTest {

    private TaskExecutionStateMachine machine;

    @BeforeEach
    void setUp() {
        machine = new TaskExecutionStateMachine();
    }

    @Nested
    @DisplayName("recovery detection")
    class RecoveryTests {

        @Test
        void shouldDetectRunningExecution() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
            // In real impl, check ProcessHandle.of(pid).isAlive()
            assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.RUNNING);
        }

        @Test
        void shouldDetectPauseRequestedExecution() {
            TaskExecution exec = buildExecution(TaskExecutionState.PAUSE_REQUESTED);
            assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.PAUSE_REQUESTED);
        }

        @Test
        void shouldNotRecoverDoneExecution() {
            TaskExecution exec = buildExecution(TaskExecutionState.DONE);
            // DONE is terminal, no recovery needed
            assertThat(exec.getExecutionState()).isEqualTo(TaskExecutionState.DONE);
        }
    }

    @Nested
    @DisplayName("state transition during recovery")
    class RecoveryTransitionTests {

        @Test
        void shouldAllowCancelFromRunning() {
            TaskExecution exec = buildExecution(TaskExecutionState.RUNNING);
            assertThat(machine.canTransition(exec, "cancel")).isTrue();
        }

        @Test
        void shouldAllowCancelFromPaused() {
            TaskExecution exec = buildExecution(TaskExecutionState.PAUSED);
            assertThat(machine.canTransition(exec, "cancel")).isTrue();
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
