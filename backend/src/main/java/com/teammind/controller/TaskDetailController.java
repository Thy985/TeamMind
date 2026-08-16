package com.teammind.controller;

import com.teammind.common.ReadinessResult;
import com.teammind.common.ReadinessState;
import com.teammind.common.TaskActivity;
import com.teammind.entity.*;
import com.teammind.repository.*;
import com.teammind.runtime.ActivityExtractor;
import com.teammind.runtime.EventStoreService;
import com.teammind.runtime.ReadinessManager;
import com.teammind.websocket.WSEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TaskDetail API — Phase 2: Real-time TaskDetail integration
 *
 * Provides backend endpoints for the TaskDetailPanel:
 *   GET  /api/tasks/{id}              → full task state snapshot
 *   GET  /api/tasks/{id}/events        → event chain
 *   GET  /api/tasks/{id}/events?after=N → event replay after snapshot
 *   POST /api/tasks/{id}/pause         → pause execution
 *   POST /api/tasks/{id}/resume        → resume execution
 *   POST /api/tasks/{id}/cancel        → cancel execution
 *   POST /api/tasks/{id}/approve       → approve pending approval
 *   POST /api/tasks/{id}/retry         → retry failed execution
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskDetailController {

    private final TaskRepository taskRepo;
    private final TaskExecutionRepository executionRepo;
    private final ExecutionStepRepository stepRepo;
    private final AgentInvocationRepository invocationRepo;
    private final ArtifactRepository artifactRepo;
    private final EvidenceRepository evidenceRepo;
    private final ApprovalRequestRepository approvalRepo;
    private final EventStoreService eventStore;
    private final ReadinessManager readinessManager;
    private final WSEventPublisher wsPublisher;
    private final ActivityExtractor activityExtractor;

    public TaskDetailController(TaskRepository taskRepo,
                                TaskExecutionRepository executionRepo,
                                ExecutionStepRepository stepRepo,
                                AgentInvocationRepository invocationRepo,
                                ArtifactRepository artifactRepo,
                                EvidenceRepository evidenceRepo,
                                ApprovalRequestRepository approvalRepo,
                                EventStoreService eventStore,
                                ReadinessManager readinessManager,
                                WSEventPublisher wsPublisher,
                                ActivityExtractor activityExtractor) {
        this.taskRepo = taskRepo;
        this.executionRepo = executionRepo;
        this.stepRepo = stepRepo;
        this.invocationRepo = invocationRepo;
        this.artifactRepo = artifactRepo;
        this.evidenceRepo = evidenceRepo;
        this.approvalRepo = approvalRepo;
        this.eventStore = eventStore;
        this.readinessManager = readinessManager;
        this.wsPublisher = wsPublisher;
        this.activityExtractor = activityExtractor;
    }

    // ─── GET /api/tasks/{id} — Full state snapshot ────────────

    @GetMapping("/{taskId}")
    public Map<String, Object> getTaskDetail(@PathVariable String taskId) {
        Task task = taskRepo.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        List<TaskExecution> executions = executionRepo.findAll().stream()
                .filter(e -> e.getTaskId().equals(taskId))
                .toList();
        TaskExecution latestExec = executions.stream()
                .max(Comparator.comparingLong(e ->
                        e.getCreatedAt() != null ? e.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0))
                .orElse(null);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskId", task.getId());
        snapshot.put("projectId", task.getProjectId());
        snapshot.put("objective", task.getObjective());
        snapshot.put("taskState", task.getState().name());
        snapshot.put("createdAt", task.getCreatedAt());
        snapshot.put("startedAt", task.getStartedAt());
        snapshot.put("completedAt", task.getCompletedAt());

        if (latestExec != null) {
            snapshot.putAll(buildExecutionSnapshot(taskId, latestExec));
        }

        // Agent readiness
        Map<String, Object> readiness = new LinkedHashMap<>();
        if (readinessManager != null) {
            for (String agentId : List.of("codex", "claude-code")) {
                try {
                    ReadinessResult r = readinessManager.check(agentId);
                    readiness.put(agentId, Map.of(
                            "state", r.state().name(),
                            "score", r.readinessScore(),
                            "diagnosis", r.diagnosis()
                    ));
                } catch (Exception e) {
                    readiness.put(agentId, Map.of("state", "UNKNOWN", "error", e.getMessage()));
                }
            }
        }
        snapshot.put("readiness", readiness);

        return snapshot;
    }

    // ─── GET /api/tasks/{id}/events ───────────────────────────

    @GetMapping("/{taskId}/events")
    public List<Map<String, Object>> getEvents(
            @PathVariable String taskId,
            @RequestParam(required = false, defaultValue = "0") Long after) {
        List<com.teammind.entity.RuntimeEvent> events;
        if (after > 0) {
            events = eventStore.getEventsAfter(taskId, after);
        } else {
            events = eventStore.getEventChain(taskId);
        }
        return events.stream().map(this::eventToMap).collect(Collectors.toList());
    }

    // ─── Control endpoints ────────────────────────────────────

    @PostMapping("/{taskId}/pause")
    public Map<String, Object> pause(@PathVariable String taskId) {
        log.info("Pause requested for task {}", taskId);
        wsPublisher.publishLog(taskId, "control", "system", "Pause requested");
        return Map.of("taskId", taskId, "action", "pause", "status", "requested");
    }

    @PostMapping("/{taskId}/resume")
    public Map<String, Object> resume(@PathVariable String taskId) {
        log.info("Resume requested for task {}", taskId);
        wsPublisher.publishLog(taskId, "control", "system", "Resume requested");
        return Map.of("taskId", taskId, "action", "resume", "status", "requested");
    }

    @PostMapping("/{taskId}/cancel")
    public Map<String, Object> cancel(@PathVariable String taskId) {
        log.info("Cancel requested for task {}", taskId);
        wsPublisher.publishLog(taskId, "control", "system", "Cancel requested");
        return Map.of("taskId", taskId, "action", "cancel", "status", "requested");
    }

    @PostMapping("/{taskId}/approve")
    public Map<String, Object> approve(@PathVariable String taskId,
                                       @RequestBody Map<String, Object> body) {
        String decision = body.getOrDefault("decision", "approved").toString();
        log.info("Approval decision for task {}: {}", taskId, decision);
        wsPublisher.publishLog(taskId, "approval", "system", "Approval: " + decision);
        return Map.of("taskId", taskId, "action", "approve", "decision", decision);
    }

    @PostMapping("/{taskId}/retry")
    public Map<String, Object> retry(@PathVariable String taskId) {
        log.info("Retry requested for task {}", taskId);
        wsPublisher.publishLog(taskId, "control", "system", "Retry requested");
        return Map.of("taskId", taskId, "action", "retry", "status", "requested");
    }

    // ─── GET /api/tasks/{id}/activity — Execution Ledger summary ───

    @GetMapping("/{taskId}/activity")
    public Map<String, Object> getActivity(@PathVariable String taskId) {
        TaskActivity activity = activityExtractor.extract(taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("extractedAt", activity.extractedAt().toString());
        result.put("commandsExecuted", activity.commandsExecuted().stream()
                .map(c -> Map.<String, Object>of(
                        "command", c.command(),
                        "durationMs", c.durationMs(),
                        "exitCode", c.exitCode(),
                        "startedAt", c.startedAt().toString()))
                .toList());
        result.put("filesChanged", activity.filesChanged());
        result.put("dependenciesChanged", activity.dependenciesChanged().stream()
                .map(d -> Map.<String, Object>of("action", d.action().name(), "name", d.name(), "version", d.version()))
                .toList());
        result.put("incidents", activity.incidents().stream()
                .map(i -> Map.<String, Object>of(
                        "type", i.type(),
                        "description", i.description() != null ? i.description() : "",
                        "resolved", i.resolved(),
                        "resolvedBy", i.resolvedBy() != null ? i.resolvedBy() : ""))
                .toList());
        result.put("verifications", activity.verifications().stream()
                .map(v -> Map.<String, Object>of("type", v.type(), "passed", v.passed(), "failed", v.failed()))
                .toList());
        result.put("agentDecisions", activity.agentDecisions().stream()
                .map(d -> Map.<String, Object>of("type", d.type(), "content", d.content() != null ? d.content() : ""))
                .toList());
        return result;
    }

    // ─── Helpers ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildExecutionSnapshot(String taskId, TaskExecution exec) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("executionId", exec.getId());
        snap.put("executionState", exec.getExecutionState() != null ? exec.getExecutionState().name() : "UNKNOWN");
        snap.put("currentStep", exec.getCurrentStepName());
        snap.put("agentId", exec.getAgentId());
        snap.put("attemptNumber", exec.getAttemptNumber());
        snap.put("durationMs", exec.getDurationMs());
        snap.put("summary", exec.getSummary());
        snap.put("errorReason", exec.getErrorReason());
        snap.put("startedAt", exec.getStartedAt());
        snap.put("completedAt", exec.getCompletedAt());

        // Steps
        List<ExecutionStep> steps = stepRepo.findAll().stream()
                .filter(s -> s.getExecutionId().equals(exec.getId()))
                .sorted(Comparator.comparing(s -> s.getStartedAt() != null ? s.getStartedAt() : java.time.LocalDateTime.MIN))
                .toList();
        snap.put("steps", steps.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("stepName", s.getStepName());
            m.put("agentId", s.getAgentId());
            m.put("role", s.getRole());
            m.put("state", s.getState() != null ? s.getState().name() : "UNKNOWN");
            m.put("prompt", s.getPrompt());
            m.put("outputSummary", s.getOutputSummary());
            m.put("durationMs", s.getDurationMs());
            m.put("startedAt", s.getStartedAt());
            m.put("completedAt", s.getCompletedAt());
            return m;
        }).toList());

        // Collect invocation IDs from steps
        List<String> stepIds = steps.stream().map(ExecutionStep::getId).toList();
        List<String> invocationIds = invocationRepo.findAll().stream()
                .filter(i -> stepIds.contains(i.getStepId()))
                .map(AgentInvocation::getId)
                .toList();

        // Artifacts
        List<Artifact> artifacts = artifactRepo.findAll().stream()
                .filter(a -> invocationIds.contains(a.getInvocationId()))
                .toList();
        snap.put("artifacts", artifacts.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("type", a.getType());
            m.put("summary", a.getSummary());
            m.put("data", a.getData());
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).toList());

        // Evidence
        List<Evidence> evidences = evidenceRepo.findAll().stream()
                .filter(e -> invocationIds.contains(e.getInvocationId()))
                .toList();
        snap.put("evidence", evidences.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("type", e.getType() != null ? e.getType().name() : "UNKNOWN");
            m.put("status", e.getStatus() != null ? e.getStatus().name() : "UNKNOWN");
            m.put("description", e.getDescription());
            return m;
        }).toList());

        // Pending approvals
        List<ApprovalRequest> approvals = approvalRepo.findAll().stream()
                .filter(a -> a.getTaskId().equals(taskId)
                        && (a.getResult() == null || a.getResult() == ApprovalRequest.ApprovalResult.PENDING))
                .toList();
        snap.put("pendingApprovals", approvals.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("pluginId", a.getPluginId());
            m.put("question", a.getQuestion());
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).toList());

        // Snapshot version (event count)
        long eventCount = eventStore.getEventChain(taskId).size();
        snap.put("snapshotVersion", eventCount);

        return snap;
    }

    private Map<String, Object> eventToMap(com.teammind.entity.RuntimeEvent event) {
        return Map.of(
                "id", event.getId(),
                "type", event.getType().name(),
                "taskId", event.getTaskId(),
                "executionId", event.getExecutionId(),
                "stepId", event.getStepId(),
                "pluginId", event.getPluginId(),
                "role", event.getRole(),
                "payload", event.getPayload(),
                "tier", event.getTier().name(),
                "createdAt", event.getCreatedAt()
        );
    }
}
