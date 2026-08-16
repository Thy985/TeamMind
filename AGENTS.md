# TeamMind AGENTS — 开发协议与约束

## Git 提交身份

所有 git commit 必须使用以下身份信息：

```
user.name  = Thy985
user.email = 1850833838@qq.com
```

此配置已通过 `git config --local` 设置，不需要每次手动指定。

> **原因：** TeamMind 是一个 AI Agent 协作系统，提交身份代表系统的"化身"。Thy985 是项目统一的作者标识，区分人类开发者与 Agent 自动生成的代码。

---

## Agent 分工

| Agent | 角色 | 工作目录 | 分支 |
|-------|------|---------|------|
| Agnes (本会话) | 实施 + 文档 | `D:\Projects\Active\TeamMind` (main) | `main` |
| Codex | 运行时实现 | `D:\DevWorkspaces\teammind-workspace\teammind-wt-codex` | `w7-codex-runtime` |
| Claude Code | 架构审查 | `D:\DevWorkspaces\teammind-workspace\teammind-wt-claude` | `w7-claude-review` |

## 工作流

1. **Codex** 在 worktree 中实现 → 提交到 `w7-codex-runtime`
2. **Claude Code** 审查 diff → 输出 review 到 `.agents/handoffs/`
3. **Agnes** 修复问题 → 合并到 `main`
4. **全量测试通过** → 才算完成

## 技术栈约束

- Java 17, Spring Boot 3.3.7, Lombok, JPA, SQLite
- Vue 3 + TypeScript + Naive UI (frontend)
- SnakeYAML (pipeline 定义)
- Maven (构建), pnpm (前端)

## 存储架构原则

> **Markdown/YAML 是项目记忆和配置；SQLite 是运行时事实。**

- `.agents/` — agent 任务、交接文档（Markdown）
- `backend/src/main/resources/` — YAML pipeline 定义
- `backend/src/main/resources/db/migration/` — SQLite schema
- `backend/target/` — 构建产物（不入库）
- `.cache/` — 运行时缓存

## 当前状态

- **Phase 1A** ✅ Runtime Contract (commit `a0f458fd`)
- **Phase 1B** ✅ Single-Agent Runtime (commit `7db54c0f`)
- **Phase 1C** ✅ Multi-Agent + Event Store + Mission Control (commits `39eb543f` → `5deec127`)
- **Phase 2** ✅ Mission Control live integration — API + WS + pipeline hooks (commits `afba917c` → `c28bef85`)

## 测试基线

- 后端单元测试：**302 pass, 0 failures**
- 前端构建：`✓ built in 1.31s (4354 modules)`
- E2E 测试：排除（需要真实 LLM provider 基础设施）
