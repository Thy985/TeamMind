package com.teammind.runtime;

import com.teammind.common.TaskExecutionState;
import com.teammind.entity.TaskExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * TaskExecution 细粒度状态机 — Phase 1A Runtime Contract
 *
 * 管理单次执行尝试的内部状态，支持 Pause / Resume / Cancel / Retry / Approval。
 *
 * 状态机：
 *   NEW → PENDING → RUNNING ↔ PAUSED → DONE/FAILED/CANCELLED
 *          ↓                    ↓
 *      NEEDS_APPROVAL       PAUSE_REQUESTED
 *          ↓
 *        APPROVING → RUNNING
 *
 * 与 TaskStateMachine（宏观状态）分离：
 *   TaskStateMachine 响应 EventBus 事件
 *   TaskExecutionStateMachine 由命令层（Command/API）直接调用
 */
@Slf4j
@Component
public class TaskExecutionStateMachine {

    /** from → (command → to) */
    private static final Map<TaskExecutionState, Map<String, TaskExecutionState>> TRANSITIONS = buildTransitions();

    private static Map<TaskExecutionState, Map<String, TaskExecutionState>> buildTransitions() {
        Map<TaskExecutionState, Map<String, TaskExecutionState>> m = new java.util.HashMap<>();

        // NEW → PENDING
        m.put(TaskExecutionState.NEW, Map.of("submit", TaskExecutionState.PENDING));

        // PENDING → RUNNING / CANCELLED
        Map<String, TaskExecutionState> pendingTrans = new java.util.HashMap<>();
        pendingTrans.put("start", TaskExecutionState.RUNNING);
        pendingTrans.put("cancel", TaskExecutionState.CANCELLED);
        m.put(TaskExecutionState.PENDING, pendingTrans);

        // RUNNING → PAUSE_REQUESTED / DONE / FAILED / NEEDS_APPROVAL / CANCELLED
        Map<String, TaskExecutionState> runningTrans = new java.util.HashMap<>();
        runningTrans.put("pauseRequested", TaskExecutionState.PAUSE_REQUESTED);
        runningTrans.put("complete", TaskExecutionState.DONE);
        runningTrans.put("fail", TaskExecutionState.FAILED);
        runningTrans.put("needsApproval", TaskExecutionState.NEEDS_APPROVAL);
        runningTrans.put("cancel", TaskExecutionState.CANCELLED);
        m.put(TaskExecutionState.RUNNING, runningTrans);

        // PAUSE_REQUESTED → PAUSED / CANCELLED
        Map<String, TaskExecutionState> pauseReqTrans = new java.util.HashMap<>();
        pauseReqTrans.put("pauseComplete", TaskExecutionState.PAUSED);
        pauseReqTrans.put("cancel", TaskExecutionState.CANCELLED);
        m.put(TaskExecutionState.PAUSE_REQUESTED, pauseReqTrans);

        // PAUSED → RUNNING / CANCELLED
        Map<String, TaskExecutionState> pausedTrans = new java.util.HashMap<>();
        pausedTrans.put("resume", TaskExecutionState.RUNNING);
        pausedTrans.put("cancel", TaskExecutionState.CANCELLED);
        m.put(TaskExecutionState.PAUSED, pausedTrans);

        // NEEDS_APPROVAL → APPROVING / ABANDONED / CANCELLED
        Map<String, TaskExecutionState> approvalTrans = new java.util.HashMap<>();
        approvalTrans.put("approve", TaskExecutionState.APPROVING);
        approvalTrans.put("deny", TaskExecutionState.ABANDONED);
        approvalTrans.put("cancel", TaskExecutionState.CANCELLED);
        m.put(TaskExecutionState.NEEDS_APPROVAL, approvalTrans);

        // APPROVING → RUNNING / CANCELLED
        Map<String, TaskExecutionState> approvingTrans = new java.util.HashMap<>();
        approvingTrans.put("approvalProceed", TaskExecutionState.RUNNING);
        approvingTrans.put("cancel", TaskExecutionState.CANCELLED);
        m.put(TaskExecutionState.APPROVING, approvingTrans);

        // FAILED → RETRYING / CANCELLED
        Map<String, TaskExecutionState> failedTrans = new java.util.HashMap<>();
        failedTrans.put("retry", TaskExecutionState.RETRYING);
        failedTrans.put("cancel", TaskExecutionState.CANCELLED);
        m.put(TaskExecutionState.FAILED, failedTrans);

        // RETRYING → PENDING / CANCELLED
        Map<String, TaskExecutionState> retryingTrans = new java.util.HashMap<>();
        retryingTrans.put("startRetry", TaskExecutionState.PENDING);
        retryingTrans.put("cancel", TaskExecutionState.CANCELLED);
        m.put(TaskExecutionState.RETRYING, retryingTrans);

        // CANCELLED / DONE / ABANDONED → terminal (no outgoing transitions)
        m.put(TaskExecutionState.CANCELLED, Map.of());
        m.put(TaskExecutionState.DONE, Map.of());
        m.put(TaskExecutionState.ABANDONED, Map.of());

        return Map.copyOf(m);
    }

    /**
     * 验证并应用状态转移。调用方需自行持久化。
     *
     * @param execution 当前执行记录
     * @param command   转移命令（如 "start", "pauseRequested", "complete"）
     * @return 新状态
     * @throws IllegalStateException 如果转移非法
     */
    public TaskExecutionState transition(TaskExecution execution, String command) {
        TaskExecutionState from = execution.getExecutionState();
        if (from == null) {
            // 首次使用：从宏观状态推断
            from = inferFromMacroState(execution.getState());
        }

        Map<String, TaskExecutionState> cmds = TRANSITIONS.get(from);
        if (cmds == null || !cmds.containsKey(command)) {
            throw new IllegalStateException(
                "Illegal transition for TaskExecution " + execution.getId()
                + ": " + from + " --[" + command + "]--> ?");
        }

        TaskExecutionState to = cmds.get(command);
        applyTransition(execution, from, to, command);
        log.debug("TaskExecution {} : {} → {} (cmd={})", execution.getId(), from, to, command);
        return to;
    }

    /**
     * 仅检查合法性，不修改状态。
     */
    public boolean canTransition(TaskExecution execution, String command) {
        TaskExecutionState from = execution.getExecutionState();
        if (from == null) {
            from = inferFromMacroState(execution.getState());
        }
        Map<String, TaskExecutionState> cmds = TRANSITIONS.get(from);
        return cmds != null && cmds.containsKey(command);
    }

    /**
     * 获取当前状态可用的命令列表（供 UI 渲染 ControlButtons）。
     */
    public Set<String> getAvailableCommands(TaskExecution execution) {
        TaskExecutionState from = execution.getExecutionState();
        if (from == null) {
            from = inferFromMacroState(execution.getState());
        }
        Map<String, TaskExecutionState> cmds = TRANSITIONS.get(from);
        return cmds != null ? Set.copyOf(cmds.keySet()) : Set.of();
    }

    // ─── private helpers ──────────────────────────────────────

    private void applyTransition(TaskExecution execution,
                                 TaskExecutionState from,
                                 TaskExecutionState to,
                                 String command) {
        execution.setExecutionState(to);

        // 时间戳：启动时记录 startedAt，结束时记录 completedAt
        if (command.equals("start") || command.equals("resume") || command.equals("startRetry")) {
            if (execution.getStartedAt() == null) {
                execution.setStartedAt(LocalDateTime.now());
            }
        }
        if (command.equals("complete") || command.equals("fail")
                || command.equals("deny") || command.equals("cancel")) {
            execution.setCompletedAt(LocalDateTime.now());
        }
    }

    /**
     * 从宏观 TaskState 推断 ExecutionState（反向兼容旧数据）。
     */
    private TaskExecutionState inferFromMacroState(com.teammind.common.TaskState macroState) {
        if (macroState == null) return TaskExecutionState.NEW;
        return switch (macroState) {
            case SUBMITTED      -> TaskExecutionState.PENDING;
            case ORCHESTRATING  -> TaskExecutionState.PENDING;
            case EXECUTING      -> TaskExecutionState.RUNNING;
            case VERIFYING      -> TaskExecutionState.RUNNING;
            case REVIEWING      -> TaskExecutionState.RUNNING;
            case NEEDS_APPROVAL -> TaskExecutionState.NEEDS_APPROVAL;
            case APPROVED       -> TaskExecutionState.APPROVING;
            case DONE           -> TaskExecutionState.DONE;
            case FAILED         -> TaskExecutionState.FAILED;
            case RETRYING       -> TaskExecutionState.RETRYING;
            case CANCELLED      -> TaskExecutionState.CANCELLED;
            case ABANDONED      -> TaskExecutionState.ABANDONED;
        };
    }
}
