package com.teammind.common;

/**
 * Agent 角色类型 — 团队成员的职责分类
 */
public enum AgentRole {
    /** 项目负责人：理解目标、调度其他成员、最终决策 */
    LEAD,
    /** 实现工程师：负责编码实现 */
    IMPLEMENTER,
    /** 安全审查：检查安全漏洞和权限问题 */
    SECURITY_GATE,
    /** 代码审查：检查代码质量和可维护性 */
    CODE_REVIEWER,
    /** 测试工程师：生成和运行测试 */
    TESTER,
    /** 架构师：架构设计和重构建议 */
    ARCHITECT,
    /** 研究者：调研和资料收集 */
    RESEARCHER,
    /** 文档工程师：生成和维护文档 */
    DOCUMENTER
}
