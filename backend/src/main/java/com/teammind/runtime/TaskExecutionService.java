package com.teammind.runtime;

import com.teammind.common.TaskState;
import com.teammind.entity.TaskExecution;
import com.teammind.repository.TaskExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * TaskExecutionService — 任务执行生命周期管理
 */
@Slf4j
@Component
public class TaskExecutionService {

    private final TaskExecutionRepository taskExecRepository;
    private final TaskStateMachine stateMachine;

    public TaskExecutionService(TaskExecutionRepository taskExecRepository,
                                 TaskStateMachine stateMachine) {
        this.taskExecRepository = taskExecRepository;
        this.stateMachine = stateMachine;
    }

    /**
     * 创建新任务执行记录
     */
    public TaskExecution create(String projectId, String objective, String taskTypeId) {
        String id = java.util.UUID.randomUUID().toString();
        TaskExecution exec = TaskExecution.builder()
                .id(id)
                .projectId(projectId)
                .objective(objective)
                .taskTypeId(taskTypeId)
                .state(TaskState.SUBMITTED)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .build();
        taskExecRepository.save(exec);
        stateMachine.initTask(id, objective, null);
        log.info("Task created: id={} project={} type={}", id, projectId, taskTypeId);
        return exec;
    }

    /**
     * 推进任务状态（处理事件）
     */
    public Optional<TaskState> advance(String taskId, com.teammind.common.EventType eventType,
                                        java.util.Map<String, Object> metadata) {
        Optional<TaskState> next = stateMachine.handleEvent(taskId, eventType, metadata);
        if (next.isPresent()) {
            taskExecRepository.findById(taskId).ifPresent(exec -> {
                exec.setState(next.get());
                taskExecRepository.save(exec);
            });
        }
        return next;
    }

    /**
     * 获取任务执行记录
     */
    public Optional<TaskExecution> get(String taskId) {
        return taskExecRepository.findById(taskId);
    }

    /**
     * 获取项目下的所有任务（按时间倒序）
     */
    public java.util.List<TaskExecution> listByProject(String projectId) {
        return taskExecRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    /**
     * 完成标记
     */
    public void markCompleted(String taskId, Double score, String summary) {
        taskExecRepository.findById(taskId).ifPresent(exec -> {
            exec.setState(TaskState.DONE);
            exec.setFinalScore(score);
            exec.setSummary(summary);
            exec.setCompletedAt(LocalDateTime.now());
            taskExecRepository.save(exec);
        });
    }

    /**
     * 失败标记
     */
    public void markFailed(String taskId, String error) {
        taskExecRepository.findById(taskId).ifPresent(exec -> {
            exec.setState(TaskState.FAILED);
            exec.setCompletedAt(LocalDateTime.now());
            taskExecRepository.save(exec);
        });
    }
}
