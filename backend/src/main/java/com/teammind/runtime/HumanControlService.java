package com.teammind.runtime;

import com.teammind.common.TaskExecutionState;
import com.teammind.entity.TaskExecution;
import com.teammind.repository.AgentInvocationRepository;
import com.teammind.repository.TaskExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * HumanControlService — Phase 1B: 人工控制服务
 *
 * 提供 pause / resume / cancel / retry / approve 操作。
 * 所有操作都通过 TaskExecutionStateMachine 验证状态转移合法性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HumanControlService {

    private final TaskExecutionStateMachine stateMachine;
    private final TaskExecutionRepository executionRepo;
    private final AgentInvocationRepository invocationRepo;

    /**
     * 暂停执行。触发 PAUSE_REQUESTED。
     */
    public TaskExecution pause(String executionId) {
        TaskExecution exec = executionRepo.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        if (exec.getExecutionState() == TaskExecutionState.PAUSE_REQUESTED) {
            return exec;
        }

        stateMachine.transition(exec, "pauseRequested");
        return executionRepo.save(exec);
    }

    /**
     * 标记暂停完成（当前 tool 已退出）。
     */
    public TaskExecution pauseComplete(String executionId) {
        TaskExecution exec = executionRepo.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        if (exec.getExecutionState() != TaskExecutionState.PAUSE_REQUESTED) {
            throw new IllegalStateException(
                    "Cannot complete pause: execution is " + exec.getExecutionState());
        }

        stateMachine.transition(exec, "pauseComplete");
        return executionRepo.save(exec);
    }

    /**
     * 恢复执行。
     */
    public TaskExecution resume(String executionId) {
        TaskExecution exec = executionRepo.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        stateMachine.transition(exec, "resume");
        return executionRepo.save(exec);
    }

    /**
     * 取消执行。尝试 kill 进程并标记 CANCELLED。
     */
    public TaskExecution cancel(String executionId) {
        TaskExecution exec = executionRepo.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        killRunningProcesses(exec);

        stateMachine.transition(exec, "cancel");
        return executionRepo.save(exec);
    }

    /**
     * 重试执行。
     */
    public Optional<TaskExecution> retry(String executionId) {
        TaskExecution exec = executionRepo.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        if (exec.getExecutionState() != TaskExecutionState.FAILED) {
            throw new IllegalStateException(
                    "Can only retry FAILED execution, current: " + exec.getExecutionState());
        }

        stateMachine.transition(exec, "retry");
        return Optional.of(executionRepo.save(exec));
    }

    /**
     * 审批通过。
     */
    public TaskExecution approve(String executionId) {
        TaskExecution exec = executionRepo.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        if (exec.getExecutionState() != TaskExecutionState.NEEDS_APPROVAL) {
            throw new IllegalStateException(
                    "Can only approve NEEDS_APPROVAL execution, current: " + exec.getExecutionState());
        }

        // NEEDS_APPROVAL → APPROVING → RUNNING
        stateMachine.transition(exec, "approve");
        exec = executionRepo.save(exec);
        stateMachine.transition(exec, "approvalProceed");
        return executionRepo.save(exec);
    }

    /**
     * 拒绝审批。
     */
    public TaskExecution deny(String executionId) {
        TaskExecution exec = executionRepo.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        stateMachine.transition(exec, "deny");
        return executionRepo.save(exec);
    }

    /**
     * 检查是否可暂停。
     */
    public boolean canPause(String executionId) {
        return executionRepo.findById(executionId)
                .map(exec -> stateMachine.canTransition(exec, "pauseRequested"))
                .orElse(false);
    }

    /**
     * 检查是否可取消。
     */
    public boolean canCancel(String executionId) {
        return executionRepo.findById(executionId)
                .map(exec -> stateMachine.canTransition(exec, "cancel"))
                .orElse(false);
    }

    /**
     * 获取当前可用的命令列表。
     */
    public Set<String> getAvailableCommands(String executionId) {
        return executionRepo.findById(executionId)
                .map(stateMachine::getAvailableCommands)
                .orElse(Set.of());
    }

    // ─── private helpers ──────────────────────────────────────

    private void killRunningProcesses(TaskExecution exec) {
        // Find all invocations with alive processes for this execution
        // Simplified: iterate through all invocations and check liveness
        invocationRepo.findAll().forEach(inv -> {
            if (inv.getPid() != null && Boolean.TRUE.equals(inv.getProcessAlive())) {
                try {
                    ProcessHandle.of(inv.getPid())
                            .ifPresent(p -> {
                                p.destroyForcibly();
                                log.info("Killed process PID={}", inv.getPid());
                            });
                } catch (Exception e) {
                    log.warn("Failed to kill process PID={}: {}", inv.getPid(), e.getMessage());
                }
            }
        });
    }
}
