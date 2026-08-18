# QwenPaw 对标分析：TeamMind 的竞品事实与产品边界

> **分析对象：** QwenPaw v2.x（Agent OS / Personal Agent Workstation）
> **对比方：** TeamMind（Project AI Team Runtime + Engineering Assurance Layer）
> **分析日期：** 2026-08-18
> **核心结论前置：** QwenPaw 已覆盖 Agent OS 层几乎全部基础设施（Workspace、Sandbox、Governance、ACP、Memory、Multi-Agent、Loop Engineering、Approval Gates）。TeamMind 不应在基础设施层竞争，而应将 QwenPaw 视为 Agent Provider 之一。TeamMind 的核心差异化是**四层工程治理**：Execution Ledger、Independent Verification、Project Team Profile、Project Team Evolution。

---

## 战略定位

```
                    AI Engineering Stack

┌─────────────────────────────────────────────┐
│               Agent OS Layer               │
│ Workspace / Sandbox / Governance / ACP      │
│ Skills / Memory / Multi-Agent / Channels   │
│                                             │
│                  QwenPaw                    │
└──────────────────────┬──────────────────────┘
                       │
                    Provider
                       │
┌──────────────────────▼──────────────────────┐
│          Project Engineering Layer          │
│                                             │
│ Project Runtime                             │
│ Execution Ledger                            │
│ Evidence                                    │
│ Independent Verification                    │
│ Acceptance                                  │
│ Role Assignment                             │
│ Performance Profile                         │
│ Role Evolution                              │
│                                             │
│                 TeamMind                    │
└─────────────────────────────────────────────┘
```

**一句话定义：**
> **QwenPaw 管理 Agent；TeamMind 管理 AI 团队交付的可信过程。**

---

## 一、QwenPaw 2.x 核心能力（事实盘点）

### 1.1 产品定位
QwenPaw 官方描述为 **"Agent OS"**，核心支柱：

```
Agent OS = Workspace + Governance + Sandbox + ACP Connector Layer
```

| 能力域 | QwenPaw 2.x 实现 | 来源 |
|--------|------------------|------|
| **Multi-Agent** | 独立 Agent 创建、各自 Memory/Skills、ACP 跨 Agent 通信、`qwenpaw agents chat` | [GitHub][1] |
| **Memory** | ReMe v0.4 自我进化知识库、三层记忆（上下文/历史/知识库）、Markdown 持久化、Dream 定期整合 | [GitHub][2] |
| **Workspace** | 独立文件导航/预览/编辑/Diff、workspace checkpoint | [QwenPaw][3] |
| **Sandbox** | Seatbelt(macOS) / Bubblewrap+Landlock(Linux) / AppContainer(Windows) | [GitHub][1] |
| **Governance** | 安全策略（allow/deny/ask/sandbox）、Skill Scanner、Tool Guard、审批级别 STRICT/SMART/AUTO/OFF | [GitHub][1] |
| **ACP** | Agent Communication Protocol 跨系统编排、`qwenpaw acp` CLI | [QwenPaw][3] |
| **MCP** | MCP 客户端管理、外部工具接入 | [GitHub][1] |
| **Skill/Plugin** | 内置 Skills（定时、PDF、新闻）、Plugin Marketplace、自定义 Skill 自动加载 | [GitHub][1] |
| **Loop** | Coding Mode（描述→执行→验收）、Mission Mode（多阶段任务）、Loop Engineering | [GitHub][1] |
| **Approval Gates** | 可组合的 allow/deny/ask 审批门 | [GitHub][1] |
| **Channels** | DingTalk、飞书、QQ、微信、Discord、Telegram、iMessage | [QwenPaw][3] |
| **Context** | Scroll Context：每轮持久化、驱逐后可按需召回 | [GitHub][1] |
| **Desktop** | Windows/macOS 桌面版分发（免 Python 环境配置） | [QwenPaw][3] |
| **TUI** | 全屏终端聊天、同 Agent/同 Memory/同 Session | [GitHub][1] |

### 1.2 关键发现

QwenPaw 2.x 已经覆盖了原先设想的"Agent OS 层"几乎所有核心能力。这意味着：

> **Workspace、ACP、Memory、Sandbox、Plugin、Multi-Agent、Mission Loop、Approval Gates 这些不再能成为 TeamMind 的差异化论据。**

它们都是 QwenPaw 已经做好的基础设施。TeamMind 的正确姿势是：把它们当作**兼容性层**接入，而不是自己造。

---

## 二、TeamMind 四个核心差异化

这四个是 QwenPaw 不做的，也是 TeamMind 必须死守的。

### 2.1 Execution Ledger（工程事实账本）

**不是 memory，不是日志，是 Task-level 的工程审计轨迹。**

QwenPaw 的持久化重心是 Agent Session / Memory / Workspace——解决"过去聊过什么、记住什么"。
TeamMind 的 Ledger 解决"**这个任务实际发生了什么**"：

```
Task #183
  Agent:        Codex
  Commands:     18
  Files:        7 modified
  Dependencies: + jsonwebtoken@9.0.2
  Tools:        + jq
  Incidents:    1 compile failure
  Recovery:     Codex retry (automatic)
  Review:       Claude found 2 issues
  Verification: 42/42 passed
  Duration:     4.5s
```

这是一个**工程事实账本**，组织的是分散的 runtime facts（命令、文件变化、环境变化、依赖、Incident、Recovery、Review、Verification、Decision）为可审查的任务级记录。

> **QwenPaw 记录对话；TeamMind 记录任务。**

### 2.2 Independent Verification（独立验收）

**执行者与验收者必须分离——这是一种责任结构，不只是 approval 功能。**

QwenPaw 有 approval gates 和 sandbox，但审批者最终是人或规则引擎，没有独立的 Reviewer Agent。

TeamMind 的核心不变量：
```
Executor（Codex）
    ↓
Evidence（Git diff + 测试结果 + Ledger entry）
    ↓
Independent Reviewer（Claude Code）← 不同的 Agent，独立的判断
    ↓
Verifier（自动测试 + 人工确认）
    ↓
Human Gate
```

**执行者不能成为自己的最终验收者。** 这不仅是功能差异，是哲学差异。

### 2.3 Project Team Profile（项目团队配置）

**不是"Agent Profile"，是"Project × Agent → Role"的映射。**

QwenPaw 的抽象是一等公民是 Agent（Persona + Skills + Workspace + Memory）。
TeamMind 的一等公民是 **Role Assignment**：

```text
Claude
    ↓
不是 "Claude Agent = Reviewer"
而是 "Project A → Claude → Security Reviewer"
      "Project B → Claude → Architecture Lead"
      "Project C → Claude → Consultant"
```

同一个 Agent 在不同项目中担任不同角色——这是 TeamMind 的 Role 抽象，QwenPaw 没有这个层次。

### 2.4 Project Team Evolution（项目级角色演化）

**不是 Agent 学会更多，是"这个项目的 AI 团队配置根据真实工程结果改变"。**

QwenPaw 有 ReMe memory-evolving——Agent 越用越了解用户。
TeamMind 的方向不同：

```
初始配置：
  Lead:        Codex
  Reviewer:    Claude Code
  Verifier:    TestRunner

三个月后（根据实际表现）：
  Lead:        Codex (94% verified success)
  Security:    Claude Code (96% valid findings)
  Refactor:    Aider (97% accepted patches)
```

学习对象不是 Agent，而是 **Project**：

```text
QwenPaw learns:  "这个 Agent 更了解用户/过去上下文。"
TeamMind learns: "这个项目里，Codex 更适合 implementation，Claude 更适合 security review。"
```

这叫 **Role Evolution，不是 Memory Evolution**。

---

## 三、QwenPaw 对 TeamMind 的价值

### 3.1 可以直接接入的 Provider

```text
TeamMind
   ↓
AgentTransport
   ↓
├── LegacyTransport (CLI process, 现有)
├── ACPTransport (Codex/AutoCode, 现有)
└── QwenPawACPTransport ← 新增：通过 QwenPaw ACP bridge 调用
```

QwenPaw 不是 TeamMind 的竞品，而是 **Agent Runtime Provider**：

```text
TeamMind Project Runtime
        ↓
     ACP Provider
        ↓
      QwenPaw
        ↓
   QwenPaw Agents
```

QwenPaw 管理自己的 Agent/Skill/Workspace，TeamMind 编排 QwenPaw 作为执行层。

### 3.2 可以借鉴的设计思路

| QwenPaw 在做 | TeamMind 可以借鉴 |
|-------------|-------------------|
| Skill Scanner（prompt injection / command injection / hardcoded keys 检测） | `SecurityScanner` 装饰器，集成到 `GenericCLIPlugin` |
| Loop Engineering（Coding Mode / Mission Mode） | Pipeline Step 间增加 approval gate（`gate: ask | allow | auto`） |
| Scroll Context（按需召回驱逐 turn） | `ReadinessManager` 扩展 context selector，不按批次全量发送 |
| Markdown-based Memory（ReMe） | `.agents/` Markdown 存储结构延续 distillation 逻辑 |
| Workspace checkpoint | M3 Workspace 实现时加入 snapshot 能力 |

### 3.3 不应再包装成差异化

以下全部属于 **Infrastructure Compatibility Layer**，不是 TeamMind 的核心创新：

```text
ACP          → 协议层，复用生态
Plugin       → 适配器模式，cli-adapters/*.yaml
Workspace    → Git Worktree + QwenPaw Workspace
Sandbox      → ProcessSupervisor + 未来 QwenPaw Sandbox
Memory       → Markdown + Project Knowledge
Multi-Agent  → AgentTransport 多 Provider
Approval     → Pipeline gate + HumanControlService
Context      → ReadinessManager
Mission Loop → PipelineOrchestrator
```

---

## 四、架构定位图

```
┌──────────────────────────────────────────────────────────┐
│                    TeamMind                              │
│                                                          │
│                 Project AI Team                          │
│                                                          │
│   ┌─────────┐  ┌──────────┐  ┌────────────────────┐     │
│   │ Role    │  │ Routing  │  │ Policy             │     │
│   └────┬────┘  └────┬─────┘  └────────┬───────────┘     │
│        └─────────────┼─────────────────┘                │
│                      ↓                                  │
│              Execution Runtime                         │
│                      ↓                                  │
│              Execution Ledger                          │
│                      ↓                                  │
│                Evidence Layer                          │
│                      ↓                                  │
│        Reviewer → Verifier → Human                     │
│                      ↓                                  │
│              Project Memory                           │
│                      ↓                                  │
│             Team Evolution                            │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  Compatibility / Provider Layer                         │
│  ACP / Legacy CLI / QwenPaw / Workspace / MCP          │
└──────────────────────────────────────────────────────────┘
```

---

## 五、Roadmap 优先级调整

基于以上分析，原 roadmap 中 M3-M7 的表述应更新为：

| Phase | 目标 | 说明 |
|-------|------|------|
| **M3** | Project Execution Model | 任务驱动的 runtime，不是 general-purpose workspace |
| **M4** | Execution Ledger | Task-level accountability model |
| **M5** | Independent Verification | Reviewer ≠ Verifier ≠ Executor |
| **M6** | Role / Performance Profile | 每个 Agent 在每个项目中的表现档案 |
| **M7** | Project Team Evolution | 基于真实结果的自动推荐 + 人工确认 |

而以下全部降级为 **Infrastructure Compatibility**，按需接入：

```text
ACP          → 已有 AgentTransport 抽象
Workspace    → M3 时参考 QwenPaw checkpoint
Sandbox      → M9 Kill Switch 前逐步实现
Memory       → .agents/ Markdown 已具备
Plugin       → cli-adapters/*.yaml 已工作
Multi-Agent  → AgentTransport 多 Provider 已就绪
Approval     → Pipeline gate 可参考 QwenPaw
Context      → ReadinessManager 可扩展
Mission Loop → PipelineOrchestrator 已有框架
```

---

## 六、与 QwenPaw 的本质区别

```
              QwenPaw                    TeamMind
          ──────────────              ─────────────────
  一等公民          Agent                Project
  记忆对象      个人偏好/历史           工程事实/任务结果
  优化目标     Agent 越用越聪明         项目团队配置越用越好
  验证机制    人审 / 规则引擎            独立 Reviewer Agent + 自动测试
  执行者身份   Executor = 最终验收者     Executor ≠ Reviewer ≠ Verifier
  产品边界    Agent OS                  Project Runtime
  类比        Linux                     Kubernetes
```

---

## 七、参考资料

[1]: https://github.com/agentscope-ai/QwenPaw "QwenPaw GitHub — Personal AI Assistant, Agent OS"
[2]: https://github.com/agentscope-ai/QwenPaw/blob/main/README.md "QwenPaw README — Core capabilities"
[3]: https://qwenpaw.agentscope.io/docs/cli/ "QwenPaw CLI Documentation — agents, acp, skills, mission"
