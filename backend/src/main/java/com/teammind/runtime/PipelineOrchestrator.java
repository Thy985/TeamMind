package com.teammind.runtime;

import com.teammind.common.*;
import com.teammind.entity.*;
import com.teammind.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PipelineOrchestrator — Phase 1B: Single-Agent Runtime
 *
 * 职责：
 *   1. 从 YAML 加载 Pipeline 定义
 *   2. 为每个 Task 创建 TaskExecution + ExecutionStep
 *   3. 调用 Agent Plugin 执行步骤
 *   4. 跟踪 AgentInvocation（pid, duration, exitCode）
 *   5. 完成后创建 Artifact + Evidence
 *   6. 失败时记录 errorReason
 *
 * 与 TaskExecutionStateMachine 的关系：
 *   PipelineOrchestrator 调用 machine.transition() 来改变状态，
 *   然后由调用方（如 MissionControlController）持久化到 DB。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineOrchestrator {

    private final TaskRepository taskRepo;
    private final TaskExecutionRepository executionRepo;
    private final ExecutionStepRepository stepRepo;
    private final AgentInvocationRepository invocationRepo;
    private final ArtifactRepository artifactRepo;
    private final EvidenceRepository evidenceRepo;
    private final TaskExecutionStateMachine stateMachine;
    private final EvidenceLifecycleService evidenceService;

    /**
     * 提交一个新 Task 并开始执行。
     *
     * @param taskId    已创建的 Task ID
     * @param objective 任务目标
     * @param agentId   使用的 Agent（"codex" / "claude-code"）
     * @return 创建的 TaskExecution
     */
    public TaskExecution submitAndRun(String taskId, String objective, String agentId) {
        // 1. 创建 TaskExecution (NEW)
        TaskExecution execution = TaskExecution.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .projectId(findProjectId(taskId))
                .objective(objective)
                .state(TaskState.EXECUTING)
                .executionState(TaskExecutionState.NEW)
                .attemptNumber(1)
                .agentId(agentId)
                .createdAt(LocalDateTime.now())
                .build();
        execution = executionRepo.save(execution);

        // 2. NEW → PENDING (submit)
        stateMachine.transition(execution, "submit");
        execution = executionRepo.save(execution);
        log.info("TaskExecution {} submitted (attempt 1)", execution.getId());

        // 3. PENDING → RUNNING (start)
        stateMachine.transition(execution, "start");
        execution = executionRepo.save(execution);

        // 4. 创建 ExecutionStep (implement)
        ExecutionStep step = ExecutionStep.builder()
                .id(UUID.randomUUID().toString())
                .executionId(execution.getId())
                .stepName("implement")
                .agentId(agentId)
                .role("LEAD")
                .state(ExecutionStepState.PENDING)
                .prompt(objective)
                .startedAt(LocalDateTime.now())
                .build();
        step = stepRepo.save(step);
        execution.setCurrentStepName("implement");
        execution.setAgentId(agentId);
        execution = executionRepo.save(execution);

        // 5. PENDING → STARTED → RUNNING
        step.setState(ExecutionStepState.STARTED);
        step = stepRepo.save(step);
        step.setState(ExecutionStepState.RUNNING);
        step = stepRepo.save(step);

        log.info("Step {} started for execution {}", step.getId(), execution.getId());
        return execution;
    }

    /**
     * 完成一个步骤（由 Agent Plugin 回调调用）。
     *
     * @param executionId 执行 ID
     * @param invocationId 调用的 AgentInvocation ID
     * @param exitCode 退出码
     * @param stdoutSummary stdout 摘要
     * @param stderrSummary stderr 摘要
     * @param durationMs 耗时
     */
    public void completeStep(String executionId, String invocationId,
                             int exitCode, String stdoutSummary,
                             String stderrSummary, long durationMs) {
        TaskExecution execution = executionRepo.findById(executionId).orElse(null);
        if (execution == null) {
            log.warn("Execution {} not found for step completion", executionId);
            return;
        }

        // 查找对应的 step
        List<ExecutionStep> steps = stepRepo.findByExecutionIdOrderByStartedAtAsc(executionId);
        ExecutionStep currentStep = steps.stream()
                .filter(s -> s.getState() == ExecutionStepState.RUNNING)
                .findFirst()
                .orElse(null);

        if (currentStep == null) {
            log.warn("No RUNNING step found for execution {}", executionId);
            return;
        }

        // 完成 Invocation
        AgentInvocation invocation = invocationRepo.findById(invocationId).orElse(null);
        if (invocation != null) {
            invocation.setExitCode(exitCode);
            invocation.setDurationMs(durationMs);
            invocation.setStdoutSummary(stdoutSummary != null ? stdoutSummary.substring(0, Math.min(500, stdoutSummary.length())) : "");
            invocation.setStderrSummary(stderrSummary != null ? stderrSummary.substring(0, Math.min(500, stderrSummary.length())) : "");
            invocation.setCompletedAt(LocalDateTime.now());
            invocationRepo.save(invocation);
        }

        if (exitCode == 0) {
            // 成功：创建 Artifact + Evidence
            String artifactId = UUID.randomUUID().toString();
            Artifact artifact = Artifact.builder()
                    .id(artifactId)
                    .invocationId(invocationId)
                    .type("CODE_DIFF")
                    .summary(stdoutSummary != null ? stdoutSummary : "Implementation completed")
                    .data(Map.of("files_changed", 0, "lines_added", 0))
                    .createdAt(LocalDateTime.now())
                    .build();
            artifactRepo.save(artifact);

            // 声明 Evidence
            Evidence evidence = evidenceService.claim(invocationId, EvidenceType.GIT_DIFF, "Agent completed implementation");
            evidence.setArtifactHash(artifactId);
            evidenceRepo.save(evidence);

            // 标记 step COMPLETED
            currentStep.setState(ExecutionStepState.COMPLETED);
            currentStep.setOutputSummary(stdoutSummary);
            currentStep.setDurationMs(durationMs);
            currentStep.setCompletedAt(LocalDateTime.now());
            stepRepo.save(currentStep);

            // Execution: RUNNING → DONE
            stateMachine.transition(execution, "complete");
            execution.setSummary("Implementation completed successfully");
            execution.setCompletedAt(LocalDateTime.now());
            execution.setDurationMs(durationMs);
            executionRepo.save(execution);

            // 更新 Task 宏观状态
            var task = taskRepo.findById(execution.getTaskId()).orElse(null);
            if (task != null) {
                task.setState(TaskState.DONE);
                task.setCompletedAt(LocalDateTime.now());
                taskRepo.save(task);
            }

            log.info("Execution {} completed successfully", executionId);
        } else {
            // 失败
            currentStep.setState(ExecutionStepState.FAILED);
            currentStep.setDurationMs(durationMs);
            currentStep.setCompletedAt(LocalDateTime.now());
            stepRepo.save(currentStep);

            stateMachine.transition(execution, "fail");
            execution.setErrorReason("Exit code: " + exitCode + ", stderr: " + stderrSummary);
            execution.setCompletedAt(LocalDateTime.now());
            executionRepo.save(execution);

            log.warn("Execution {} failed with exit code {}", executionId, exitCode);
        }
    }

    /**
     * 重试一个失败的 Execution。
     */
    public TaskExecution retryExecution(String executionId) {
        TaskExecution original = executionRepo.findById(executionId).orElse(null);
        if (original == null) return null;

        // FAILED → RETRYING
        stateMachine.transition(original, "retry");
        original = executionRepo.save(original);

        // RETRYING → PENDING (startRetry)
        stateMachine.transition(original, "startRetry");
        original = executionRepo.save(original);

        // 创建新的 attempt
        TaskExecution newExec = TaskExecution.builder()
                .id(UUID.randomUUID().toString())
                .taskId(original.getTaskId())
                .projectId(original.getProjectId())
                .objective(original.getObjective())
                .state(TaskState.RETRYING)
                .executionState(TaskExecutionState.PENDING)
                .attemptNumber(original.getAttemptNumber() + 1)
                .agentId(original.getAgentId())
                .createdAt(LocalDateTime.now())
                .build();
        newExec = executionRepo.save(newExec);

        // PENDING → RUNNING
        stateMachine.transition(newExec, "start");
        newExec = executionRepo.save(newExec);

        // 创建新 step
        ExecutionStep step = ExecutionStep.builder()
                .id(UUID.randomUUID().toString())
                .executionId(newExec.getId())
                .stepName("implement")
                .agentId(newExec.getAgentId())
                .role("LEAD")
                .state(ExecutionStepState.PENDING)
                .prompt(newExec.getObjective())
                .startedAt(LocalDateTime.now())
                .build();
        step = stepRepo.save(step);
        step.setState(ExecutionStepState.STARTED);
        step = stepRepo.save(step);
        step.setState(ExecutionStepState.RUNNING);
        step = stepRepo.save(step);

        log.info("Retrying execution {} as attempt {}", executionId, newExec.getAttemptNumber());
        return newExec;
    }

    private String findProjectId(String taskId) {
        return taskRepo.findById(taskId)
                .map(Task::getProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }
}
