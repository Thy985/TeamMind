package com.teammind.runtime;

import com.teammind.common.*;
import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task 状态机 — 11 个状态 + 完整转移表
 *
 * 状态转移逻辑：
 *   SUBMITTED → ORCHESTRATING → EXECUTING → VERIFYING → DONE
 *                                         ↓              ↑
 *                                     REVIEWING ←───────┘
 *                                         ↓
 *                                   NEEDS_APPROVAL → APPROVED
 *                                         ↓
 *                                      FAILED → RETRYING → ABANDONED
 *                                         ↓
 *                                      CANCELLED
 */
@Slf4j
@Component
public class TaskStateMachine {

    private final EventBus eventBus;
    private final PolicyEngine policyEngine;

    /** taskId → currentState */
    private final Map<String, TaskState> stateStore = new ConcurrentHashMap<>();

    /** taskId → lastEvent */
    private final Map<String, TeamMindEvent> lastEventStore = new ConcurrentHashMap<>();

    public TaskStateMachine(EventBus eventBus, PolicyEngine policyEngine) {
        this.eventBus = eventBus;
        this.policyEngine = policyEngine;
    }

    /**
     * 初始化任务状态
     */
    public void initTask(String taskId, String objective, String teamConfig) {
        stateStore.put(taskId, TaskState.SUBMITTED);
        log.info("Task initialized: id={} objective={}", taskId, objective);
    }

    /**
     * 获取当前状态
     */
    public TaskState getState(String taskId) {
        return stateStore.get(taskId);
    }

    /**
     * 处理事件并触发状态转移
     *
     * @return 新状态（如果发生转移），否则返回原状态
     */
    public Optional<TaskState> handleEvent(String taskId, EventType eventType,
                                            Map<String, Object> metadata) {
        TaskState currentState = stateStore.get(taskId);
        if (currentState == null) {
            log.warn("Task not found: {}", taskId);
            return Optional.empty();
        }

        TaskState nextState = decideNextState(currentState, eventType, metadata);
        if (nextState != null && nextState != currentState) {
            log.info("Task {} state transition: {} → {}", taskId, currentState, nextState);
            stateStore.put(taskId, nextState);
            lastEventStore.put(taskId, buildEvent(eventType, taskId, nextState));
            eventBus.emit(buildEvent(eventType, taskId, nextState));
            return Optional.of(nextState);
        }
        return Optional.empty();
    }

    /**
     * 状态转移决策核心逻辑
     */
    private TaskState decideNextState(TaskState current, EventType event, Map<String, Object> metadata) {
        return switch (current) {
            case SUBMITTED -> handleSubmitted(event);
            case ORCHESTRATING -> handleOrchestrating(event, metadata);
            case EXECUTING -> handleExecuting(event, metadata);
            case VERIFYING -> handleVerifying(event, metadata);
            case REVIEWING -> handleReviewing(event, metadata);
            case NEEDS_APPROVAL -> handleNeedsApproval(event, metadata);
            case APPROVED -> handleApproved(event, metadata);
            case RETRYING -> handleRetrying(event, metadata);
            default -> null; // 终态不接受转移
        };
    }

    // ─── SUBMITTED ─────────────────────────────
    private TaskState handleSubmitted(EventType event) {
        if (event == EventType.TASK_STARTED) return TaskState.ORCHESTRATING;
        if (event == EventType.TASK_CANCELLED) return TaskState.CANCELLED;
        return null;
    }

    // ─── ORCHESTRATING ──────────────────────────
    private TaskState handleOrchestrating(EventType event, Map<String, Object> metadata) {
        if (event == EventType.ROUTING_DECIDED) return TaskState.EXECUTING;
        if (event == EventType.TASK_CANCELLED) return TaskState.CANCELLED;
        if (event == EventType.ERROR_CRITICAL) return TaskState.FAILED;
        return null;
    }

    // ─── EXECUTING ─────────────────────────────
    private TaskState handleExecuting(EventType event, Map<String, Object> metadata) {
        return switch (event) {
            case TASK_COMPLETED -> TaskState.VERIFYING;
            case TASK_CANCELLED -> TaskState.CANCELLED;
            case AGENT_FAILED -> TaskState.FAILED;
            case ERROR_CRITICAL -> TaskState.FAILED;
            case HANDOFF_REQUESTED -> {
                String nextRole = (String) metadata.get("toRole");
                if ("REVIEW".equals(nextRole) || "REVIEWER".equals(nextRole)
                        || "SECURITY_GATE".equals(nextRole)) {
                    yield TaskState.REVIEWING;
                } else {
                    yield TaskState.EXECUTING; // 同角色移交，保持 EXECUTING
                }
            }
            case FINDING_CREATED -> {
                Object severity = metadata.get("severity");
                if ("CRITICAL".equals(severity)) {
                    yield TaskState.NEEDS_APPROVAL;
                }
                yield TaskState.REVIEWING;
            }
            case TEST_FAILED -> TaskState.VERIFYING; // 进入验证，可能触发重试
            case EVIDENCE_FAILED -> TaskState.RETRYING;
            default -> null;
        };
    }

    // ─── VERIFYING ─────────────────────────────
    private TaskState handleVerifying(EventType event, Map<String, Object> metadata) {
        return switch (event) {
            case EVIDENCE_VERIFIED -> TaskState.DONE;
            case EVIDENCE_FAILED -> TaskState.RETRYING;
            case FINDING_CREATED -> TaskState.REVIEWING;
            case TASK_CANCELLED -> TaskState.CANCELLED;
            default -> null;
        };
    }

    // ─── REVIEWING ─────────────────────────────
    private TaskState handleReviewing(EventType event, Map<String, Object> metadata) {
        return switch (event) {
            case REVIEW_APPROVED -> TaskState.APPROVED;
            case REVIEW_REJECTED -> TaskState.EXECUTING; // 返回执行者修复
            case FINDING_CREATED -> {
                Object severity = metadata.get("severity");
                if ("CRITICAL".equals(severity)) {
                    yield TaskState.NEEDS_APPROVAL;
                }
                yield TaskState.EXECUTING; // 一般发现 → 返回修复
            }
            case TASK_CANCELLED -> TaskState.CANCELLED;
            default -> null;
        };
    }

    // ─── NEEDS_APPROVAL ────────────────────────
    private TaskState handleNeedsApproval(EventType event, Map<String, Object> metadata) {
        return switch (event) {
            case APPROVAL_GRANTED, APPROVAL_AUTO_APPROVED -> TaskState.APPROVED;
            case APPROVAL_DENIED -> TaskState.CANCELLED;
            default -> null;
        };
    }

    // ─── APPROVED ──────────────────────────────
    private TaskState handleApproved(EventType event, Map<String, Object> metadata) {
        return switch (event) {
            case TASK_COMPLETED -> TaskState.VERIFYING;
            case TASK_CANCELLED -> TaskState.CANCELLED;
            default -> null;
        };
    }

    // ─── RETRYING ──────────────────────────────
    private TaskState handleRetrying(EventType event, Map<String, Object> metadata) {
        Integer retryCount = (Integer) metadata.get("retryCount");
        Integer maxRetries = (Integer) metadata.get("maxRetries");
        int max = maxRetries != null ? maxRetries : 3;

        return switch (event) {
            case TASK_COMPLETED -> TaskState.VERIFYING;
            case TASK_FAILED -> {
                if (retryCount != null && retryCount >= max) {
                    yield TaskState.ABANDONED;
                }
                yield TaskState.EXECUTING; // 继续重试
            }
            case ERROR_CRITICAL -> TaskState.FAILED;
            case TASK_CANCELLED -> TaskState.CANCELLED;
            default -> null;
        };
    }

    /**
     * 手动设置状态（用于紧急操作）
     */
    public void setState(String taskId, TaskState newState) {
        stateStore.put(taskId, newState);
        log.warn("Manual state change: {} → {}", taskId, newState);
    }

    /**
     * 取消任务
     */
    public TaskState cancel(String taskId) {
        stateStore.put(taskId, TaskState.CANCELLED);
        eventBus.emit(TeamMindEvent.of(EventType.TASK_CANCELLED, taskId, "system", "system"));
        return TaskState.CANCELLED;
    }

    /**
     * 构建事件
     */
    private TeamMindEvent buildEvent(EventType type, String taskId, TaskState state) {
        return TeamMindEvent.of(type, taskId, "system", "SYSTEM", Map.of(
                "from_state", state.name(),
                "to_state", state.name(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * 获取任务历史事件
     */
    public List<TeamMindEvent> getHistory(String taskId) {
        // TODO: 持久化存储
        return List.of();
    }
}
