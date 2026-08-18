package com.teammind.runtime;

import com.teammind.common.TaskExecutionState;
import com.teammind.entity.TaskExecution;
import com.teammind.plugin.PluginManager;
import com.teammind.plugin.adapter.CLIAdapter;
import com.teammind.plugin.adapter.CLIProcessTracker;
import com.teammind.repository.AgentInvocationRepository;
import com.teammind.repository.TaskExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * RecoveryService — Phase 3A: 服务重启恢复 + CLI 健康验证
 *
 * 实现 CommandLineRunner，在 Spring Boot 启动时：
 *   1. 找出所有 RUNNING / PAUSE_REQUESTED 状态的 TaskExecution
 *   2. 检查关联的 AgentInvocation.pid 是否还活着（ProcessHandle.of(pid)）
 *   3. 如果活着 → 标记 RECOVERING，等待用户决策
 *   4. 如果已死 → 标记 FAILED with reason "PROCESS_DIED"
 *   5. 扫描所有已注入 CLI 的健康状态（Phase 3B: 平台化验证）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryService implements CommandLineRunner, RuntimeLifecycle {

    private final TaskExecutionRepository executionRepo;
    private final AgentInvocationRepository invocationRepo;
    private final TaskExecutionStateMachine stateMachine;
    private final PluginManager pluginManager;
    private final CLIProcessTracker processTracker;

    @Override
    public void run(String... args) {
        // initialize() 由 RuntimeBootstrap @PostConstruct 调用，此处不再重复
    }

    @Override
    public void initialize() throws Exception {
        log.info("RecoveryService: scanning for in-flight executions after restart");

        // ── Step 1: 恢复 in-flight executions ──────────────────
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

        // ── Step 2: 验证所有已注入 CLI 的健康状态（Phase 3B）────
        scanCLIHealth();

        log.info("RecoveryService: scanned {} executions, {} registered CLI(s)",
                inFlight.size(), pluginManager.getAll().size());
    }

    private void recoverExecution(TaskExecution exec) {
        try {
            // 尝试从 AgentInvocation 找到 pid
            Long pid = findPID(exec);

            if (pid != null && ProcessHandle.of(pid).isPresent()) {
                // 进程还活着 → RECOVERING
                exec.setExecutionState(TaskExecutionState.RECOVERING);
                exec.setErrorReason("PROCESS_ALIVE_PID=" + pid);
                executionRepo.save(exec);
                log.info("Execution {} → RECOVERING (process still alive, PID={})", exec.getId(), pid);
            } else {
                // 进程死了 → FAILED
                exec.setExecutionState(TaskExecutionState.FAILED);
                exec.setErrorReason("PROCESS_DIED");
                executionRepo.save(exec);
                log.info("Execution {} → FAILED (process died)", exec.getId());
            }
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

    /**
     * 从 AgentInvocation 查找进程 PID
     */
    private Long findPID(TaskExecution exec) {
        return invocationRepo.findAll().stream()
                .filter(i -> i.getStepId() != null)
                .filter(i -> exec.getAgentId() != null && exec.getAgentId().equals(i.getPluginId()))
                .map(inv -> inv.getPid())
                .filter(pid -> pid != null && ProcessHandle.of(pid).isPresent())
                .findFirst()
                .orElse(null);
    }

    /**
     * 验证所有已注册 CLI 的健康状态（Phase 3B 核心：3A 验证 3B 成果）
     */
    private void scanCLIHealth() {
        log.info("RecoveryService: verifying {} registered CLI(s)", pluginManager.getAll().size());

        for (var plugin : pluginManager.getAll()) {
            if (plugin.type() != com.teammind.plugin.Plugin.PluginType.AGENT) continue;

            try {
                // 尝试转型为 CLIAdapter（Phase 3B 新增）
                CLIAdapter adapter = (CLIAdapter) plugin;
                com.teammind.plugin.Plugin.PluginHealth health = adapter.inspect();

                log.info("CLI {} health: {} (command={}, alive={})",
                        adapter.id(), health, adapter.config().command(), adapter.isAlive());

                // 将存活进程注册到 tracker
                if (adapter.isAlive()) {
                    adapter.getProcessHandle().ifPresent(handle ->
                            processTracker.register(adapter.id(), handle));
                }
            } catch (ClassCastException e) {
                // 非 CLI 插件（Verifier/Memory 等），跳过
                log.debug("Plugin {} is not a CLIAdapter, skipping health check", plugin.id());
            }
        }
    }
}
