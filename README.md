# 🧠 TeamMind

**Project AI Team Runtime**
*One Project. One AI Team. Each Agent with its own Philosophy.*

> 给每个代码项目配置一支由不同 AI Agent 组成的有哲学互补的工程团队，
> 让它们围绕共享项目状态真正协同完成任务，并随项目运行逐渐进化。

---

## 它解决什么问题

你装了 Claude Code、Codex、Aider 三个 Agent CLI，但每次开发：

- 复杂任务用 Claude 写代码 → 手动复制到 Codex 让它审查 → 再回到 Claude 修改 → 再用 Aider 补测试
- 不同 CLI 之间的对话完全割裂，每次都从头讲
- 不知道哪个 Agent 应该负责什么，出问题不知道找谁
- 不知道哪个 Agent 在你这个项目里表现最好

**TeamMind 让你给项目配一支有哲学互补的 AI 团队：**

```
Project: FormulaFix

  Team Profile: High Assurance
  
  AI Team
  ─────────────────────────────
  🔹 Codex          LEAD        执行 / 构建 / 测试闭环
  🔸 Claude Code    SECURITY    安全 / 权限 / 显式审批
  🔸 Aider          REFACTORER  快速定向编辑

  用户输入：修复 LaTeX 渲染错位问题，不破坏 E2E

  [自动执行]
  Codex 分析 → 修改代码
       ↓
  Claude Code 审查 → 发现 2 处权限边界问题
       ↓
  Codex 修复 → Aider 补充测试
       ↓
  [最终结果 + 验证证据]
  Changes:  7 files, +183 / -79
  Review:   Claude: 2 issues found, 2 resolved
  Tests:    42 passed, 0 failed
  Evidence: 5 verified items

  [Project Profile 更新]
  Codex (LEAD):    implementation +0.02
  Claude (SECURITY): review_quality +0.01
```

---

## 设计哲学（三层）

### 第一层：Everything is a Plugin

```
TeamMind Core
    │
Cordis-like Plugin Runtime
    │
    ├─ Agent Plugins    (Claude / Codex / Aider / OpenCode / Gemini / 自定义)
    ├─ Verifier Plugins (Test Runner / Lint / Static Analysis)
    ├─ Memory Plugins   (Project Memory / Task Memory)
    └─ Integration Plugins (GitHub / GitLab / Jira)
```

核心系统根本不需要知道 Claude 是什么、Codex 是什么。它只懂 Plugin 接口。

### 第二层：Everything is a Capability

```
Lead Agent 想：
  ❌ "我要调用 Codex。"
  
Lead Agent 想：
  ✅ "我现在需要代码审查能力。"
  ✅ "Runtime，帮我找一个最合适的 Agent。"
```

Runtime 按**能力 + 哲学 + 历史表现**评分后自动选择。

### 第三层：Every Agent brings its own Philosophy

```
Claude Code = 安全 / 权限 / 显式审批的工程师
Codex       = 执行 / 构建 / 测试闭环的工程师
Aider       = 快速定向编辑的工程师
Gemini CLI  = 研究 / 多模态 / 大上下文的工程师
OpenCode    = 灵活 / 开源 / 隐私本地的工程师
```

不同 CLI 是不同**工程方法论**，TeamMind 让这些方法论形成**异质性冗余交叉验证**。

---

## 核心差异化

| 维度 | 通用 CLI 编排 | TeamMind |
|---|---|---|
| 思考模型 | "把几个 CLI 串起来" | "组建一支有哲学互补的 AI 团队" |
| 抽象层次 | 工具箱 | Runtime |
| Agent 之间传什么 | stdout 文本 | **Task Artifact + Evidence** |
| 调度依据 | CLI 名字 | **Capability + Philosophy + 历史表现** |
| 验证机制 | 信任 Agent 自报 | **独立 Evidence Verifier** |
| 自适应性 | 静态 | **自适应 Role Evolution** |
| 长期价值 | CLI 配置 | **项目级 AI 工程知识库** |
| 护城河 | 可复制 | **不可复制**（项目历史数据） |

**一句话**：
> **不是选择最强的 Agent，而是让不同 Agent 在最适合自己的位置协同工作。**

---

## 工作原理（30 秒看懂）

```
                TeamMind Runtime (localhost:8080)
                          │
                    ┌─────┴─────┐
                    │  Core    │
                    │Orchestrator
                    └─────┬─────┘
                          │
            ┌─────────────┼─────────────┐
            ↓             ↓             ↓
       Project State  Capability   Evidence
       (SQLite)       Routing      Verifier
            │             │             │
            └─────────────┼─────────────┘
                          │
                  Plugin Runtime
                  ┌───────┼────────┐
                  ↓       ↓        ↓
              Claude   Codex    Aider
              Plugin   Plugin   Plugin
                ↓         ↓         ↓
              CLI      CLI       CLI
```

---

## 快速开始（5 分钟）

### 前置要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- 至少一个 AI Agent CLI：
  - [Claude Code](https://github.com/anthropics/claude-code)
  - [Codex CLI](https://github.com/openai/codex)
  - [Aider](https://github.com/Aider-AI/aider)
  - [Gemini CLI](https://github.com/google-gemini/gemini-cli)
  - [OpenCode](https://github.com/opencode-ai/opencode)

### 一键启动（Windows）

```cmd
git clone https://github.com/yourname/teammind.git
cd teammind
start-all.bat
```

### CLI 探测 demo

```bash
cd backend
mvn -B compile
java -cp target/classes com.teammind.cli.registry.CLIDiscovery
```

输出示例：

```
=================================================
 TeamMind CLI Auto-Discovery
=================================================

  [OK] claude-code  Claude Code   v2.1.215
  [OK] codex        Codex CLI     v0.144.5

  Detected: 2 / 5 CLIs
```

---

## 文档

### 设计文档

- [docs/runtime/core-model.md](docs/runtime/core-model.md) - **核心数据模型（必读）**
- [docs/runtime/plugin-system.md](docs/runtime/plugin-system.md) - Cordis-like Plugin Runtime
- [docs/runtime/capability-routing.md](docs/runtime/capability-routing.md) - 能力路由 + **8 因素评分 + Team Policy**
- [docs/runtime/role-evolution.md](docs/runtime/role-evolution.md) - **四层** Adaptive Role Evolution（Global/Project/Task-Type/History）
- [docs/runtime/agent-philosophy-matrix.md](docs/runtime/agent-philosophy-matrix.md) - 5 个 CLI 详细拆解
- [docs/runtime/event-protocol.md](docs/runtime/event-protocol.md) - **统一事件协议（40+ 事件类型）**
- [docs/runtime/task-state-machine.md](docs/runtime/task-state-machine.md) - **Task 状态机 + Policy Engine**
- [docs/runtime/web-ui-architecture.md](docs/runtime/web-ui-architecture.md) - **Mission Control UI 架构**
- [docs/runtime/control-modes.md](docs/runtime/control-modes.md) - **三级控制模式**

### 协议与调研

- [docs/adapters/spec.md](docs/adapters/spec.md) - Agent Adapter 协议
- [docs/research/agent-cli-orchestration-landscape.md](docs/research/agent-cli-orchestration-landscape.md) - 市场调研
- [docs/research/orca-competitive-analysis.md](docs/research/orca-competitive-analysis.md) - **Orca 竞品深度分析**

### 开发指南

- [docs/development/README.md](docs/development/README.md) - **开发文档总入口**
- [docs/development/environment-setup.md](docs/development/environment-setup.md) - 环境搭建
- [docs/development/w2-plugin-runtime.md](docs/development/w2-plugin-runtime.md) - W2 Plugin Runtime 实现
- [docs/development/w2-capability-registry.md](docs/development/w2-capability-registry.md) - W2 能力注册表
- [docs/development/w2-schema-migration.md](docs/development/w2-schema-migration.md) - W2 数据库迁移
- [docs/development/w3-claude-plugin.md](docs/development/w3-claude-plugin.md) - W3 Claude Code Plugin
- [docs/development/w3-codex-plugin.md](docs/development/w3-codex-plugin.md) - W3 Codex Plugin
- [docs/development/w3-verifier-plugins.md](docs/development/w3-verifier-plugins.md) - W3 Verifier Plugins
- [docs/development/w4-role-evolution.md](docs/development/w4-role-evolution.md) - W4 自适应闭环
- [docs/development/testing-guide.md](docs/development/testing-guide.md) - 测试策略

### 演进历史

- [docs/RFC-001-cli-orchestration.md](docs/RFC-001-cli-orchestration.md) - v1 定位 RFC
- [docs/teammind-remediation-plan.md](docs/teammind-remediation-plan.md) - 修复计划档案

---

## 如何贡献 Agent Plugin

详见 [docs/runtime/plugin-system.md](docs/runtime/plugin-system.md)

1. Fork 仓库
2. 在 `backend/src/main/resources/adapters/<id>.yaml` 定义 Plugin
3. 实现 `Plugin` 接口（或继承 `AgentPlugin`）
4. 添加单元测试
5. 提 PR（附 Philosophy 与 Capability 声明）

### 优先级 Agent Plugin

| CLI | Stars | 哲学 | 状态 |
|---|---|---|---|
| [Claude Code](https://github.com/anthropics/claude-code) | 14.1 万 | 安全 / 显式审批 | 待贡献 |
| [Codex CLI](https://github.com/openai/codex) | 10.5 万 | 执行 / 测试闭环 | 待贡献 |
| [OpenCode](https://github.com/opencode-ai/opencode) | 19.5 万 | 多模型 / 隐私 | 待贡献 |
| [Gemini CLI](https://github.com/google-gemini/gemini-cli) | 10.5 万 | 研究 / 多模态 | 待贡献 |
| [Aider](https://github.com/Aider-AI/aider) | 4.8 万 | 快速定向编辑 | 待贡献 |

---

## 技术栈

| 层 | 技术 |
|---|---|
| Plugin Runtime | 自研 Cordis-like（生命周期 + 事件总线 + 调度） |
| Capability Registry | 自研（按能力 + 哲学索引） |
| Evidence Verifier | git CLI + 测试框架 + 文件系统 |
| 后端 | Spring Boot 3.3 + SQLite (WAL) |
| 实时 | WebSocket (STOMP) |
| 前端 | Vue 3 + TypeScript + Naive UI + Vue Flow |
| Agent 集成 | Java ProcessBuilder + 子进程 stdin/stdout |

---

## 与其他工具的边界

| 工具 | 边界 |
|---|---|
| [CC Switch](https://github.com/farion1231/cc-switch) | CC Switch 管"今天用哪个供应商"（10 万 Stars）。TeamMind 管"这个项目应该由谁做什么"。 |
| LangGraph / AutoGen | 那些是 Python 库，要写代码。TeamMind 是 Web 工具，配置即可。 |
| Claude Code / Codex 各自 subagent | 单 CLI 内部已有多 Agent 能力。TeamMind 是**跨 CLI 协作层**，不抢这个赛道。 |

---

## License

MIT

---

## 致谢

- [CC Switch](https://github.com/farion1231/cc-switch) - CLI 统一管理先驱
- [DeepSeek Harness / Cordis](https://github.com/deepseek-ai) - Plugin Runtime 架构哲学启发
- [Vue Flow](https://vueflow.dev/) - 可视化画布
- [Naive UI](https://www.naiveui.com/) - Vue 3 UI 组件库