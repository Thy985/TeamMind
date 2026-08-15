package com.teammind.controller;

import com.teammind.common.ControlMode;
import com.teammind.entity.PerformanceRecord;
import com.teammind.entity.TaskExecution;
import com.teammind.performance.DriftDetector;
import com.teammind.performance.PerformanceTracker;
import com.teammind.performance.TeamRecommender;
import com.teammind.repository.PerformanceRecordRepository;
import com.teammind.repository.TaskExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Mission Control API — 4 面板数据的 REST 入口
 *
 * Panel 1: Project Overview  → GET /api/mission-control/project/{id}/overview
 * Panel 2: Live Execution    → GET /api/mission-control/project/{id}/running
 * Panel 3: Performance       → GET /api/mission-control/project/{id}/profile
 * Panel 4: Recommendations   → GET /api/mission-control/project/{id}/recommendation
 *                                         GET /api/mission-control/project/{id}/drift
 *                                         POST /api/mission-control/project/{id}/recalculate
 */
@Slf4j
@RestController
@RequestMapping("/api/mission-control")
@CrossOrigin(origins = "*")
public class MissionControlController {

    private final PerformanceTracker tracker;
    private final DriftDetector driftDetector;
    private final TeamRecommender recommender;
    private final TaskExecutionRepository taskRepo;
    private final PerformanceRecordRepository perfRepo;

    public MissionControlController(PerformanceTracker tracker,
                                     DriftDetector driftDetector,
                                     TeamRecommender recommender,
                                     TaskExecutionRepository taskRepo,
                                     PerformanceRecordRepository perfRepo) {
        this.tracker = tracker;
        this.driftDetector = driftDetector;
        this.recommender = recommender;
        this.taskRepo = taskRepo;
        this.perfRepo = perfRepo;
    }

    // ═══════════════════════════════════════════════════════════
    // Panel 1: Project Overview
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/project/{projectId}/overview")
    public Map<String, Object> getProjectOverview(@PathVariable String projectId) {
        List<TaskExecution> tasks = taskRepo.findByProjectIdOrderByCreatedAtDesc(projectId);
        long total = tasks.size();
        long done = tasks.stream().filter(t -> t.getState().name().equals("DONE")).count();
        long failed = tasks.stream().filter(t -> t.getState().name().equals("FAILED")).count();
        long pending = tasks.stream().filter(t -> {
            String s = t.getState().name();
            return s.equals("SUBMITTED") || s.equals("ORCHESTRATING")
                    || s.equals("EXECUTING") || s.equals("VERIFYING");
        }).count();

        long avgDur = (long) tasks.stream()
                .filter(t -> t.getDurationMs() != null)
                .mapToLong(TaskExecution::getDurationMs)
                .average().orElse(0);

        return Map.of(
                "projectId", projectId,
                "totalTasks", total,
                "completed", done,
                "failed", failed,
                "pending", pending,
                "successRate", total > 0 ? Math.round(done * 10000.0 / total) / 100.0 : 0.0,
                "avgDurationMs", avgDur,
                "controlMode", ControlMode.SUPERVISED.name()
        );
    }

    // ═══════════════════════════════════════════════════════════
    // Panel 2: Live Execution
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/project/{projectId}/running")
    public List<Map<String, Object>> getRunningTasks(@PathVariable String projectId) {
        String[] active = {"ORCHESTRATING", "EXECUTING", "VERIFYING", "NEEDS_APPROVAL"};
        Set<String> activeSet = new HashSet<>(Arrays.asList(active));
        return taskRepo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(t -> activeSet.contains(t.getState().name()))
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/project/{projectId}/history")
    public List<Map<String, Object>> getHistory(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "20") int limit) {
        return taskRepo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .sorted(Comparator.comparing(
                        (TaskExecution e) -> e.getCreatedAt() != null ? e.getCreatedAt() : LocalDateTime.MAX,
                        Comparator.reverseOrder()))
                .limit(limit)
                .map(this::toDto)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════
    // Panel 3: Performance Profile
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/project/{projectId}/profile")
    public Map<String, Object> getProfile(@PathVariable String projectId) {
        DriftDetector.TrendSummary trends = driftDetector.getTrendSummary(projectId);
        List<PerformanceRecord> records = perfRepo.findByProjectId(projectId);

        Map<String, List<Map<String, Object>>> byRole = new LinkedHashMap<>();
        for (PerformanceRecord r : records) {
            String role = r.getRole() != null ? r.getRole() : "UNKNOWN";
            byRole.computeIfAbsent(role, k -> new ArrayList<>()).add(Map.of(
                    "pluginId", r.getPluginId(),
                    "taskTypeId", r.getTaskTypeId(),
                    "successRate", r.getSuccessRate(),
                    "sampleSize", r.getSampleSize(),
                    "avgDurationMs", r.getAvgDurationMs(),
                    "avgIterations", r.getAvgIterations()
            ));
        }

        return Map.of(
                "trend", trends,
                "byRole", byRole,
                "totalRecords", records.size()
        );
    }

    // ═══════════════════════════════════════════════════════════
    // Panel 4: Recommendations & Drift
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/project/{projectId}/recommendation")
    public Optional<TeamRecommender.TeamRecommendation> getRecommendation(
            @PathVariable String projectId) {
        return recommender.recommend(projectId);
    }

    @GetMapping("/project/{projectId}/drift")
    public List<DriftDetector.DriftAlert> getDriftAlerts(@PathVariable String projectId) {
        return driftDetector.detect(projectId);
    }

    @PostMapping("/project/{projectId}/recalculate")
    public Map<String, Object> recalculate(@PathVariable String projectId) {
        tracker.recalculateAll();
        List<DriftDetector.DriftAlert> drift = driftDetector.detect(projectId);
        Optional<TeamRecommender.TeamRecommendation> rec = recommender.recommend(projectId);

        Map<String, Object> result = new HashMap<>();
        result.put("driftAlerts", drift != null ? drift : Collections.emptyList());
        result.put("recommendation", rec != null ? rec.orElse(null) : null);
        result.put("message", "Recalculation complete");
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    // Control Mode
    // ═══════════════════════════════════════════════════════════

    @PutMapping("/project/{projectId}/control-mode")
    public Map<String, Object> setControlMode(
            @PathVariable String projectId,
            @RequestBody Map<String, String> body) {
        String modeStr = body.get("controlMode");
        try {
            ControlMode mode = ControlMode.valueOf(modeStr.toUpperCase());
            return Map.of("projectId", projectId, "controlMode", mode.name(), "success", true);
        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid control mode: " + modeStr, "success", false);
        }
    }

    @GetMapping("/project/{projectId}/control-mode")
    public Map<String, Object> getControlMode(@PathVariable String projectId) {
        return Map.of("projectId", projectId, "controlMode", ControlMode.SUPERVISED.name());
    }

    // ─── Internal helpers ──────────────────────────────────────

    private Map<String, Object> toDto(TaskExecution exec) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", exec.getId());
        dto.put("objective", exec.getObjective());
        dto.put("state", exec.getState().name());
        dto.put("currentAgentId", exec.getCurrentAgentId());
        dto.put("currentRole", exec.getCurrentRole());
        dto.put("retryCount", exec.getRetryCount());
        dto.put("durationMs", exec.getDurationMs());
        dto.put("createdAt", exec.getCreatedAt());
        dto.put("completedAt", exec.getCompletedAt());
        return dto;
    }
}
