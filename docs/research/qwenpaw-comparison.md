# QwenPaw 对标分析：可复用、可借鉴、需差异化

> **分析对象：** QwenPaw v2.x（Agent OS / Personal Agent Workstation）
> **参考对比方：** TeamMind（Project AI Team Runtime + Engineering Assurance Layer）
> **分析日期：** 2026-08-18
> **核心结论前置：** QwenPaw 在底层基础设施层（Workspace、Governance、ACP、Memory、Plugin）重合极大，但这恰恰是好消息——TeamMind 不应重新造这些轮子，而应把它们作为 Provider/Adapter 直接复用，把注意力集中在真正差异化的三层：Execution Ledger、Independent Verification、Project-specific Role Evolution。

---

## 一、QwenPaw 2.x 核心能力速览

### 1.1 产品定位
QwenPaw 官方描述为 **"Agent OS"**，三大支柱：

```
Agent OS = Workspace + Governance + Sandbox
```

- 每个 Agent 有独立的 Resources（透明文件系统）、Governance（allow/deny/ask/sandbox）、Sandbox（macOS/Linux/Windows 内核级隔离）
- MCP / A2A / ACP connector layer（协议中立的外部工具集成）
- Loop Engineering：Coding Mode、Mission Mode，可组合 approval gates

### 1.2 核心能力矩阵

| 能力域 | QwenPaw 2.x 实现 | 状态 |
|--------|------------------|------|
| **Multi-Agent** | 独立 Agent 创建、各自 Memory/Skills、ACP 跨 Agent 通信 | ✅ 成熟 |
| **Memory** | ReMe v0.4 自我进化知识库、三层层记忆（上下文/历史/知识库）、Markdown 持久化 | ✅ 成熟 |
| **Workspace** | 统一文件导航/预览/编辑/Diff/上传/下载 | ✅ 成熟 |
| **Sandbox** | Seatbelt(macOS) / Bubblewrap+Landlock(Linux) / AppContainer(Windows) | ✅ 成熟 |
| **Governance** | 安全策略（allow/deny/ask/sandbox）、Skill Scanner、Tool Guard | ✅ 成熟 |
| **ACP** | Agent Communication Protocol，跨系统编排 | ✅ 成熟（v1.1.2 引入） |
| **MCP** | MCP 客户端管理、外部工具接入 | ✅ 成熟 |
| **Skill/Plugin** | 内置 Skills（定时、PDF、新闻）、Plugin Marketplace、自定义 Skill 自动加载 | ✅ 成熟 |
| **Loop** | Coding Mode（描述→执行→验收）、Mission Mode（多阶段任务） | ✅ 成熟 |
| **Approval Gates** | 可组合的 allow/deny/ask 审批门 | ✅ 成熟 |
| **Channels** | DingTalk、飞书、QQ、微信、Discord、Telegram、iMessage | ✅ 成熟 |
| **Context** | Scroll Context：每轮持久化、驱逐后可按需召回 | ✅ 成熟 |
| **TUI** | 全屏终端聊天、同 Agent/同 Memory/同 Session | ✅ 成熟 |

---

## 二、QwenPaw → TeamMind 对照分析

### 2.1 可直接拿来用（复用）

#### ✅ ACP 协议层

**QwenPaw 实现：**
- ACP 协议作为跨 Agent 通信标准
- `Agent Communication Protocol` 支持同步/异步消息
- 每个 Agent 可以独立运行，通过 ACP 握手后交换上下文
- 具体见：[ACP Integration Docs](https://qwenpaw.agentscope.io/docs/acp-integration)

**TeamMind 现状：**
- M2.5 已完成 `ACPEventMapper` + `AgentTransport` 抽象层
- `ACPTransport.java` + `LegacyTransport.java` 已实现
- Rust 侧 `acp_event_stream.rs` 已完成 JSONL → TeamMind Event 映射

**结论：** ACP 协议层面不需要重造。QwenPaw 的 ACP 实现是 Python 侧的，TeamMind 已经有 Java + Rust 的对接层。可以直接把 QwenPaw 的 ACP bridge 作为 Provider 之一，和 Codex/Claude Code 一样接入 `AgentTransport`。

**行动项：**
```
AgentTransport
├── LegacyTransport (现有 ProcessBuilder 路径)
├── ACPTransport (现有 Codex/AutoCode 路径)
└── QwenPawACPTransport ← 新增：通过 QwenPaw 的 ACP bridge 调用
```

---

#### ✅ Skill/Plugin 系统架构

**QwenPaw 实现：**
- Skill 定义在 `~/.qwenpaw/skills/` 目录下，自动加载
- 每个 Skill 是一个 Markdown 文件 + Python 脚本
- Skill Marketplace 在 AgentScope Platform 上
- 自定义 Skill 结构：
  ```yaml
  # skills/web_search/skill.yaml
  name: web_search
  description: Search the web for current information
  triggers: ["search", "lookup", "查询"]
  ```
  ```python
  # skills/web_search/skill.py
  async def run(ctx, args):
      results = await tavily_search(args["query"])
      return {"content": results}
  ```
- Plugin 架构更强大：可以封装整个 Agent 作为 Plugin

**TeamMind 现状：**
- `cli-adapters/*.yaml` 驱动 `GenericCLIPlugin`
- `PluginRegistry` 注册内置插件 + YAML 加载
- `AgentTransport` 抽象层统一接口
- 但缺少：Skill 级别的元数据描述、自动发现机制

**可借鉴点：**
1. **YAML Schema 设计**：QwenPaw 的 Skill 元数据格式值得参考，可以增强 `CLIConfig`
2. **Skill Scanner 安全扫描**：QwenPaw 在安装前扫描 prompt injection、command injection、硬编码密钥等，这个能力可以直接参考
3. **Plugin Marketplace 思路**：不需要做市场，但 `cli-adapters/` 的自动发现机制可以参考

**结论：** `GenericCLIPlugin` + `CLIConfig` 的设计思路和 QwenPaw 的 Skill 系统高度一致。可以直接复用 CLI 适配器架构，不需要重写。

---

#### ✅ Workspace（工作区）概念

**QwenPaw 实现：**
- 每个 Agent 有独立 workspace（文件系统路径）
- Workspace 内文件完全透明：导航、预览、编辑、Diff
- 工作区支持快照（checkpoint），M2.3 引入了 workspace checkpoints
- 支持多工作区切换

**TeamMind 现状：**
- `WorkspaceManager.java` 已在 roadmap M3 中
- 当前没有独立的 workspace 隔离（复用同一个项目目录）
- Git worktree 是未来的 M3 目标

**可借鉴点：**
1. Workspace 应该是 Task 的一等公民，而不是全局共享
2. 快照机制（checkpoint）对 Recovery 很重要
3. 文件系统透明管理（导航/预览/Diff）是 Mission Control UI 的加分项

**结论：** 这是 M3 的目标，可以在 roadmap 中标注 QwenPaw 的实现作为参考，不需要重新设计。

---

#### ✅ Sandbox / Governance 思路

**QwenPaw 实现：**
- 内核级沙箱（Seatbelt/Bubblewrap/AppContainer）
- Tool Guard：YAML 规则引擎，命令执行前检查
- File Guard：限制文件访问
- Skill Scanner：安装前安全扫描
- 审批级别：STRICT / SMART / AUTO / OFF

**TeamMind 现状：**
- `ProcessSupervisor` 接口已有（M2 完成）
- 但没有安全扫描层
- `PolicyEngine.java` 有策略框架，但未与安全集成

**可借鉴点：**
1. `ShellEvasionGuardian`（QwenPaw 的命令安全检查器）的思路可以参考，作为 `ProcessSupervisor` 的装饰器
2. Approval levels 可以和 TeamMind 的 `HumanControlService` 集成

**结论：** Sandbox 是 M9（Kill Switch）之前的中间层，可以作为 Runtime Contract 的一部分提前设计。

---

#### ✅ Memory 架构（ReMe）

**QwenPaw 实现：**
- 三层记忆：Live Context / Verbatim History / Personal Knowledge Base
- 基于 ReMe（self-evolving personal knowledge base）
- 记忆是 Markdown，可编辑、可搜索、可链接
- 自动 distillation：对话结束后提取关键信息存入知识库
- Dream 功能：计划内定期清理/重组记忆

**TeamMind 现状：**
- `EvidenceLifecycleService.java` 有证据生命周期管理
- Knowledge Promotion（Phase 4 Sprint 4）有 ADR/Lesson 持久化
- 但缺少：
  - 对话级别的完整历史存储
  - 自动 distillation
  - 知识图谱（链接记忆）

**可借鉴点：**
1. ReMe 的 Markdown-based memory 思路完全符合 TeamMind 的 `.agents/` Markdown 存储原则
2. Distillation 可以在 `EvidenceLifecycleService` 中实现
3. Dream（定期记忆清理）可以作为 Schedule 任务

**结论：** Memory 层不需要直接移植 ReMe（它是 Python 库），但 TeamMind 的 `.agents/` Markdown 存储结构已经类似，只需补充 distillation 逻辑。

---

### 2.2 可以借鉴思路改进（需要适配）

#### 🔧 Agent Communication Protocol

**QwenPaw 实现：**
- 同步/异步两种通信模式
- 每个 Agent 有独立的 channel/topic
- 支持 handoff（任务交接）
- ACP 桥接外部 Agent

**TeamMind 现状：**
- `HandoffVerificationTest` 已验证 6 个测试通过
- `AgentTransport` 接口支持 session 管理
- 但缺少：channel/topic 路由、异步消息队列

**改进建议：**
```
当前：AgentTransport.submitPrompt() — 同步阻塞
改进：增加异步通道
  - submitPromptAsync(prompt, context) → CompletableFuture<String>
  - 支持 handoff：AgentA 完成任务后自动传递给 AgentB
```

---

#### 🔧 Loop Engineering（Coding Mode / Mission Mode）

**QwenPaw 实现：**
- Coding Mode：描述目标 → 自动执行 → 醒来看结果
- Mission Mode：多阶段任务自动编排
- 可组合的 approval gates

**TeamMind 现状：**
- `PipelineOrchestrator.java` 实现了 YAML pipeline 编排
- `PipelineStepDefinition` + `PipelineContext` + `PipelineExecutionResult`
- `ReviewLoopE2ETest` 已验证 Codex + Claude 协作

**改进建议：**
1. QwenPaw 的 Loop 是 UI 驱动的（Console 输入自然语言目标）
2. TeamMind 的 Pipeline 是 YAML 驱动的（声明式）
3. **可以借鉴**：Pipeline 步骤之间增加 approval gates
   ```yaml
   steps:
     - name: implement
       agent: codex
       gate: ask    # 完成后需要人工确认
     - name: review
       agent: claude-code
       gate: allow  # 自动执行
     - name: verify
       agent: none
       gate: none   # 直接跑测试
   ```

---

#### 🔧 Context Management（Scroll Context）

**QwenPaw 实现：**
- 每轮对话持久化
- 驱逐的 turn 索引化，按需召回
- 不是 summary，是原始文本 + 索引

**TeamMind 现状：**
- `EventStoreService.java` 存储事件
- `ACPEventMapper` 解析事件流
- 但没有"context window"管理

**改进建议：**
1. TeamMind 的 `RuntimeEvent` 本质上是"scroll context"
2. 可以借鉴"按需召回"机制：不一次发送所有历史，而是根据任务阶段动态加载
3. `ReadinessManager` 可以扩展为 context selector

---

#### 🔧 Security Scanning

**QwenPaw 实现：**
- Skill Scanner：检测 prompt injection、command injection、hardcoded keys、data exfiltration
- 三种模式：block / warn / off
- 白名单机制

**TeamMind 现状：**
- 没有任何 skill/plugin 安全扫描
- `GenericCLIPlugin` 直接执行任意 YAML 中配置的命令

**改进建议：**
```java
// 可以添加的装饰器模式
public class SafeGenericCLIPlugin extends GenericCLIPlugin {
    private final SecurityScanner scanner;
    
    public SafeGenericCLIPlugin(CLIConfig config, EventBus eventBus, 
                                 ProcessSupervisor supervisor,
                                 SecurityScanner scanner) {
        super(config, eventBus, supervisor);
        this.scanner = scanner;
    }
    
    @Override
    public void startProcess(String prompt, String workDir) {
        scanner.scan(config.getCommand()); // 命令注入检测
        scanner.scan(prompt);              // prompt injection 检测
        super.startProcess(prompt, workDir);
    }
}
```

---

### 2.3 TeamMind 独有、QwenPaw 没有的能力

#### 🔴 Execution Ledger（工程事实账本）

这是 TeamMind 最核心的差异化。

QwenPaw 的 memory 解决"过去聊过什么"，Ledger 解决"这个任务实际发生了什么"：

```
Task #183
  Agent:      Codex
  Commands:   18
  Files:      7 modified
  Dependencies: + jsonwebtoken@9.0.2
  Tools:      + jq
  Incidents:  1 compile failure
  Recovery:   Codex retry (automatic)
  Review:     Claude found 2 issues
  Verification: 42/42 passed
  Duration:   4.5s
```

这不是 memory，这是**工程审计轨迹**。

**QwenPaw 完全没有这个概念。** QwenPaw 记录对话历史，但不记录：
- 执行了哪些命令
- 修改了哪些文件
- 测试覆盖率变化
- 谁（哪个 Agent）做了什么决策

#### 🔴 Independent Verification（独立验收）

QwenPaw 有 approval gates，但是：
- 审批者是人（或规则引擎）
- **没有独立的 Reviewer Agent**

TeamMind 的核心原则：
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

**执行者不能成为自己的最终验收者。**

#### 🔴 Project-specific Role Evolution（项目级角色进化）

QwenPaw 有"Agent 学会更多"（Memory），但：
- 学习的是个人偏好和历史
- 没有"项目团队配置根据工程结果改变"的概念

TeamMind 的方向：
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

这不需要更聪明的 Agent，只需要**记录每个 Agent 在每个项目中的表现**，然后自动调整 Role Assignment。

---

## 三、具体行动建议

### 3.1 短期（M2-M3 阶段，1-2 周）

#### 3.1.1 直接复用
1. **ACP 协议**：把 QwenPaw 作为 ACP Provider 之一接入 `AgentTransport`，和 Codex/Claude Code 平级
2. **Skill Scanner 思路**：在 `GenericCLIPlugin` 前加一个 `SecurityScanner` 装饰器，参考 QwenPaw 的 block/warn/off 三级
3. **Markdown Memory 存储**：确认 `.agents/` 结构符合 ReMe 的思路（Markdown + 链接），不需要引入向量数据库

#### 3.1.2 改进 TeamMind
1. **Pipeline Approval Gates**：在 `PipelineStepDefinition` 中增加 `gate: ask | allow | auto` 字段
   ```yaml
   steps:
     - name: implement
       agent: codex
       gate: ask          # 完成后等待人工确认
     - name: verify
       agent: test-runner
       gate: auto         # 自动执行
   ```
2. **Context Selector**：借鉴 QwenPaw 的 "按需召回"，在 `ReadinessManager` 中增加 context window 管理

### 3.2 中期（M4-M7 阶段，4-8 周）

1. **Workspace Checkpoints**：参考 QwenPaw 的 workspace snapshot，在 M3 实现时加入 checkpoint 能力
2. **Distillation**：在 `EvidenceLifecycleService` 中实现自动提取关键信息到 `.agents/knowledge/`
3. **Role Performance Profile**：开始记录每个 Agent 在每个项目中的成功率、耗时、review 采纳率

### 3.3 长期（M8-M10 阶段，8-12 周）

1. **Project Team Evolution**：基于 Role Performance Profile，自动推荐每个项目的最佳 Agent 配置
2. **QwenPaw as Provider**：如果 QwenPaw 暴露 ACP，TeamMind 可以直接把它作为一个 Agent Provider，而不是竞争关系

---

## 四、架构对比图

```
┌──────────────────────────────────────────────────────────────────┐
│                        QwenPaw 2.x                               │
│                                                                   │
│  ┌─────────────┐   ┌─────────────┐   ┌──────────────────────┐   │
│  │  Workspace  │   │  Governance │   │     Sandbox          │   │
│  │  (文件系统)  │   │ (策略引擎)   │   │  (内核级隔离)        │   │
│  └──────┬──────┘   └──────┬──────┘   └──────────┬───────────┘   │
│         │                 │                     │               │
│  ┌──────▼──────┐   ┌──────▼──────┐   ┌──────────▼───────────┐  │
│  │  Memory     │   │  Skills/    │   │  Multi-Agent         │  │
│  │  (ReMe)     │   │  Plugins    │   │  (ACP 通信)           │  │
│  └──────┬──────┘   └──────┬──────┘   └──────────┬───────────┘  │
│         │                 │                     │               │
│  ┌──────▼─────────────────▼─────────────────────▼───────────┐  │
│  │                   Console / TUI / Channels                 │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  定位：个人 AI 工作站 / Agent OS                                  │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                        TeamMind                                  │
│                                                                   │
│  ┌─────────────┐   ┌─────────────┐   ┌──────────────────────┐   │
│  │  Workspace  │   │  Policy     │   │     Process          │   │
│  │  (M3 Rust)  │   │  Engine     │   │     Supervisor       │   │
│  └──────┬──────┘   └──────┬──────┘   └──────────┬───────────┘   │
│         │                 │                     │               │
│  ┌──────▼──────┐   ┌──────▼──────┐   ┌──────────▼───────────┐  │
│  │  Evidence   │   │  Agents/    │   │  Execution Ledger    │  │
│  │  Store      │   │  Transports │   │  (核心差异化)         │  │
│  └──────┬──────┘   └──────┬──────┘   └──────────┬───────────┘  │
│         │                 │                     │               │
│  ┌──────▼─────────────────▼─────────────────────▼───────────┐  │
│  │              Role Assignment + Performance Profile         │  │
│  │              (项目级 Agent 配置进化)                         │  │
│  └────────────────────────┬──────────────────────────────────┘  │
│                            │                                     │
│  ┌────────────────────────▼──────────────────────────────────┐  │
│  │              Pipeline Orchestrator                         │  │
│  │              (YAML 声明式编排 + Approval Gates)             │  │
│  └────────────────────────┬──────────────────────────────────┘  │
│                            │                                     │
│  ┌────────────────────────▼──────────────────────────────────┐  │
│  │              Mission Control (Web UI)                      │  │
│  │              + Human Control Interface                     │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  定位：Project AI Team Runtime + Engineering Assurance Layer      │
└──────────────────────────────────────────────────────────────────┘
```

---

## 五、总结：QwenPaw 对 TeamMind 的价值

### QwenPaw 能告诉 TeamMind 不要做什么

| QwenPaw 在做 | TeamMind 不应该做 |
|-------------|-------------------|
| Personal Agent OS | 不做通用助手 |
| Skill Marketplace | 不做市场 |
| 10+ 通讯渠道 | 专注项目场景 |
| 个人知识库 | 聚焦工程事实 |
| 对话式交互 | 聚焦 Pipeline 编排 |

### QwenPaw 能告诉 TeamMind 应该怎么做

| QwenPaw 在做 | TeamMind 可以借鉴 |
|-------------|-------------------|
| ACP 协议 | 直接复用，作为 Provider |
| Workspace 隔离 | M3 实现时参考 |
| Sandbox/Governance | 安全扫描装饰器 |
| Markdown Memory | 与 `.agents/` 原则一致 |
| Loop Engineering | Pipeline + Approval Gates |
| Context 管理 | ReadinessManager 扩展 |

### QwenPaw 不能替代 TeamMind 的部分

| TeamMind 独有 | 为什么 QwenPaw 没有 |
|--------------|-------------------|
| Execution Ledger | 这是工程审计，不是对话历史 |
| Independent Verification | QwenPaw 的审批是人，不是独立 Agent |
| Role Performance Profile | 项目级 Agent 配置进化，QwenPaw 没有这个抽象 |
| Pipeline Orchestrator | QwenPaw 是单 Agent 驱动，TeamMind 是多 Agent 协作 |

---

## 六、下一步

1. **M3 实现 Workspace 时**，参考 QwenPaw 的 workspace checkpoint 设计
2. **M4 实现 Event/Streaming 时**，参考 QwenPaw 的 Scroll Context 按需召回
3. **Pipeline Step Definition 中增加 approval gate**，这是 M3 之前就可以做的改进
4. **QwenPaw ACP Provider**，等 QwenPaw 暴露正式 ACP bridge 后接入

> **核心理念不变：不要和 QwenPaw 竞争 Agent OS，而是把它当成一个 Agent Provider，专注于工程验收这一层。**
