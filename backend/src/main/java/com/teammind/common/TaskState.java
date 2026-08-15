package com.teammind.common;

/**
 * Task 状态机状态（11 个状态 + 完整转移表）
 *
 * 状态定义见 docs/runtime/task-state-machine.md
 */
public enum TaskState {
    SUBMITTED,
    ORCHESTRATING,
    EXECUTING,
    VERIFYING,
    REVIEWING,
    NEEDS_APPROVAL,
    APPROVED,
    DONE,
    FAILED,
    RETRYING,
    ABANDONED,
    CANCELLED
}
