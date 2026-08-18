# TeamMind ACP Transport Integration Plan v2

> **目标：将 TeamMind 的 Agent 接入层从"CLI-specific process parsing"升级为
> "Transport-agnostic Agent Client"，ACP 作为第一类标准 Transport，Legacy CLI
> 作为兼容 Transport。两者并行共存，ACP preferred，Legacy fallback。**

---

## 一、问题诊断（v1 的问题）

### 1.1 当前 M2.5 的问题

```
M2.5 实现：
  Java → ProcessBuilder("node <cx-path> exec --json") → stdout JSONL → ACPEventMapper → TeamMindEvent

问题：
  1. "ACP"实际只是另一种 stdout parser，不是真正的 ACP Client
  2. ACP 是协议层，不是数据格式；真正的 ACP 有 session init、prompt、
     notifications、permission request-response、tool call round-trip 等交互
  3. Task 配置用 cli_id="codex-acp"，污染了 CapabilityRegistry 和 Performance Profile
  4. completion → EVIDENCE_VERIFIED 错误耦合：Agent 完成 ≠ 证据已验证
```

### 1.2 真正的 ACP 生态

| Agent | 官方 ACP Server | 协议 | 通信方式 |
|-------|----------------|------|---------|
| Codex | `codex-acp` (TypeScript) | ACP stdio JSON-RPC | ACP ↔ Codex App Server 桥接 |
| Claude | `claude-agent-acp` (TypeScript) | ACP stdio JSON-RPC | ACP ↔ Claude Agent SDK 桥接 |
| 未来 | `agentclientprotocol/*` | ACP stdio JSON-RPC | 统一的 Agent ↔ Client 协议 |

ACP 是**协议层**，不是输出格式。它定义了 client 和 agent 之间的：
- Session lifecycle（init → prompt → notifications → close）
- Tool call round-trip（request → execute → result）
- Permission request（structured request → approval/deny response）
- File change events（structured, not guessed）
- Subagent transcripts（nested agent sessions）

**我们的目标：通过 ACP 协议与 Agent 通信，而不是解析 Agent 的 stdout。**

---

## 二、架构设计

### 2.1 Transport 分层

```
v1（错误）:                    v2（正确）:
  Plugin ──→ YAML ──→ ProcessBuilder ──→ stdout ──→ Parser   Plugin ──→ YAML ──→ TransportFactory ──┬──→ LegacyTransport
                                                                                               │      (ProcessBuilder + Parser)
                                                                                               │
                                                                                               └──→ ACPTransport
                                                                                                     (ACP Bridge → ACP events)
```

### 2.2 核心接口

```java
// 1. AgentTransport — 抽象 Agent 调用方式
public interface AgentTransport {
    TransportType type();
    AgentSession start(AgentConfig config);
    void close();
    TransportCapabilities capabilities();
}

// 2. AgentSession — 单次执行会话
public interface AgentSession {
    String submitPrompt(String prompt, Map<String, Object> context);
    void cancel();
    void close();
    boolean isAlive();
    SessionMetadata metadata();
}

// 3. Transport 类型
enum TransportType { LEGACY, ACP }
```

### 2.3 两个 Transport 实现

```
LegacyTransport：
  - 封装现有 GenericCLIPlugin 行为
  - ProcessBuilder + stdout parser
  - 不改变任何现有行为

ACPTransport：
  - 管理 Node.js/Rust ACP bridge 进程
  - 通过 stdio JSON-RPC 与 ACP agent server 通信
  - 使用官方 codex-acp / claude-agent-acp
  - Permission 请求 → PolicyEngine 路由
  - 事件通过 ACPEventMapper 映射为 TeamMind 事件
```

### 2.4 AgentPlugin 不变（消除乘法膨胀）

```
之前（v1）：
  CodexPlugin       → cli_id="codex"       (legacy)
  CodexACPPlugin    → cli_id="codex-acp"   (ACP)
  ClaudeCodePlugin  → cli_id="claude-code" (legacy)
  ClaudeACPPlugin   → cli_id="claude-acp"  (ACP)
  = 4 plugins, 能力重复声明

之后（v2）：
  CodexPlugin       → transport=legacy (默认) 或 transport=acp
  ClaudeCodePlugin  → transport=legacy (默认) 或 transport=acp
  = 2 plugins, 通过 transport 字段选择接入方式
```

### 2.5 YAML 配置

```yaml
# Legacy（默认，不变）
agent: codex
# transport 省略或 "legacy"
command: "codex"
args: ["exec", "-c", "approval_policy=never", "<prompt>"]
output_format: "text"

# ACP 路径
agent: codex
transport: acp
# command/args 由 ACPTransport 内部配置，用户不需要关心
# 或通过 acp_bridge 指定 bridge 路径
acp_bridge: "codex-acp"    # 或直接使用官方 bridge
acp_config:
  sandbox: "danger-full-access"
  approve_for_me: true
```

### 2.6 ACP Bridge 模型

```
TeamMind (Java)
  │
  │  AgentTransport.start()
  │
  ▼
ACPBridge (managed process)
  ├── codex-acp (Node.js stdio ACP server)
  │     └── Codex App Server (实际 Agent)
  │
  └── claude-agent-acp (Node.js stdio ACP server)
        └── Claude Agent SDK (实际 Agent)
              │
              │ stdio JSON-RPC (ACP protocol)
              ▼
        ACP Event Stream
              │
              ▼
        ACPEventMapper
              │
              ▼
        TeamMind Events → EventBus
              │
              ├─ tool_call → TOOL_CALLED
              ├─ file_change → FILE_CHANGED
              ├─ permission_request → PolicyEngine → ApprovalRequest
              ├─ completion → TASK_COMPLETED (NOT EVIDENCE_VERIFIED)
              └─ error → ERROR_RECOVERABLE / ERROR_CRITICAL
```

### 2.7 Permission 路由（关键设计）

```
ACP permission_request event
        │
        ▼
ACPEventMapper
        │
        ▼
DECISION_REQUIRES_APPROVAL event
        │
        ▼
PolicyEngine (ControlMode)
        │
        ├── AUTOMATED  → auto approve based on risk rules
        ├── SUPERVISED → create ApprovalRequest (default)
        │                → Mission Control UI
        │                → Human approves/denies
        │                → ACPTransport responds to ACP server
        └── MANUAL     → every action needs confirmation
```

### 2.8 Evidence 分离（关键修复）

```
之前（v1，错误）：
  completion exit_code=0
      → TASK_COMPLETED + EVIDENCE_VERIFIED  ← 错误耦合

之后（v2，正确）：
  completion exit_code=0
      → TASK_COMPLETED
      → (Agent claims success)
      │
      ▼
  GitVerifier + TestRunner + Filesystem checks
      → VERIFICATION_PASSED / VERIFICATION_FAILED
      → Evidence.COLLECTED → Evidence.VERIFIED
```

**原则：Agent 完成 ≠ Evidence 已验证。**
TeamMind 的核心价值就是在 Agent 声称完成后，独立验证结果。

---

## 三、实现步骤

### P0: 核心接口（本阶段）

| Step | 文件 | 内容 |
|------|------|------|
| 1 | `AgentTransport.java` (new) | Transport 接口定义 |
| 2 | `AgentSession.java` (new) | Session 接口定义 |
| 3 | `TransportCapabilities.java` (new) | 能力声明 |
| 4 | `LegacyTransport.java` (new) | 提取现有 GenericCLIPlugin 行为 |
| 5 | `ACPEventMapper.java` (fix) | 修复 completion→EVIDENCE_VERIFIED 错误 |
| 6 | `ACPTransport.java` (new) | ACP Transport stub（待 bridge 实现） |
| 7 | `ACPBridge.java` (new) | ACP bridge 进程管理 |
| 8 | `AgentTransportFactory.java` (new) | 根据 config 选择 transport |

### P1: Codex ACP E2E

| Step | 文件 | 内容 |
|------|------|------|
| 9 | `codex-acp.yaml` (update) | 使用 `agent: codex, transport: acp` 语法 |
| 10 | `ACPTransportTest.java` | 测试 ACP transport 启动/关闭/事件流 |
| 11 | `CodexACPIntegrationTest.java` | E2E: 真实启动 codex-acp bridge |

### P2: Claude ACP + 路由

| Step | 文件 | 内容 |
|------|------|------|
| 12 | `claude-agent-acp.yaml` (new) | Claude ACP 配置 |
| 13 | `CapabilityRouter.java` (new) | 根据 TransportCapabilities 路由 Task |
| 14 | `LegacyACPEquivalenceTest.java` | 对比验证：legacy vs ACP 语义等价 |
| 15 | `PermissionPolicyIntegrationTest.java` | Permission → PolicyEngine 集成测试 |

### P3: Rust Runtime 复用

| Step | 文件 | 内容 |
|------|------|------|
| 16 | `docs/contracts/runtime-contract.md` (update) | 新增 AgentTransport contract |
| 17 | Rust ACP client | 在 Rust Runtime 中实现同一 AgentTransport |

---

## 四、文件变更清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `plugin/transport/AgentTransport.java` | Transport 接口 |
| `plugin/transport/AgentSession.java` | Session 接口 |
| `plugin/transport/TransportCapabilities.java` | 能力声明 |
| `plugin/transport/LegacyTransport.java` | 现有行为封装 |
| `plugin/transport/ACPTransport.java` | ACP Transport 实现 |
| `plugin/transport/ACPBridge.java` | ACP bridge 进程管理 |
| `plugin/transport/ACPTransportFactory.java` | Transport 工厂 |
| `event/mapper/ACPEventMapper.java` (fix) | 修复 completion mapping |
| `test/plugin/transport/ACPTransportTest.java` | Transport 单元测试 |
| `test/plugin/transport/CodexACPIntegrationTest.java` | Codex ACP E2E |
| `resources/cli-adapters/codex-acp.yaml` (update) | ACP 配置 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `ACPEventMapper.java` | 移除 completion→EVIDENCE_VERIFIED 错误映射 |
| `ACPEventMapperTest.java` | 更新 completion 测试断言 |
| `PluginRegistry.java` | 注册 AgentTransportFactory |
| `AGENTS.md` | 更新迁移状态 |

### 不改动的文件

| 文件 | 原因 |
|------|------|
| `CodexPlugin.java` | 内部改为使用 LegacyTransport，行为不变 |
| `ClaudeCodePlugin.java` | 同上 |
| `GenericCLIPlugin.java` | LegacyTransport 复用其逻辑 |
| `ACPCLIPlugin.java` | **删除** — v1 设计错误，已被 AgentTransport 取代 |
| `codex-acp.yaml` (旧版) | **删除** — 被新的配置语法替代 |

---

## 五、验证标准

### 5.1 语义等价验证

对同一个 Task，分别走 Legacy 和 ACP transport：

```
断言：
  1. Task outcome 相同（都成功 / 都失败）
  2. 产生的 Artifact 相同（files changed、git diff）
  3. Verification 结果相同（tests pass/fail）
  4. ACP 提供额外的可观测事实（file_change events、structured permissions）
  5. 两条路径不会互相干扰（隔离性）
```

### 5.2 ACP 特有验证

```
  1. Permission request → PolicyEngine → ApprovalRequest 链路完整
  2. Structured file_change 比 stdout 解析更准确
  3. Session resume 正常工作
  4. Cancel 触发 ACP 协议的 cancel 而非 kill
  5. Subagent transcript 正确捕获
```

### 5.3 回归验证

```
  1. 现有 336 个单元测试全部通过
  2. 现有 13 个 E2E 测试全部通过
  3. CodexPlugin/ClaudeCodePlugin legacy 路径行为完全不变
```

---

## 六、与 M2.5 的关系

```
M2.5（当前 main）：ACPEventMapper + ACPCLIPlugin
  → 这是一个 POC，证明了 JSONL 解析的可行性
  → 但它不是最终架构

M2.5+（本方案）：AgentTransport 抽象层
  → 将 M2.5 的 ACPEventMapper 保留（它是正确的 mapping 层）
  → 将 ACPCLIPlugin 替换为 ACPTransport（真正的协议层）
  → 将 LegacyTransport 从 GenericCLIPlugin 提取
  → 删除 ACPCLIPlugin（v1 设计错误）
  → 删除 codex-acp.yaml（v1 配置语法错误）
```

**M2.5 不是浪费** — ACPEventMapper 的映射逻辑是正确的，只是调用层需要重构。

---

## 七、风险与约束

| 风险 | 缓解 |
|------|------|
| ACP bridge 进程管理复杂 | 第一阶段用 Node.js stdio bridge（成熟），第二阶段考虑 Rust |
| codex-acp API 未稳定 | AgentTransport 接口屏蔽底层变化 |
| ACP event schema 变化 | ACPEventMapper 有 default 分支兜底 |
| Legacy ↔ ACP 行为差异 | 语义等价测试 + 对比验证 |
| 现有测试不被破坏 | 所有改动在 transport 层，Plugin 层不变 |

---

## 八、最终架构愿景

```
                        TeamMind Runtime
                               │
                    Agent Plugin Contract
                               │
                        AgentTransport
                        /            \
                       /              \
              LegacyTransport      ACPTransport
                  │                    │
           ProcessBuilder         ACP Bridge
                  │                    │
            stdout parser      stdio JSON-RPC
                  │                    │
            Agent Events       Agent Events
                  │                    │
            ────────┴───────────────────┘
                         │
                  TeamMind Canonical Events
                         │
            State / Evidence / Ledger / Policy
                         │
                  Mission Control UI
```

**价值：** 未来新增任何 Agent（Aider、Gemini CLI、自定义 Agent），
只需实现一个 Transport（Legacy 或 ACP），Plugin 层零改动。

---

*方案设计：Agnes (v2)*
*日期：2026-08-18*
*修订：根据 Thy985 架构审查反馈*
*关联：M2.5 POC → M2.5+ Transport Abstraction → M6 Rust Runtime*
