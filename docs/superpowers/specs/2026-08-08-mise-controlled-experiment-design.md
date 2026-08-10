# mise 受控实验设计

- 日期：2026-08-08
- 范围：`TeamMind`、`campus-order/front/app`、`AI Agent-CLI`
- 目标：安装 mise，并验证其对现有 Node/Bun 项目的最小侵入式集成能力。

## Context

三个项目均已有依赖目录、包管理配置和运行脚本。TeamMind 与 campus-order 还包含 Java/Spring Boot 后端；AI Agent-CLI 以 Bun 为主要运行时。本实验不是一次完整工具链迁移，重点是验证 mise 配置识别、项目级诊断任务和回滚能力。

## Decision

采用最小受控实验：

1. 使用现有 Scoop 安装 mise。
2. 读取当前 Node/Bun 版本后，写入主版本约束，不凭空假设版本。
3. 只新增项目级 `mise.toml`：
   - `D:/Projects/Active/TeamMind/mise.toml`
   - `D:/Projects/Active/campus-order/front/app/mise.toml`
   - `D:/Projects/Active/AI Agent-CLI/mise.toml`
4. 每个配置提供只读 `info` 任务，输出项目路径、运行时和包管理器版本。
5. 不执行 `mise install`，不启用 shell activation，不执行全局 `mise trust -a`。
6. 不修改 `package.json`、lockfile、node_modules、Java/Maven/Gradle/Android 配置、数据库、Docker 或全局 PATH。

## Project-specific boundaries

### TeamMind

仅管理前端 Node 约束和诊断任务；后端 Java 17、Maven 和 Spring Boot 保持现状。

### campus-order

配置放在 `front/app`，避免把前端 Node 约束应用到仓库根目录的 Spring Boot 后端。后端 Java 17、Maven、MySQL、Redis 和 Docker 保持现状。

### AI Agent-CLI

仅声明 Bun 主版本和诊断任务；不修改 Bun/npm/pnpm lockfile，也不迁移全局 CLI。

## Verification

验证以下项目：

- `mise --version`
- `mise doctor`
- 三个配置可被 mise 读取
- 三个 `mise run info` 成功
- `package.json`、lockfile、依赖目录未被修改
- 不启动业务服务，不运行数据库或 Docker

## Rollback

实验产生的项目变更仅限三个 `mise.toml` 和本设计文档。回滚时删除对应 `mise.toml` 即可。由于不启用 activation、不修改 PATH，卸载 mise 不影响原有 Node/Bun 环境。

## Risks

- `mise.toml` 中的任务和环境指令具有执行能力，因此不对陌生配置执行 `mise trust -a`。
- Windows Shell 可能造成任务脚本差异，`info` 任务使用 Windows 兼容命令。
- 本实验不会验证 mise 自动下载运行时的能力；若后续需要，再单独审批第二阶段。
