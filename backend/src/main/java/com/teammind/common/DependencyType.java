package com.teammind.common;

/**
 * 依赖类型 — Plugin 声明的运行时依赖分类
 */
public enum DependencyType {
    /** CLI 可执行文件（如 codex, claude） */
    EXECUTABLE,
    /** HTTP 服务（如本地 LLM provider） */
    SERVICE,
    /** 认证/配置文件 */
    AUTH,
    /** Git 仓库环境 */
    WORKSPACE,
    /** 外部 API Key / 环境变量 */
    ENVIRONMENT,
    /** 系统库（如 Python, Node.js） */
    SYSTEM_LIBRARY
}
