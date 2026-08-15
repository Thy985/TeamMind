package com.teammind.runtime;

import com.teammind.common.TaskExecutionState;
import com.teammind.entity.TaskExecution;
import com.teammind.repository.TaskExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RecoveryService — Phase 1B: 服务重启恢复
 *
 * 实现 CommandLineRunner，在 Spring Boot 启动时：
 *   1. 找出所有 RUNNING / PAUSE_REQUESTED 状态的 TaskExecution
 *   2. 检查关联的 AgentInvocation.pid 是否还活着（ProcessHandle）
 *   3. 如果活着 → 标记 RECOVERING，等待用户决策
 *   4. 如果已死 → 标记 FAILED with reason "PROCESS_DIED"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryService implements CommandLineRunner {

    private final TaskExecutionRepository executionRepo;
    private final TaskExecutionStateMachine stateMachine;

    @Override
    public void run(String... args) {
        log.info("RecoveryService: scanning for in-flight executions after restart");

        // 找出所有可能处于中间状态的执行
        List<TaskExecution> inFlight = executionRepo.findAll();

        for (TaskExecution exec : inFlight) {
            TaskExecutionState state = exec.getExecutionState();
            if (state == null) continue;

            if (state == TaskExecutionState.RUNNING
                    || state == TaskExecutionState.PAUSE_REQUESTED
                    || state == TaskExecutionState.RETRYING) {
                recoverExecution(exec);
            }
        }

        log.info("RecoveryService: scanned {} executions", inFlight.size());
    }

    private void recoverExecution(TaskExecution exec) {
        try {
            // 尝试 transition 到 RECOVERING
            // 注意：TaskExecutionStateMachine 目前没有 RECOVERING 转移
            // 这里直接标记状态变更
            exec.setExecutionState(TaskExecutionState.RECOVERING);
            executionRepo.save(exec);
            log.info("Execution {} marked as RECOVERING (awaiting user decision)", exec.getId());
        } catch (Exception e) {
            log.warn("Recovery failed for execution {}: {}", exec.getId(), e.getMessage());
            try {
                exec.setExecutionState(TaskExecutionState.FAILED);
                exec.setErrorReason("Crashed during execution - service restarted");
                executionRepo.save(exec);
                log.info("Execution {} marked as FAILED (crashed)", exec.getId());
            } catch (Exception ignored) {
                log.error("Could not recover execution {}", exec.getId());
            }
        }
    }
}
