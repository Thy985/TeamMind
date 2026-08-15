# TeamMind 统一事件协议 (Unified Event Protocol)

> **所有 CLI Adapter 把自己的行为映射成 TeamMind 标准事件，前端只认识这套协议。**
>
> 这是 TeamMind Runtime 与 Web UI 之间的**契约接口**。

---

## 一、为什么要统一事件协议

### 1.1 问题

每个 CLI 输出格式完全不同：

```
Claude Code: {"type":"assistant","message":{"content":[{"type":"text","text":"..."}]}}
Codex:       {"type":"item.completed","item":{"type":"agent_message","message":"Done."}}
Aider:       # git diff 风格输出
Gemini:      {"type":"result","..."}
OpenCode:    {"event":"...",...}
```

如果前端直接消费这些原始事件，需要为每个 CLI 写一个解析器。

**解决方案**：Adapter 在入口处把 CLI 原始事件**转换成标准 TeamMind 事件**，前端只管标准事件。

### 1.2 原则

```
CLI 原始输出
    ↓
[Adapter 入口] —— 转换 ——→ 标准 TeamMind 事件 ——→ EventBus ——→ WebSocket ——→ Web UI
    ↑                                          ↓
  知道具体 CLI格式                          不知道具体 CLI
```

**前端永远不需要知道：**
- Claude Code 的 JSON 格式是什么
- Codex 的事件类型有哪些
- Aider 的输出是文本还是 diff

**前端只需要知道：**
```
AgentStarted
AgentThinking
ToolCalled
FileChanged
ArtifactCreated
TaskCompleted
ReviewRequested
ReviewCompleted
FindingCreated
TestStarted
TestCompleted
ApprovalRequired
AgentFailed
HandoffRequested
```

---

## 二、事件体系

### 2.1 事件顶层结构

```typescript
interface TeamMindEvent {
  type: EventType;
  timestamp: number;            // epoch ms
  taskId: string;               // 任务 ID
  stepId?: string;              // 步骤 ID（子事件）
  pluginId: string;             // 哪个 Agent Plugin 触发的
  agentId: string;              // 兼容别名（同 pluginId）
  role: string;                 // LEAD / REVIEWER / TESTER / ...
  metadata: Record<string, any>; // 可选附加数据
}
```

### 2.2 事件类型总览

```typescript
type EventType =
  // ─── 生命周期 ─────────────────────────────
  | 'task.started'
  | 'task.completed'
  | 'task.failed'
  | 'task.cancelled'
  | 'task.retrying'

  // ─── Agent 状态 ───────────────────────────
  | 'agent.started'
  | 'agent.thinking'         // Agent 正在思考/执行
  | 'agent.idle'
  | 'agent.completed'
  | 'agent.failed'
  | 'agent.handoff'          // 移交给下一个 Agent

  // ─── 执行细节 ─────────────────────────────
  | 'agent.chunk'            // 流式输出片段（给人看的文字）
  | 'tool.called'            // Agent 调用了某个工具
  | 'tool.result'            // 工具返回结果
  | 'file.changed'           // 文件被修改
  | 'command.running'        // 命令正在执行

  // ─── 产物 ─────────────────────────────────
  | 'artifact.created'       // 新的 Artifact 产出
  | 'artifact.updated'       // Artifact 被修改

  // ─── 验证 ─────────────────────────────────
  | 'evidence.verifying'     // 开始验证
  | 'evidence.verified'      // 验证通过
  | 'evidence.failed'        // 验证失败
  | 'test.started'           // 测试开始
  | 'test.passed'            // 测试通过
  | 'test.failed'            // 测试失败
  | 'test.result'            // 测试结果摘要

  // ─── 审查 ─────────────────────────────────
  | 'review.requested'       // 发起审查
  | 'review.started'         // 审查开始
  | 'finding.created'        // 发现新问题
  | 'finding.resolved'       // 问题已解决
  | 'review.completed'       // 审查完成
  | 'review.approved'        // 审查通过
  | 'review.rejected'        // 审查未通过

  // ─── 决策 ─────────────────────────────────
  | 'decision.made'          // Agent 做了决策
  | 'decision.requires_approval'  // 需要用户审批
  | 'approval.granted'       // 用户批准
  | 'approval.denied'        // 用户拒绝
  | 'approval.auto_approved' // 系统自动批准

  // ─── 路由 ─────────────────────────────────
  | 'routing.decided'        // 路由决策已做出
  | 'routing.skipped'        // 某 Agent 被跳过
  | 'handoff.requested'      // 请求移交
  | 'handoff.accepted'       // 移交被接受

  // ─── 异常 ─────────────────────────────────
  | 'error.critical'         // 严重错误
  | 'error.recoverable'      // 可恢复错误
  | 'retry.initiated'        // 开始重试
  | 'fallback.triggered'     // 触发降级
  | 'plugin.unhealthy'       // Plugin 不健康
  | 'plugin.down'            // Plugin 下线

  // ─── 进化 ─────────────────────────────────
  | 'profile.updated'        // Agent Profile 更新
  | 'drift.detected'         // 检测到漂移
  | 'recommendation.generated' // 推荐已生成
  | 'lesson.learned'         // 学到新 Routing Lesson
```

---

## 三、关键事件详细说明

### 3.1 agent.chunk（流式输出）

```typescript
interface AgentChunkEvent extends TeamMindEvent {
  type: 'agent.chunk';
  metadata: {
    content: string;           // 实际文本片段
    isFinal?: boolean;         // 是否最后一条
    tokenCount?: number;       // 可选：token 数
  };
}
```

**用途**：实时显示 Agent 的思考/输出文字。

**示例**：
```json
{
  "type": "agent.chunk",
  "timestamp": 1723633204000,
  "taskId": "T-182",
  "stepId": "step-3",
  "pluginId": "codex",
  "agentId": "codex",
  "role": "LEAD",
  "metadata": { "content": "Analyzing auth module structure...", "isFinal": false }
}
```

### 3.2 tool.called（工具调用）

```typescript
interface ToolCalledEvent extends TeamMindEvent {
  type: 'tool.called';
  metadata: {
    toolName: string;          // "Read" / "Edit" / "Bash" / ...
    input: Record<string, any>; // 工具参数
    reason?: string;           // Agent 为什么调用这个工具
  };
}
```

**用途**：让前端展示"Agent 正在做什么操作"。

**示例**：
```json
{
  "type": "tool.called",
  "pluginId": "claude-code",
  "role": "REVIEWER",
  "metadata": {
    "toolName": "Read",
    "input": { "path": "src/auth/jwt.ts" },
    "reason": "Reading auth implementation for review"
  }
}
```

### 3.3 file.changed（文件修改）

```typescript
interface FileChangedEvent extends TeamMindEvent {
  type: 'file.changed';
  metadata: {
    filePath: string;
    changeType: 'ADD' | 'MODIFY' | 'DELETE' | 'RENAME';
    diff?: string;             // unified diff（可选，大文件不传）
    linesChanged?: number;     // 行数变化
  };
}
```

**用途**：实时更新文件树 + diff 预览。

### 3.4 evidence.verified（证据验证）

```typescript
interface EvidenceVerifiedEvent extends TeamMindEvent {
  type: 'evidence.verified';
  metadata: {
    evidenceType: 'GIT_DIFF' | 'TEST_EXECUTION' | 'FILE_EXISTENCE' | 'COMMAND_EXIT';
    passed: boolean;
    summary: string;           // 一句话总结
    details?: Record<string, any>;
  };
}
```

**示例**：
```json
{
  "type": "evidence.verified",
  "pluginId": "git-verifier",
  "role": "VERIFIER",
  "metadata": {
    "evidenceType": "GIT_DIFF",
    "passed": true,
    "summary": "All 7 claimed file changes verified in git status",
    "details": { "filesVerified": 7, "filesMissing": 0 }
  }
}
```

### 3.5 test.result（测试报告）

```typescript
interface TestResultEvent extends TeamMindEvent {
  type: 'test.result';
  metadata: {
    framework: string;         // "jest" / "pytest" / "go-test"
    total: number;
    passed: number;
    failed: number;
    skipped: number;
    durationMs: number;
    failures?: Array<{
      testName: string;
      error: string;
      file?: string;
    }>;
  };
}
```

### 3.6 finding.created（审查发现）

```typescript
interface FindingCreatedEvent extends TeamMindEvent {
  type: 'finding.created';
  metadata: {
    severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
    title: string;
    description: string;
    file?: string;
    line?: number;
    suggestion?: string;
    ruleId?: string;           // 如果是规则驱动
  };
}
```

### 3.7 approval.required（需要审批）

```typescript
interface ApprovalRequiredEvent extends TeamMindEvent {
  type: 'approval.required';
  metadata: {
    approvalId: string;        // 用于后续 grant/deny
    question: string;          // 问用户什么
    context?: Record<string, any>;  // 上下文数据
    autoPolicy?: AutoApprovalPolicy;  // 是否有自动审批策略
    timeoutMs?: number;        // 超时自动处理
  };
}

interface AutoApprovalPolicy {
  rule: string;               // 触发自动审批的规则 ID
  fallbackAction: 'APPROVE' | 'DENY' | 'PAUSE';
  reason: string;
}
```

**示例**：
```json
{
  "type": "approval.required",
  "pluginId": "codex",
  "role": "LEAD",
  "metadata": {
    "approvalId": "app-T182-003",
    "question": "Codex proposes to modify src/auth/jwt.ts. Approve?",
    "context": {
      "file": "src/auth/jwt.ts",
      "diff": "@@ -12,7 +12,7 @@\n- session-based\n+ jwt-based\n..."
    },
    "autoPolicy": {
      "rule": "code-review-findings",
      "fallbackAction": "PAUSE",
      "reason": "Security findings require manual review"
    },
    "timeoutMs": 120000
  }
}
```

### 3.8 routing.decided（路由决策）

```typescript
interface RoutingDecidedEvent extends TeamMindEvent {
  type: 'routing.decided';
  metadata: {
    fromAgent: string;
    toAgent: string;
    toRole: string;
    capability: string;
    reason: string;
    scoreBreakdown?: Record<string, number>;  // 各权重得分
  };
}
```

**示例**：
```json
{
  "type": "routing.decided",
  "pluginId": "codex",
  "role": "LEAD",
  "metadata": {
    "fromAgent": "codex",
    "toAgent": "claude-code",
    "toRole": "SECURITY_GATE",
    "capability": "code_review",
    "reason": "Implementation complete, security review required",
    "scoreBreakdown": {
      "projectPerformance": 0.94,
      "philosophyMatch": 1.0,
      "capabilityQuality": 0.8,
      "userPreference": 0.0,
      "costLatency": -0.1
    }
  }
}
```

### 3.9 handoff.requested（移交请求）

```typescript
interface HandoffRequestedEvent extends TeamMindEvent {
  type: 'handoff.requested';
  metadata: {
    fromAgent: string;
    fromRole: string;
    toAgent: string;
    toRole: string;
    summary: string;              // 交接摘要
    artifacts: ArtifactSummary[];
    nextAction?: string;          // 建议下一步
  };
}

interface ArtifactSummary {
  type: string;
  path?: string;
  summary: string;
}
```

### 3.10 profile.updated（Profile 更新）

```typescript
interface ProfileUpdatedEvent extends TeamMindEvent {
  type: 'profile.updated';
  metadata: {
    projectId: string;
    pluginId: string;
    role: string;
    metric: string;              // 'success_rate' | 'avg_iterations' | ...
    oldValue: number;
    newValue: number;
    sampleSize: number;
    reason: string;
  };
}
```

---

## 四、事件到 WebSocket 的序列化

### 4.1 传输格式

```typescript
interface WebSocketFrame {
  // 框架层（Transport 级）
  channel: 'tasks' | 'agents' | 'system' | 'approvals';
  version: number;               // 协议版本，用于兼容
  seq: number;                   // 序列号，用于乱序检测
  heartbeat?: number;            // 心跳间隔 ms（可选）
  
  // 内容层
  payload: TeamMindEvent | SystemEvent | ControlMessage;
}

interface SystemEvent {
  type: 'connected' | 'disconnected' | 'heartbeat';
  timestamp: number;
  meta?: {
    serverVersion?: string;
    clientVersion?: string;
    supportedChannels?: string[];
  };
}

interface ControlMessage {
  type: 'subscribe' | 'unsubscribe' | 'channel_error';
  channel: string;
  metadata?: Record<string, any>;
}
```

### 4.2 订阅模型

```typescript
// 客户端订阅
{
  "channel": "tasks",
  "type": "subscribe",
  "filters": {
    "projectId": "proj-123",
    "eventType": ["agent.chunk", "tool.called", "file.changed"],
    "pluginId": ["claude-code", "codex"]
  }
}

// 服务端只推送匹配的
```

### 4.3 心跳

```typescript
// 每 30 秒一次心跳
{
  "type": "heartbeat",
  "timestamp": 1723633234000,
  "channel": "system"
}
```

---

## 五、Adapter 中的事件映射层

### 5.1 映射层架构

```
┌─────────────────────────────────────────┐
│  ClaudeCodePlugin.invoke(context)       │
│         │                               │
│         ▼                               │
│  [Raw CLI Output Parser]                │  ← 读 stream-json 事件
│         │                               │
│         ▼                               │
│  [TeamMind Event Mapper]                │  ← 翻译成标准事件
│         │                               │
│         ▼                               │
│  EventBus.emit(standardEvent)           │  ← 标准协议
│                                               
└─────────────────────────────────────────┘
```

### 5.2 Claude Code 事件映射

```typescript
// Claude Code 原始输出 → TeamMind 标准事件

// {"type":"assistant","message":{"content":[{"type":"text","text":"..."}]}}
// → agent.chunk

// {"type":"assistant","message":{"content":[{"type":"tool_use","name":"Read","input":{...}}]}}
// → tool.called

// {"type":"user","message":{"content":[{"type":"tool_result",...}]}}
// → tool.result

// {"type":"result","subtype":"success","result":"..."}
// → task.completed

// {"type":"system","subtype":"init",...}
// → agent.started
```

### 5.3 Codex 事件映射

```typescript
// {"type":"item.completed","item":{"type":"agent_message","message":"..."}}
// → agent.chunk

// {"type":"item.completed","item":{"type":"command_execution","command":"npm test","exit_code":0}}
// → command.running + test.started

// {"type":"item.completed","item":{"type":"file_change","file":"src/auth.ts","diff":"..."}}
// → file.changed

// {"type":"thread.completed"}
// → task.completed
```

### 5.4 映射器接口

```typescript
interface EventMapper {
  /** 将 CLI 原始事件映射为 TeamMind 标准事件 */
  map(rawEvent: CliEvent): TeamMindEvent[];
  
  /** 返回该适配器支持的事件类型 */
  supportedEventTypes(): EventType[];
}

// CLI 原始事件（每个 Adapter 自己定义）
interface CliEvent {
  raw: string;           // 原始文本行
  parsed?: any;          // 已解析的 JSON
  type?: string;         // 事件类型
}
```

---

## 六、事件协议版本管理

### 6.1 版本号规则

```typescript
// protocol-version.ts
export const TEAMMIND_EVENT_PROTOCOL_VERSION = 1;

// 每次 breaking change 递增
// Breaking = 事件类型改名 / 字段重命名 / 语义变更
// 非 Breaking = 新增事件类型 / 新增可选字段
```

### 6.2 兼容性规则

参考 Orca 的 `remote-wire-compatibility.md`：

| 规则 | 说明 | 示例 |
|---|---|---|
| **Rule 1**：新可选字段安全 | 旧客户端忽略未知字段 | 在 `finding.created` 加 `suggestion` 字段 |
| **Rule 2**：新事件类型不破坏 | 旧客户端忽略未知 type | 加 `evidence.verifying` 事件 |
| **Rule 3**：事件语义不变 | 字段含义不能变 | `severity` 永远是 'CRITICAL'/'HIGH'/... |
| **Rule 4**：废弃字段标记 | 不要突然删字段 | 旧字段保留但标记 `@deprecated` |

---

## 七、测试规范

### 7.1 映射器单元测试

```typescript
class ClaudeCodeEventMapperTest {
  @Test
  void shouldMapAssistantTextToAgentChunk() {
    CliEvent raw = new CliEvent(
      "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"Analyzing...\"}]}}"
    );
    
    List<TeamMindEvent> mapped = mapper.map(raw);
    
    assertThat(mapped).hasSize(1);
    assertThat(mapped.get(0).type()).isEqualTo("agent.chunk");
    assertThat(((AgentChunkEvent)mapped.get(0)).content()).isEqualTo("Analyzing...");
  }
  
  @Test
  void shouldMapToolUseToToolCalled() {
    CliEvent raw = new CliEvent(
      "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Edit\",\"input\":{\"path\":\"src/main.ts\"}}]}}"
    );
    
    List<TeamMindEvent> mapped = mapper.map(raw);
    
    assertThat(mapped.get(0).type()).isEqualTo("tool.called");
    assertThat(((ToolCalledEvent)mapped.get(0)).toolName()).isEqualTo("Edit");
  }
  
  @Test
  void shouldIgnoreUnknownEventType() {
    CliEvent raw = new CliEvent("{\"type\":\"unknown_event\",\"data\":\"x\"}");
    
    List<TeamMindEvent> mapped = mapper.map(raw);
    
    assertThat(mapped).isEmpty();
  }
  
  @Test
  void shouldHandleMalformedJsonGracefully() {
    CliEvent raw = new CliEvent("{not valid json");
    
    List<TeamMindEvent> mapped = mapper.map(raw);
    
    assertThat(mapped).isEmpty();
  }
}
```

### 7.2 端到端事件流测试

```typescript
@SpringBootTest
class EventFlowIntegrationTest {
  @Autowired EventBus eventBus;
  @Autowired WebSocketBroadcaster broadcaster;
  
  @Test
  void shouldPropagateEventsFromPluginToWebSocket() throws Exception {
    // 1. 注册监听
    BlockingQueue<TeamMindEvent> received = new BlockingQueue<>();
    eventBus.subscribe("agent.chunk", "test", received::add);
    
    // 2. 模拟事件
    TeamMindEvent event = new AgentChunkEvent("t1", "step1", "codex", "LEAD", "Hello");
    eventBus.emit(event);
    
    // 3. 验证收到
    TeamMindEvent got = received.poll(1, SECONDS);
    assertThat(got).isNotNull();
    assertThat(got.type()).isEqualTo("agent.chunk");
    assertThat(got.pluginId()).isEqualTo("codex");
  }
}
```

---

## 八、事件协议速查表

| 场景 | 事件 | 关键字段 |
|---|---|---|
| 任务开始 | `task.started` | objective, team config |
| Agent 开始 | `agent.started` | role, pluginId |
| Agent 思考中 | `agent.thinking` | context summary |
| 流式输出 | `agent.chunk` | content, isFinal |
| 工具调用 | `tool.called` | toolName, input |
| 工具结果 | `tool.result` | toolName, success, output |
| 文件修改 | `file.changed` | filePath, changeType, diff |
| 命令运行 | `command.running` | command, args |
| 测试开始 | `test.started` | framework, command |
| 测试通过 | `test.passed` | count, duration |
| 测试失败 | `test.failed` | testName, error |
| 测试结果 | `test.result` | total, passed, failed |
| 新产物 | `artifact.created` | type, path, summary |
| 证据验证 | `evidence.verified` | evidenceType, passed |
| 新发现 | `finding.created` | severity, title, description |
| 审查通过 | `review.approved` | reviewer, comments |
| 审查拒绝 | `review.rejected` | reviewer, issues |
| 路由决策 | `routing.decided` | fromAgent, toAgent, capability, reason |
| 移交请求 | `handoff.requested` | fromAgent, toAgent, summary |
| 需审批 | `approval.required` | approvalId, question, context |
| 已批准 | `approval.granted` | approvalId, grantedBy |
| 已拒绝 | `approval.denied` | approvalId, reason |
| 任务完成 | `task.completed` | score, summary, evidenceCount |
| 任务失败 | `task.failed` | error, recoverable |
| 性能更新 | `profile.updated` | metric, oldValue, newValue |
| 漂移检测 | `drift.detected` | role, pluginId, trend |
| 推荐生成 | `recommendation.generated` | currentTeam, recommendedTeam |
| 学习新课 | `lesson.learned` | lessonKey, confidence |
| 插件故障 | `plugin.down` | pluginId, reason |
| 系统错误 | `error.critical` | message, recoverable |

---

## 九、与其他文档的关系

| 文档 | 关系 |
|---|---|
| [plugin-system.md](plugin-system.md) | Adapter 实现此协议 |
| [capability-routing.md](capability-routing.md) | Routing 发出 `routing.decided` |
| [role-evolution.md](role-evolution.md) | Evolution 发出 `profile.updated` / `drift.detected` |
| [web-ui-architecture.md](web-ui-architecture.md) | UI 消费此协议 |
| [task-state-machine.md](task-state-machine.md) | State Machine 产生此协议 |

---

**版本**：v0.1 Draft
**最后更新**：2026-08-14
**协议版本**：TEAMMIND_EVENT_PROTOCOL_VERSION = 1