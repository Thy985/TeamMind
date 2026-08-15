# RFC-001: TeamMind 重新定位为"Project AI Team Runtime"

> **状态**：草案征集意见中 → 已演进到 v0.2
> **截止日期**：2026-09-15
> **作者**：TeamMind Maintainers
> **演进历史**：v0.1（CLI 编排控制台） → v0.2（Project AI Team Runtime，按用户反馈修正）

---

## 摘要

将 TeamMind 从"AI Agent 协作平台"重新定位为"**Project AI Team Runtime**"。

**三层设计哲学**：
1. **Everything is a Plugin** —— 核心系统保持极小，能力外置
2. **Everything is a Capability** —— Runtime 按能力而非名字调度 Agent
3. **Every Agent brings its own Philosophy** —— 不同 Agent 形成异质性冗余

**核心定位**：
> 给每个代码项目配置一支由不同 AI Agent 组成的有哲学互补的工程团队，让它们围绕共享项目状态真正协同完成任务，并随项目运行逐渐进化。

---

## 动机

### 背景

TeamMind 当前代码库包含完整的"AI Agent 协作平台"骨架：Evolution 引擎、模板系统、Agent 市场、多用户 JWT。但代码从未被严肃使用：

- 6 个测试套件失败 + 1 个启动崩溃 bug = 8 个 P0 项目 bug
- 没有任何外部用户
- 定位模糊：商业化、教学、还是工具？

### 触发事件

**市场调研**发现：

1. **AI Agent CLI 已成主流生态**：
   - OpenCode 19.5 万 Stars / Claude Code 14.1 万 Stars / Codex CLI 10.5 万 Stars / Gemini CLI 10.5 万 Stars
   - DeepSeek 1.39 亿 MAU / Kimi 2269 万 MAU（国内市场更大）

2. **CLI 统一管理是真实痛点**：
   - CC Switch 10 万 Stars 已证明
   - Open Design 自动扫描 PATH 上所有 CLI

3. **没有人做"跨 CLI 项目级协作"**：
   - CC Switch 管配置，Open Design 偏设计，Orca 单一界面
   - **没有人在画布上编排"有哲学互补的 AI 团队"**

### 用户反馈驱动的设计进化

第一轮（v0.1）：定位为"CLI 编排控制台"

第二轮（v0.2，基于用户反馈）：
- ❌ "CLI 编排"太工具箱化
- ✅ 升级为"Project AI Team Runtime"
- ✅ 加入 Cordis-like Plugin Runtime
- ✅ 加入 Capability Routing（按能力而非名字）
- ✅ 加入 Agent Philosophy（异质性冗余）
- ✅ 加入 Adaptive Role Evolution（项目级飞轮）

---

## 新设计

### 一句话定位

> **TeamMind：给每个代码项目配置一支由不同 AI Agent 组成的有哲学互补的工程团队，让它们围绕共享项目状态真正协同完成任务，并随项目运行逐渐进化。**

### 三层设计哲学

#### 第一层：Everything is a Plugin

```
TeamMind Core  ←  极小，只懂 Plugin 接口
    │
Cordis-like Plugin Runtime  ←  加载/调度/事件/健康
    │
    ├─ Agent Plugins    (Claude / Codex / Aider / ...)
    ├─ Verifier Plugins (Test Runner / Lint / ...)
    ├─ Memory Plugins   (Project Memory / ...)
    └─ Integration Plugins (GitHub / GitLab / ...)
```

**关键原则**：Core 不写 `if agent === 'claude'`，只写 `pluginManager.invoke({ capability: ... })`。

#### 第二层：Everything is a Capability

```
Lead Agent 不需要想：
  ❌ "我要调用 Codex。"
  
Lead Agent 想：
  ✅ "我现在需要代码审查能力。"
  ✅ "Runtime，帮我找一个最合适的 Agent。"
```

Runtime 按 5 因素评分：
- 权重 1（40）：项目级历史表现
- 权重 2（20）：哲学匹配度
- 权重 3（15）：能力声明质量
- 权重 4（10）：用户显式偏好
- 权重 5（扣分）：成本与延迟

#### 第三层：Every Agent brings its own Philosophy

| CLI | 设计哲学 | 适合角色 | 不适合 |
|---|---|---|---|
| Claude Code | 安全 / 显式审批 / 沙箱 | security_review / architecture_review | bulk_refactor |
| Codex | 执行 / 测试闭环 / 仓库理解 | implementation / testing | pure_research |
| Aider | 快速定向编辑 / Git 原生 | refactoring / rapid_patch | architecture_design |
| Gemini CLI | 大上下文 / 多模态 / 研究 | research / documentation | complex_refactor |
| OpenCode | 多模型 / 隐私本地 | privacy_sensitive / flexible | （通用） |

**关键洞察**：让不同方法论形成**异质性冗余交叉验证**。
- `Codex → Claude`：执行哲学 × 安全哲学（不同偏见）
- `Aider → Claude`：快速编辑 × 显式审批
- `Codex → Codex`：同种偏见，意义小

---

## 数据模型

详见 [docs/runtime/core-model.md](docs/runtime/core-model.md)

核心对象：
- **Project** + **TeamConfig** + **SharedState**
- **AgentPlugin**（含 philosophy + capabilities）
- **CapabilityRegistry** + **TaskScheduler**
- **ProjectAgentProfile** + **RoutingLesson**

---

## 架构

```
                TeamMind UI (localhost:3000)
                Vue 3 + Vue Flow + Naive UI
                          │
                          ↓ WebSocket
                TeamMind Runtime (localhost:8080)
                Spring Boot 3.3 + SQLite
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
   Plugin Manager   Capability       Evidence
   + EventBus       Registry         Verifier
        │                 │                 │
        └─────────────────┼─────────────────┘
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

## 实施计划

### W1 ✅ 已完成
- [x] 预研报告
- [x] Adapter spec
- [x] README 重写
- [x] RFC-001
- [x] CLIDiscovery（实测发现 Claude + Codex）
- [x] **核心数据模型 v0.2**（按用户反馈重写）
- [x] **Plugin System 设计文档**
- [x] **Capability Routing + Philosophy Matrix**
- [x] **Role Evolution 闭环设计**

### W2：核心 Runtime 骨架

| # | 任务 | 工作量 |
|---|---|---|
| W2.1 | Plugin Runtime 框架（Cordis-like） | 2 天 |
| W2.2 | Capability Registry + 路由算法 | 1.5 天 |
| W2.3 | Agent Plugin 接口 + 5 个 CLI 默认 Plugin | 1.5 天 |
| W2.4 | Evidence Verifier | 1 天 |

### W3：首个完整 Pipeline

| # | 任务 | 工作量 |
|---|---|---|
| W3.1 | Claude Code Plugin（完整实现） | 2 天 |
| W3.2 | Codex Plugin（完整实现） | 1.5 天 |
| W3.3 | Git / Test Verifier Plugins | 1 天 |
| W3.4 | 端到端：用户 → Lead → Member → Verify → 完成 | 2 天 |

### W4：自适应闭环

| # | 任务 | 工作量 |
|---|---|---|
| W4.1 | Project Agent Profile 数据采集 | 1.5 天 |
| W4.2 | Role Drift Detection | 1 天 |
| W4.3 | Team Recommendation UI | 1 天 |

### W5：发布

| # | 任务 | 工作量 |
|---|---|---|
| W5.1 | 录视频 + 写 README | 1 天 |
| W5.2 | GitHub Release v0.1 | 0.5 天 |

**总计：W2-W5 = 16.5 天**

---

## 反馈方式

- GitHub Issue：提 `RFC-001` 标签
- 邮件：maintainers@teammind.local
- 微信群：（待建立）

---

## 附录 A：从 v0.1 到 v0.2 的关键演进

| v0.1（CLI 编排控制台） | v0.2（Project AI Team Runtime） |
|---|---|
| 5 个固定 CLI | **一切皆插件** |
| Lead = CLI 名 | **Lead = role + 能力匹配** |
| Agent 之间传 stdout | **Agent 之间传 Task Artifact + Evidence** |
| Agent 自报 success | **独立 Evidence Verifier** |
| 静态配置 | **自适应 Role Evolution** |
| 选 CLI | **组建有哲学互补的团队** |
| 价值 = 支持几个 CLI | **价值 = 项目级 AI 工程知识库** |

## 附录 B：关键参考链接

- OpenCode: https://github.com/opencode-ai/opencode
- Claude Code: https://github.com/anthropics/claude-code
- Codex CLI: https://github.com/openai/codex
- Gemini CLI: https://github.com/google-gemini/gemini-cli
- Aider: https://github.com/Aider-AI/aider
- CC Switch: https://github.com/farion1231/cc-switch
- Orca: https://github.com/stablyai/orca（v1.4.178，详细分析见 [orca-competitive-analysis.md](../research/orca-competitive-analysis.md)）
- DeepSeek Harness / Cordis: https://github.com/deepseek-ai

---

**最后更新**：2026-08-14
**状态**：v0.2 Draft，等待社区反馈