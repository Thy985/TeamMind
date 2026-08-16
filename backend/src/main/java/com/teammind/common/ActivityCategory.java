package com.teammind.common;

/**
 * ActivityCategory — Execution Ledger 的任务活动分类
 *
 * 每个 Category 对应一组 RuntimeEvent Type，
 * ActivityExtractor 将 Raw Trace（RuntimeEvent 列表）聚合为 Task Activity。
 *
 * 分类原则：
 *   - 用户视角：这个任务期间发生了什么？
 *   - 系统事实：来自事件数据，不是 Agent 自述
 *   - 可折叠：同一 Category 的事件可以合并展示
 */
public enum ActivityCategory {

    /**
     * 执行的命令（COMMAND_RUNNING + TOOL_CALLED + TOOL_RESULT）
     * 展示为折叠列表，只高亮重要命令
     */
    COMMANDS_EXECUTED,

    /**
     * 文件变更（FILE_CHANGED）
     * 展示为文件列表 + 变更统计
     */
    FILES_CHANGED,

    /**
     * 依赖变更（DEPENDENCY_CHANGED）
     * 展示为新增/移除的依赖列表
     */
    DEPENDENCIES_CHANGED,

    /**
     * 遇到的问题和解决方式（ERROR_CRITICAL + ERROR_RECOVERABLE）
     * 展示为 Incident / Resolution 卡片
     */
    INCIDENTS,

    /**
     * 验证结果（EVIDENCE_VERIFIED + TEST_PASSED + TEST_FAILED）
     * 展示为验证通过/失败摘要
     */
    VERIFICATIONS,

    /**
     * Agent 决策（DECISION_MADE + APPROVAL_GRANTED + APPROVAL_DENIED）
     * 展示为决策摘要
     */
    AGENT_DECISIONS
}
