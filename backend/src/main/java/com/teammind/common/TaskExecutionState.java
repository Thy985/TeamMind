package com.teammind.common;

/**
 * TaskExecution 内部执行状态（细粒度，区别于 Task 的宏观状态）
 *
 * 对应 Phase 1A Runtime Contract：
 *   NEW → PENDING → RUNNING ↔ PAUSED → DONE/FAILED/CANCELLED
 *          ↓                    ↓
 *      NEEDS_APPROVAL       PAUSE_REQUESTED
 *          ↓
 *        APPROVING → RUNNING
 *
 * 支持的操作：submit / start / pauseRequested / pauseComplete / resume /
 *            complete / fail / needsApproval / approve / deny / retry / cancel / recover
 */
public enum TaskExecutionState {
    /** 刚创建，尚未提交执行 */
    NEW,
    /** 已提交，等待执行资源 */
    PENDING,
    /** 正在执行中 */
    RUNNING,
    /** 用户请求暂停，等待当前 tool 完成 */
    PAUSE_REQUESTED,
    /** 已暂停（当前 tool 已完成） */
    PAUSED,
    /** 需要人工审批（Critical finding） */
    NEEDS_APPROVAL,
    /** 审批进行中 */
    APPROVING,
    /** 成功完成 */
    DONE,
    /** 失败（所有重试耗尽或不可恢复错误） */
    FAILED,
    /** 正在重试 */
    RETRYING,
    /** 用户取消 */
    CANCELLED,
    /** 审批被拒绝 */
    ABANDONED,
    /** 服务重启后，进程状态未知 */
    RECOVERING
}
