package com.teammind.performance;

import com.teammind.entity.PerformanceRecord;
import com.teammind.entity.TaskExecution;
import com.teammind.common.TaskState;
import com.teammind.repository.PerformanceRecordRepository;
import com.teammind.repository.TaskExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerformanceTrackerTest {

    private PerformanceRecordRepository recordRepo;
    private TaskExecutionRepository taskRepo;
    private PerformanceTracker tracker;

    @BeforeEach
    void setUp() {
        recordRepo = mock(PerformanceRecordRepository.class);
        taskRepo = mock(TaskExecutionRepository.class);
        tracker = new PerformanceTracker(recordRepo, taskRepo);
    }

    @Test
    @DisplayName("任务完成后更新 PerformanceRecord")
    void onTaskCompletedUpdatesRecord() {
        TaskExecution exec = buildTask("t-1", "p-1", "codex", "LEAD", "implementation", true);
        when(taskRepo.findById("t-1")).thenReturn(java.util.Optional.of(exec));
        when(recordRepo.findFirstByProjectIdAndPluginIdAndRoleOrderByLastUpdatedDesc("p-1", "codex", "LEAD"))
                .thenReturn(java.util.Optional.empty());

        tracker.onTaskCompleted("t-1", null);

        var captor = org.mockito.ArgumentCaptor.forClass(PerformanceRecord.class);
        verify(recordRepo).save(captor.capture());
        PerformanceRecord saved = captor.getValue();
        assertEquals(1.0, saved.getSuccessRate());
        assertEquals(1, saved.getSampleSize());
    }

    @Test
    @DisplayName("失败任务记录 successRate=0")
    void failedTaskRecordsZeroRate() {
        TaskExecution exec = buildTask("t-2", "p-1", "claude-code", "LEAD", "implementation", false);
        when(taskRepo.findById("t-2")).thenReturn(java.util.Optional.of(exec));
        when(recordRepo.findFirstByProjectIdAndPluginIdAndRoleOrderByLastUpdatedDesc("p-1", "claude-code", "LEAD"))
                .thenReturn(java.util.Optional.empty());

        tracker.onTaskCompleted("t-2", null);

        var captor = org.mockito.ArgumentCaptor.forClass(PerformanceRecord.class);
        verify(recordRepo).save(captor.capture());
        assertEquals(0.0, captor.getValue().getSuccessRate());
    }

    @Test
    @DisplayName("不存在任务时不抛异常")
    void missingTaskDoesNotThrow() {
        when(taskRepo.findById("nonexistent")).thenReturn(java.util.Optional.empty());
        assertDoesNotThrow(() -> tracker.onTaskCompleted("nonexistent", null));
    }

    @Test
    @DisplayName("recalculateAll 处理空列表")
    void recalculateAllEmpty() {
        when(recordRepo.findByProjectId(anyString())).thenReturn(List.of());
        assertDoesNotThrow(() -> tracker.recalculateAll());
    }

    private TaskExecution buildTask(String id, String projectId, String agentId,
                                     String role, String taskType, boolean success) {
        TaskExecution exec = new TaskExecution();
        exec.setId(id);
        exec.setProjectId(projectId);
        exec.setCurrentAgentId(agentId);
        exec.setCurrentRole(role);
        exec.setTaskTypeId(taskType);
        exec.setState(success ? TaskState.DONE : TaskState.FAILED);
        exec.setDurationMs(5000L);
        exec.setRetryCount(0);
        exec.setCreatedAt(LocalDateTime.now());
        return exec;
    }
}
