package com.teammind.runtime;

import com.teammind.common.*;
import com.teammind.entity.*;
import com.teammind.plugin.Plugin;
import com.teammind.plugin.PluginManager;
import com.teammind.repository.*;
import com.teammind.websocket.WSEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * PipelineOrchestrator — Phase 1C-2: Multi-Agent Pipeline
 *
 * 职责：
 *   1. 从 YAML 加载 PipelineDefinition
 *   2. 解析步骤定义（agent, prompt, handoff, 条件跳转）
 *   3. 执行多步骤流水线，自动 handoff
 *   4. 跟踪每个步骤的 PipelineStepResult
 *   5. 根据条件决定下一步（handoff / retry / approval）
 *   6. 构建最终的 PipelineExecutionResult
 *
 * 与 Phase 1B PipelineOrchestrator 的关系：
 *   保留了 submitAndRun / completeStep / retryExecution 方法以保持兼容，
 *   新增 executePipeline() 支持多步骤 YAML 驱动执行。
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
    private final PluginManager pluginManager;
    private final ReadinessManager readinessManager;
    private final WSEventPublisher wsPublisher;

    // ─── Phase 1B compatibility ──────────────────────────────

    public TaskExecution submitAndRun(String taskId, String objective, String agentId) {
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

        stateMachine.transition(execution, "submit");
        execution = executionRepo.save(execution);
        log.info("TaskExecution {} submitted (attempt 1)", execution.getId());

        stateMachine.transition(execution, "start");
        execution = executionRepo.save(execution);

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

        step.setState(ExecutionStepState.STARTED);
        step = stepRepo.save(step);
        step.setState(ExecutionStepState.RUNNING);
        step = stepRepo.save(step);

        log.info("Step {} started for execution {}", step.getId(), execution.getId());
        return execution;
    }

    public void completeStep(String executionId, String invocationId,
                              int exitCode, String stdoutSummary,
                              String stderrSummary, long durationMs) {
        TaskExecution execution = executionRepo.findById(executionId).orElse(null);
        if (execution == null) {
            log.warn("Execution {} not found for step completion", executionId);
            return;
        }

        List<ExecutionStep> steps = stepRepo.findByExecutionIdOrderByStartedAtAsc(executionId);
        ExecutionStep currentStep = steps.stream()
                .filter(s -> s.getState() == ExecutionStepState.RUNNING)
                .findFirst()
                .orElse(null);

        if (currentStep == null) {
            log.warn("No RUNNING step found for execution {}", executionId);
            return;
        }

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

            Evidence evidence = evidenceService.claim(invocationId, EvidenceType.GIT_DIFF, "Agent completed implementation");
            evidence.setArtifactHash(artifactId);
            evidenceRepo.save(evidence);

            currentStep.setState(ExecutionStepState.COMPLETED);
            currentStep.setOutputSummary(stdoutSummary);
            currentStep.setDurationMs(durationMs);
            currentStep.setCompletedAt(LocalDateTime.now());
            stepRepo.save(currentStep);

            stateMachine.transition(execution, "complete");
            execution.setSummary("Implementation completed successfully");
            execution.setCompletedAt(LocalDateTime.now());
            execution.setDurationMs(durationMs);
            executionRepo.save(execution);

            var task = taskRepo.findById(execution.getTaskId()).orElse(null);
            if (task != null) {
                task.setState(TaskState.DONE);
                task.setCompletedAt(LocalDateTime.now());
                taskRepo.save(task);
            }

            log.info("Execution {} completed successfully", executionId);
        } else {
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

    public TaskExecution retryExecution(String executionId) {
        TaskExecution original = executionRepo.findById(executionId).orElse(null);
        if (original == null) return null;

        stateMachine.transition(original, "retry");
        original = executionRepo.save(original);

        stateMachine.transition(original, "startRetry");
        original = executionRepo.save(original);

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

        stateMachine.transition(newExec, "start");
        newExec = executionRepo.save(newExec);

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

    // ─── Phase 1C-2: Multi-Agent Pipeline ────────────────────

    /**
     * 从 classpath 加载 Pipeline 定义
     */
    public PipelineDefinition loadPipeline(String resourceName) {
        try {
            ClassPathResource resource = new ClassPathResource("pipelines/" + resourceName);
            try (InputStream is = resource.getInputStream()) {
                Yaml yaml = new Yaml();
                Map<String, Object> map = yaml.load(is);
                return parsePipelineDefinition(map);
            }
        } catch (Exception e) {
            log.error("Failed to load pipeline '{}': {}", resourceName, e.getMessage());
            throw new RuntimeException("Failed to load pipeline: " + resourceName, e);
        }
    }

    /**
     * 执行多步骤 Pipeline
     *
     * @param taskId         任务 ID
     * @param objective      任务目标
     * @param constraints    约束列表
     * @param pipelineName   Pipeline YAML 文件名（不含 .yaml 后缀）
     * @return 执行结果
     */
    public PipelineExecutionResult executePipeline(String taskId, String objective,
                                                     List<String> constraints,
                                                     String pipelineName) {
        PipelineDefinition def = loadPipeline(pipelineName + ".yaml");
        log.info("Executing pipeline '{}' for task {}", pipelineName, taskId);

        PipelineContext context = PipelineContext.builder()
                .pipelineName(def.getName())
                .taskId(taskId)
                .projectId(findProjectId(taskId))
                .objective(objective)
                .stepIndex(0)
                .startedAt(LocalDateTime.now())
                .build();

        // Publish pipeline start
        if (wsPublisher != null) {
            wsPublisher.publishLog(taskId, "pipeline", "orchestrator",
                    "Pipeline '" + def.getName() + "' started");
        }

        List<PipelineStepResult> allResults = new ArrayList<>();
        String currentStepName = def.getSteps().get(0).getName();
        int totalAttempts = 0;
        int maxAttempts = def.getRetry().getMaxAttempts();

        while (currentStepName != null && totalAttempts < maxAttempts * def.getSteps().size()) {
            totalAttempts++;
            log.info("Pipeline step: {} (attempt {}/{})", currentStepName, totalAttempts, maxAttempts);

            PipelineStepDefinition stepDef = def.getStep(currentStepName);
            if (stepDef == null) {
                log.error("Step '{}' not found in pipeline definition", currentStepName);
                break;
            }

            context.setCurrentStep(currentStepName);

            // Resolve prompt
            String prompt = stepDef.resolvePrompt(objective, constraints, context);

            // Execute step
            PipelineStepResult stepResult = executeStep(stepDef, prompt, context);
            allResults.add(stepResult);
            context.recordStepResult(currentStepName, stepResult);

            // Check for approval needed
            if (stepResult.isCritical()) {
                log.warn("Critical finding in step '{}', need approval", currentStepName);
                context.setCompletedAt(LocalDateTime.now());
                return PipelineExecutionResult.builder()
                        .pipelineName(def.getName())
                        .taskId(taskId)
                        .overallStatus("NEEDS_APPROVAL")
                        .context(context)
                        .stepResults(allResults)
                        .totalDurationMs(calculateTotalDuration(allResults))
                        .startedAt(context.getStartedAt())
                        .completedAt(LocalDateTime.now())
                        .build();
            }

            // Determine next step
            currentStepName = stepDef.determineNext(stepResult);
            if (currentStepName != null) {
                context.recordHandoff(context.getCurrentStep(), currentStepName,
                        stepResult.isSuccess() ? "success" : "condition");
            }

            // Backoff between steps
            if (currentStepName != null) {
                try {
                    Thread.sleep(def.getRetry().getBackoffMs() / 4);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // Build final result
        Artifact finalArtifact = context.getArtifacts().values().stream()
                .filter(Objects::nonNull)
                .reduce((a, b) -> b)  // last artifact wins
                .orElse(null);

        String overallStatus = allResults.stream().anyMatch(PipelineStepResult::isFailed)
                ? "FAILED" : "SUCCESS";

        context.setCompletedAt(LocalDateTime.now());

        PipelineExecutionResult result = PipelineExecutionResult.builder()
                .pipelineName(def.getName())
                .taskId(taskId)
                .overallStatus(overallStatus)
                .context(context)
                .stepResults(allResults)
                .finalArtifact(finalArtifact)
                .totalDurationMs(calculateTotalDuration(allResults))
                .startedAt(context.getStartedAt())
                .completedAt(context.getCompletedAt())
                .build();

        log.info("Pipeline '{}' completed: status={}", def.getName(), overallStatus);

        // Publish final state update
        if (wsPublisher != null) {
            wsPublisher.publishLog(taskId, "pipeline", "orchestrator",
                    "Pipeline '" + def.getName() + "' finished: " + overallStatus);
        }

        return result;
    }

    /**
     * 执行单个步骤（模拟 agent invocation）
     */
    private PipelineStepResult executeStep(PipelineStepDefinition stepDef,
                                             String prompt, PipelineContext context) {
        long startMs = System.currentTimeMillis();

        try {
            String agentId = stepDef.getAgent();
            if (agentId == null && stepDef.isMultiAgent()) {
                // 多 Agent 步骤：取第一个可用的
                agentId = stepDef.getAgents().get(0);
            }

            if (agentId == null) {
                log.warn("No agent specified for step '{}'", stepDef.getName());
                return PipelineStepResult.builder()
                        .stepName(stepDef.getName())
                        .state("FAILED")
                        .errorReason("No agent specified")
                        .durationMs(System.currentTimeMillis() - startMs)
                        .build();
            }

            // Check readiness
            if (readinessManager != null) {
                ReadinessResult readiness = readinessManager.check(agentId);
                if (!readiness.isRunnable()) {
                    log.warn("Agent '{}' not ready: {}", agentId, readiness.state());
                    if (wsPublisher != null) {
                        wsPublisher.publishLog(context.getTaskId(), "error", agentId,
                                "Agent not ready: " + readiness.state());
                    }
                    return PipelineStepResult.builder()
                            .stepName(stepDef.getName())
                            .agentId(agentId)
                            .state("FAILED")
                            .errorReason("Agent not ready: " + readiness.state())
                            .durationMs(System.currentTimeMillis() - startMs)
                            .build();
                }
            }

            // Publish step started event
            if (wsPublisher != null) {
                wsPublisher.publishStepStarted(context.getTaskId(), stepDef.getName(), agentId);
            }

            // Execute via real plugin invocation
            String output;
            long duration;
            try {
                var pluginOpt = pluginManager.findById(agentId);
                if (pluginOpt.isEmpty()) {
                    log.error("Plugin '{}' not found for step '{}'", agentId, stepDef.getName());
                    return PipelineStepResult.builder()
                            .stepName(stepDef.getName())
                            .agentId(agentId)
                            .state("FAILED")
                            .errorReason("Plugin not found: " + agentId)
                            .durationMs(System.currentTimeMillis() - startMs)
                            .build();
                }
                Plugin plugin = pluginOpt.get();
                String projectId = context.getProjectId() != null ? context.getProjectId() : "default";
                Map<String, Object> artifactMap = new java.util.HashMap<>();
                if (context.getArtifacts() != null) {
                    context.getArtifacts().forEach((k, v) -> artifactMap.put(k, v));
                }
                Plugin.PluginContext pluginCtx = new Plugin.PluginContext(
                        projectId, context.getTaskId(),
                        stepDef.getPrompt() != null ? Map.of("prompt", prompt) : Map.of(),
                        ".",  // projectPath: 默认当前目录（避免使用 objective 文本）
                        artifactMap,
                        plugin.type() == Plugin.PluginType.VERIFIER
                                ? List.of("verification")
                                : List.of("implementation"));

                Plugin.PluginResult result = plugin.invoke(pluginCtx);
                if (result.success()) {
                    output = result.data() != null ? result.data().toString() : "";
                    duration = System.currentTimeMillis() - startMs;
                } else {
                    log.warn("Plugin '{}' failed for step '{}': {}", agentId, stepDef.getName(), result.error());
                    return PipelineStepResult.builder()
                            .stepName(stepDef.getName())
                            .agentId(agentId)
                            .state("FAILED")
                            .errorReason(result.error())
                            .durationMs(System.currentTimeMillis() - startMs)
                            .build();
                }
            } catch (Exception e) {
                log.error("Step '{}' plugin invocation failed: {}", stepDef.getName(), e.getMessage(), e);
                return PipelineStepResult.builder()
                        .stepName(stepDef.getName())
                        .agentId(agentId)
                        .state("FAILED")
                        .errorReason(e.getMessage())
                        .durationMs(System.currentTimeMillis() - startMs)
                        .build();
            }

            // Create artifact with files_changed data
            String artifactId = UUID.randomUUID().toString();
            Map<String, Object> artifactData = new HashMap<>();
            artifactData.put("step", stepDef.getName());
            artifactData.put("agent", agentId);
            // Extract files_changed from output if available
            String filesChanged = extractFilesChanged(output);
            if (filesChanged != null) {
                artifactData.put("files_changed", filesChanged);
            }
            Artifact artifact = Artifact.builder()
                    .id(artifactId)
                    .type(stepDef.getOutput() != null ? stepDef.getOutput() : "UNKNOWN")
                    .summary(output)
                    .data(artifactData)
                    .createdAt(LocalDateTime.now())
                    .build();

            log.info("Step '{}' completed: agent={}, duration={}ms, output_len={}",
                    stepDef.getName(), agentId, duration, output.length());

            // Publish step completed event
            if (wsPublisher != null) {
                wsPublisher.publishStepCompleted(context.getTaskId(),
                        stepDef.getName(), agentId, true);
            }

            return PipelineStepResult.builder()
                    .stepName(stepDef.getName())
                    .agentId(agentId)
                    .state("SUCCESS")
                    .artifact(artifact)
                    .outputSummary(output)
                    .durationMs(duration)
                    .exitCode(0)
                    .build();

        } catch (Exception e) {
            log.error("Step '{}' failed: {}", stepDef.getName(), e.getMessage());
            return PipelineStepResult.builder()
                    .stepName(stepDef.getName())
                    .state("FAILED")
                    .errorReason(e.getMessage())
                    .durationMs(System.currentTimeMillis() - startMs)
                    .build();
        }
    }

    private long calculateTotalDuration(List<PipelineStepResult> results) {
        return results.stream().mapToLong(PipelineStepResult::getDurationMs).sum();
    }

    /**
     * 从 CLI 输出中提取 files_changed 列表
     * 匹配形如 "Files changed: file1.ts, file2.ts" 或 "file: src/main.ts" 的行
     */
    private String extractFilesChanged(String output) {
        if (output == null || output.isBlank()) return null;
        // Try to match "Files changed: ..." pattern
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?i)files?\\s*(?:changed|modified|created|deleted)?\\s*:?\\s*(.+)",
                java.util.regex.Pattern.MULTILINE);
        var m = p.matcher(output);
        if (m.find()) {
            String line = m.group(1).trim();
            if (!line.isBlank() && line.length() < 500) return line;
        }
        // Try to match "file: path" pattern
        StringBuilder sb = new StringBuilder();
        java.util.regex.Pattern fileP = java.util.regex.Pattern.compile(
                "(?i)(?:file|path)\\s*:\\s*(\\S+)", java.util.regex.Pattern.MULTILINE);
        var fm = fileP.matcher(output);
        while (fm.find()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(fm.group(1));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String findProjectId(String taskId) {
        return taskRepo.findById(taskId)
                .map(Task::getProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    /**
     * 取消正在执行的 Pipeline
     *
     * @param executionId 执行 ID
     */
    public void cancelPipeline(String executionId) {
        TaskExecution execution = executionRepo.findById(executionId).orElse(null);
        if (execution == null) {
            log.warn("Execution {} not found for cancellation", executionId);
            return;
        }

        // 检查是否可以取消
        if (execution.getExecutionState() != TaskExecutionState.RUNNING) {
            log.warn("Execution {} is not RUNNING, state={}", executionId, execution.getExecutionState());
            return;
        }

        log.info("Cancelling execution {}", executionId);

        // 取消当前运行的 step
        List<ExecutionStep> steps = stepRepo.findByExecutionIdOrderByStartedAtAsc(executionId);
        ExecutionStep currentStep = steps.stream()
                .filter(s -> s.getState() == ExecutionStepState.RUNNING)
                .findFirst()
                .orElse(null);

        if (currentStep != null) {
            // 取消当前 step 的 agent invocation
            List<AgentInvocation> invocations = invocationRepo.findByStepId(currentStep.getId());
            for (AgentInvocation invocation : invocations) {
                Optional<Plugin> pluginOpt = pluginManager.findById(invocation.getPluginId());
                pluginOpt.ifPresent(plugin -> {
                    try {
                        plugin.cancel();
                        log.info("Cancelled plugin '{}' for step '{}'", plugin.id(), currentStep.getStepName());
                    } catch (Exception e) {
                        log.warn("Failed to cancel plugin '{}': {}", plugin.id(), e.getMessage());
                    }
                });
            }

            currentStep.setState(ExecutionStepState.FAILED);
            currentStep.setCompletedAt(LocalDateTime.now());
            stepRepo.save(currentStep);
        }

        // 更新 execution 状态
        execution.setState(TaskState.CANCELLED);
        execution.setExecutionState(TaskExecutionState.CANCELLED);
        execution.setCompletedAt(LocalDateTime.now());
        execution.setErrorReason("Pipeline cancelled by user");
        execution = executionRepo.save(execution);

        // 更新 task 状态
        Task task = taskRepo.findById(execution.getTaskId()).orElse(null);
        if (task != null) {
            task.setState(TaskState.CANCELLED);
            task.setCompletedAt(LocalDateTime.now());
            taskRepo.save(task);
        }

        log.info("Execution {} cancelled successfully", executionId);

        // Publish cancellation event
        if (wsPublisher != null) {
            wsPublisher.publishLog(execution.getTaskId(), "pipeline", "orchestrator",
                    "Pipeline execution cancelled");
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("state", execution.getState().name());
            snapshot.put("executionState", execution.getExecutionState().name());
            wsPublisher.publishStateUpdate(execution.getTaskId(), snapshot);
        }
    }

    /**
     * 暂停正在执行的 Pipeline
     *
     * @param executionId 执行 ID
     */
    public void pausePipeline(String executionId) {
        TaskExecution execution = executionRepo.findById(executionId).orElse(null);
        if (execution == null) {
            log.warn("Execution {} not found for pause", executionId);
            return;
        }

        if (execution.getExecutionState() != TaskExecutionState.RUNNING) {
            log.warn("Execution {} is not RUNNING, state={}", executionId, execution.getExecutionState());
            return;
        }

        log.info("Pausing execution {}", executionId);
        execution.setExecutionState(TaskExecutionState.PAUSE_REQUESTED);
        execution = executionRepo.save(execution);

        if (wsPublisher != null) {
            wsPublisher.publishLog(execution.getTaskId(), "pipeline", "orchestrator",
                    "Pipeline pause requested");
        }
    }

    /**
     * 恢复暂停的 Pipeline
     *
     * @param executionId 执行 ID
     */
    public void resumePipeline(String executionId) {
        TaskExecution execution = executionRepo.findById(executionId).orElse(null);
        if (execution == null) {
            log.warn("Execution {} not found for resume", executionId);
            return;
        }

        if (execution.getExecutionState() != TaskExecutionState.PAUSE_REQUESTED
                && execution.getExecutionState() != TaskExecutionState.PAUSED) {
            log.warn("Execution {} is not paused, state={}", executionId, execution.getExecutionState());
            return;
        }

        log.info("Resuming execution {}", executionId);
        execution.setExecutionState(TaskExecutionState.RUNNING);
        execution = executionRepo.save(execution);

        if (wsPublisher != null) {
            wsPublisher.publishLog(execution.getTaskId(), "pipeline", "orchestrator",
                    "Pipeline resumed");
        }
    }

    /**
     * 将 YAML Map 解析为 PipelineDefinition
     */
    private PipelineDefinition parsePipelineDefinition(Map<String, Object> map) {
        PipelineDefinition.PipelineDefinitionBuilder builder = PipelineDefinition.builder();
        builder.name(safeStr(map.get("name")));
        builder.description(safeStr(map.get("description")));

        // Parse steps
        List<Object> stepsRaw = safeList(map.get("steps"));
        List<PipelineStepDefinition> steps = new ArrayList<>();
        for (Object stepObj : stepsRaw) {
            if (stepObj instanceof Map) {
                steps.add(parseStepDefinition((Map<String, Object>) stepObj));
            }
        }
        builder.steps(steps);

        // Parse retry policy
        Map<String, Object> retryMap = safeMap(map.get("retry"));
        if (!retryMap.isEmpty()) {
            builder.retry(PipelineRetryPolicy.builder()
                    .maxAttempts(safeInt(retryMap.get("max_attempts"), 3))
                    .backoffMs(safeLong(retryMap.get("backoff_ms"), 5000L))
                    .build());
        } else {
            builder.retry(PipelineRetryPolicy.DEFAULT);
        }

        return builder.build();
    }

    private PipelineStepDefinition parseStepDefinition(Map<String, Object> map) {
        PipelineStepDefinition.PipelineStepDefinitionBuilder builder = PipelineStepDefinition.builder();
        builder.name(safeStr(map.get("name")));
        builder.role(safeStr(map.get("role")));
        builder.agent(safeStr(map.get("agent")));
        builder.output(safeStr(map.get("output")));
        builder.handoff(safeStr(map.get("handoff")));
        builder.onCritical(safeStr(map.get("on_critical")));
        builder.onSuccess(safeStr(map.get("on_success")));
        builder.onAllPass(safeStr(map.get("on_all_pass")));
        builder.onAnyFail(safeStr(map.get("on_any_fail")));
        builder.timeout(safeLong(map.get("timeout"), 300000L));
        builder.maxRetries(safeInt(map.get("max_retries"), 0));
        builder.prompt(safeStr(map.get("prompt")));

        // Parse agents list
        List<Object> agentsRaw = safeList(map.get("agents"));
        if (!agentsRaw.isEmpty()) {
            List<String> agents = new ArrayList<>();
            for (Object a : agentsRaw) {
                if (a instanceof String) agents.add((String) a);
            }
            builder.agents(agents);
        }

        return builder.build();
    }

    // ─── Type-safe helpers ────────────────────────────────────

    private String safeStr(Object obj) {
        return obj instanceof String ? (String) obj : null;
    }

    private int safeInt(Object obj, int defaultVal) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        return defaultVal;
    }

    private long safeLong(Object obj, long defaultVal) {
        if (obj instanceof Number) return ((Number) obj).longValue();
        return defaultVal;
    }

    @SuppressWarnings("unchecked")
    private List<Object> safeList(Object obj) {
        return obj instanceof List ? (List<Object>) obj : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object obj) {
        return obj instanceof Map ? (Map<String, Object>) obj : Map.of();
    }
}

