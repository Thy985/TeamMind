package com.teammind.common;

/**
 * ExecutionStep 状态
 *
 * 每个 Pipeline 步骤的生命周期：
 *   PENDING → STARTED → RUNNING → COMPLETED / FAILED / SKIPPED
 */
public enum ExecutionStepState {
    /** 步骤已创建，尚未开始 */
    PENDING,
    /** 步骤已开始（Agent 进程已启动） */
    STARTED,
    /** 步骤正在执行（Agent 活跃运行） */
    RUNNING,
    /** 步骤成功完成 */
    COMPLETED,
    /** 步骤失败 */
    FAILED,
    /** 步骤被跳过（上游失败或条件不满足） */
    SKIPPED
}
