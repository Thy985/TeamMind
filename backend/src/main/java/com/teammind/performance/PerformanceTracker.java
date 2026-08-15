package com.teammind.performance;

import com.teammind.common.TaskState;
import com.teammind.entity.PerformanceRecord;
import com.teammind.entity.TaskExecution;
import com.teammind.repository.PerformanceRecordRepository;
import com.teammind.repository.TaskExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PerformanceTracker — 任务完成后更新 PerformanceRecord
 *
 * 触发条件：
 *   - Task 到达 DONE 状态
 *   - Evidence 验证通过
 *
 * 更新维度（四层 Profile）：
 *   1. Project + Role + Plugin（最精确）
 *   2. Project + Plugin + TaskType
 *   3. Project + Plugin（全局）
 *   4. 全局默认（无项目时）
 */
@Slf4j
@Component
public class PerformanceTracker {

    private final PerformanceRecordRepository recordRepo;
    private final TaskExecutionRepository taskRepo;

    public PerformanceTracker(PerformanceRecordRepository recordRepo,
                              TaskExecutionRepository taskRepo) {
        this.recordRepo = recordRepo;
        this.taskRepo = taskRepo;
    }

    /**
     * 任务完成时调用，更新 PerformanceRecord
     *
     * @param taskId    任务 ID
     * @param accepted  用户是否接受（null = 不确定，仅依赖 evidence）
     */
    public void onTaskCompleted(String taskId, Boolean accepted) {
        var exec = taskRepo.findById(taskId).orElse(null);
        if (exec == null) return;

        record(exec.getProjectId(), exec.getCurrentAgentId(), exec.getCurrentRole(),
                exec.getTaskTypeId(), exec.getState().name().equals("DONE"),
                exec.getDurationMs(), exec.getRetryCount(), accepted);

        log.debug("[PerformanceTracker] Updated profile for task={}, plugin={}, role={}",
                taskId, exec.getCurrentAgentId(), exec.getCurrentRole());
    }

    /**
     * 批量更新（从历史任务重算）
     */
    public void recalculateAll() {
        List<TaskExecution> completed = taskRepo.findAll().stream()
                .filter(e -> e.getState() == TaskState.DONE)
                .toList();

        for (TaskExecution exec : completed) {
            Boolean accepted = extractUserAccepted(exec.getEvidence());
            record(exec.getProjectId(), exec.getCurrentAgentId(), exec.getCurrentRole(),
                    exec.getTaskTypeId(), true,
                    exec.getDurationMs(), exec.getRetryCount(), accepted);
        }
        log.info("[PerformanceTracker] Recalculated {} completed tasks", completed.size());
    }

    // ─── Internal helpers ──────────────────────────────────────

    private void record(String projectId, String pluginId, String role,
                        String taskTypeId, boolean success,
                        Long durationMs, int retryCount, Boolean accepted) {
        if (pluginId == null || projectId == null) return;

        // 获取或创建记录（按最近更新时间取最新一条，避免重复）
        PerformanceRecord record = recordRepo
                .findFirstByProjectIdAndPluginIdAndRoleOrderByLastUpdatedDesc(projectId, pluginId, role)
                .orElseGet(() -> {
                    PerformanceRecord r = new PerformanceRecord();
                    r.setProjectId(projectId);
                    r.setPluginId(pluginId);
                    r.setRole(role);
                    r.setTaskTypeId(taskTypeId);
                    r.setSuccessRate(success ? 1.0 : 0.0);
                    r.setSampleSize(0);
                    r.setAvgIterations(0.0);
                    r.setAvgDurationMs(0L);
                    r.setCreatedAt(LocalDateTime.now());
                    r.setLastUpdated(LocalDateTime.now());
                    return r;
                });

        // 更新 taskTypeId（可能为空首次记录）
        if (taskTypeId != null) record.setTaskTypeId(taskTypeId);

        // 计算新值
        int sample = record.getSampleSize() + 1;
        double successRate = (record.getSuccessRate() * record.getSampleSize()
                + (success ? 1.0 : 0.0)) / sample;
        double avgIter = ((record.getAvgIterations() != null ? record.getAvgIterations() : 0)
                * record.getSampleSize() + retryCount) / sample;
        long avgDur = record.getAvgDurationMs() != null
                ? (record.getAvgDurationMs() * record.getSampleSize() + (durationMs != null ? durationMs : 0)) / sample
                : (durationMs != null ? durationMs : 0);

        record.setSuccessRate(successRate);
        record.setSampleSize(sample);
        record.setAvgIterations(avgIter);
        record.setAvgDurationMs(avgDur);
        if (accepted != null) record.setUserAcceptanceRate(accepted ? 1.0 : 0.0);
        record.setLastUpdated(LocalDateTime.now());

        recordRepo.save(record);
        log.debug("[PerformanceTracker] Updated record: project={}, plugin={}, rate={}",
                projectId, pluginId, successRate);
    }

    @SuppressWarnings("unchecked")
    private Boolean extractUserAccepted(Map<String, Object> evidence) {
        if (evidence == null) return null;
        Object passed = evidence.get("passed");
        if (passed instanceof Boolean b) return b;
        Object verified = evidence.get("verified");
        if (verified instanceof Boolean b) return b;
        return null;
    }
}
