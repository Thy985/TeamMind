# TeamMind Runtime Contract v1

> **这是 TeamMind 的产品本质，与技术栈无关。**
>
> Java 实现和未来的 Rust 实现都必须满足此 Contract 的所有语义。
> 任何偏离 Contract 的"优化"都是架构退化。

---

## 一、Runtime Core 定义

### 1.1 一句话定义

```
TeamMind = Project + Plugin Runtime + Capability Routing + Team Policy + Shared State + Adaptive Evolution
```

### 1.2 核心不变量（Architecture Invariants）

| # | Invariant | 违反即拒绝 PR |
|---|-----------|-------------|
| 1 | Runtime Core 不依赖 Web Host | `runtime/` 不得 import `controller/` |
| 2 | Host 只依赖 Runtime Core，不反向污染 | — |
| 3 | Agent Plugin 不直接修改 Task State | Plugin 只返回结果，状态变更经 State Machine |
| 4 | SQLite/JPA 是 Source of Truth | WebSocket 是 Projection，可丢失可重建 |
| 5 | Reviewer 默认不能修改 Executor Workspace | 两个 worktree 隔离 |
| 6 | Agent 自报结果不是 Evidence | Evidence 必须来自独立验证源 |
| 7 | — | — |

---

## 二、数据模型 Contract

### 2.1 Task（用户意图）

```
Task
├── id: String                    // UUID
├── projectId: String             // 所属项目
├── objective: String             // 自然语言描述
├── taskTypeId: String            // 能力分类（code_refactor / db_migration / ...）
├── state: TaskState              // SUBMITTED/ORCHESTRATING/EXECUTING/DONE/FAILED/CANCELLED
├── createdBy: String             // 用户
├── createdAt: LocalDateTime
└── policyOverrides: Map<String,Object>  // 项目级策略覆盖
```

**生命周期语义：**
- `SUBMITTED` → 用户创建，等待编排
- `ORCHESTRATING` → Pipeline 正在选择 Agent 和执行步骤
- `EXECUTING` → 至少一个 Agent 正在工作
- `DONE` → 所有步骤完成，Evidence 全部 VERIFIED
- `FAILED` → 不可恢复的错误（用户需手动介入）
- `CANCELLED` → 用户主动取消

### 2.2 TaskExecution（一次尝试）

```
TaskExecution
├── id: String
├── taskId: String                // 关联 Task
├── attemptNumber: Int            // 第几次尝试（retry 递增）
├── state: TaskExecutionState     // NEW/PENDING/RUNNING/PAUSED/DONE/FAILED/CANCELLED/NEEDS_APPROVAL
├── currentAgentId: String        // 当前负责执行的 Agent
├── currentStepName: String       // 当前 Pipeline 步骤名
├── routingHistory: List<RouteRecord>  // Agent 切换历史
├── evidence: List<Evidence>      // 收集的证据
├── artifacts: List<Artifact>     // 产出的文件
├── summary: String               // 最终摘要
├── errorReason: String           // 失败原因（如有）
├── durationMs: Long
├── startedAt: LocalDateTime
└── completedAt: LocalDateTime
```

**状态机完整转移：**

```
NEW ──submit──▶ PENDING ──start──▶ RUNNING ──complete──▶ DONE
                            │              │
                          cancel         pauseRequested
                            │              │
                       CANCELLED    PAUSE_REQUESTED ──pauseComplete──▶ PAUSED
                                                     │
                                                  cancel
                                                     │
                                               CANCELLED

RUNNING ──needsApproval──▶ NEEDS_APPROVAL ──approve──▶ APPROVING ──▶ RUNNING
                           │                ──deny──▶ FAILED
                           │
                        retry ──▶ NEW（attemptNumber+1）

RUNNING ──fail──▶ FAILED
PAUSED ──resume──▶ RUNNING
```

### 2.3 ExecutionStep（Pipeline 一步）

```
ExecutionStep
├── id: String
├── executionId: String
├── stepName: String              // YAML pipeline 中的 step id
├── agentId: String               // 负责此步骤的 Agent
├── role: String                  // LEAD / REVIEWER / TESTER
├── state: StepState              // PENDING/RUNNING/DONE/FAILED/SKIPPED
├── input: Map<String,Object>     // 上游步骤输出
├── output: Map<String,Object>    // 本步骤产出
├── durationMs: Long
└── handoffContext: HandoffContext?  // 移交上下文
```

### 2.4 AgentInvocation（一次 Agent 调用）

```
AgentInvocation
├── id: String
├── stepId: String
├── executionId: String
├── pluginId: String              // "claude-code" / "codex" / "atomcode"
├── prompt: String                // 实际发送给 CLI 的 prompt
├── workDir: String               // 工作目录
├── env: Map<String,String>       // 环境变量
├── pid: Long?                    // 进程 ID
├── state: InvocationState        // INIT/RUNNING/DONE/FAILED/TIMED_OUT/CANCELLED
├── stdoutChunks: List<String>    // 流式输出
├── exitCode: Int?
├── durationMs: Long
└── errorMessage: String?
```

**Process Supervisor Contract：**

```
Interface ProcessSupervisor {
    spawn(command, workDir, env) → ProcessHandle
    isAlive(pid) → bool
    readStdout(pid, timeoutMs) → string
    cancel(pid) → void          // SIGTERM → 等待 → SIGKILL
    waitExit(pid, timeoutMs) → ExitStatus
}
```

### 2.5 Evidence（独立验证结果）

```
Evidence
├── id: String
├── executionId: String
├── type: EvidenceType          // GIT_DIFF / FILE_EXISTENCE / TEST_EXECUTION / ...
├── claim: String               // Agent 声称的事实
├── verification: VerificationResult  // COLLECTED / VERIFIED / FAILED / INVALIDATED
├── sourceData: Object          // 原始数据（diff text / test output / ...）
└── verifiedAt: LocalDateTime
```

**Evidence 类型：**

| 类型 | 来源 | 语义 |
|------|------|------|
| `GIT_DIFF` | `git diff` | 代码变更内容 |
| `FILE_EXISTENCE` | `fs.exists()` | 文件/目录存在 |
| `TEST_EXECUTION` | test runner | 测试结果 |
| `COMMAND_EXITED` | process exit code | 命令退出码 |
| `PACKAGE_INSTALLED` | package manager | 依赖安装状态 |
| `ENV_VAR_MODIFIED` | env diff | 环境变量变化 |
| `PROCESS_STARTED` | process spawn | 进程启动确认 |
| `FILE_DELETED` | fs operation | 文件删除 |

**关键语义：Agent 说"我修好了" ≠ Evidence。必须是独立验证源。**

### 2.6 Artifact（产物）

```
Artifact
├── id: String
├── executionId: String
├── type: String                // FILE / DOC / CONFIG / ...
├── path: String                // 在项目中的相对路径
├── sizeBytes: Long
├── createdAt: LocalDateTime
└── metadata: Map<String,Object>
```

### 2.7 RuntimeEvent（事件索引）

```
RuntimeEvent
├── id: String
├── taskId: String
├── type: EventType             // task.started / agent.chunk / tool.called / ...
├── pluginId: String
├── agentId: String
├── role: String
├── timestamp: LocalDateTime
├── stepId: String?
├── metadata: JSON              // 事件载荷
└── tier: EventTier             // HOT/WARM/COLD（分级存储）
```

**Tiered Storage：**

| Tier | TTL | 存储位置 | 用途 |
|------|-----|---------|------|
| HOT | 24h | 内存 + SQLite | 实时 UI 订阅 |
| WARM | 7d | SQLite | replay / 审计 |
| COLD | 永久 | Filesystem (.teammind/events/) | 合规 / 长期归档 |

### 2.8 HandoffContext（Agent 间移交）

```
HandoffContext
├── previousObjective: String
├── completedSteps: List<StepSummary>
├── currentFindings: List<Finding>
├── repoState: RepoSnapshot     // 当前 git 状态摘要
├── activeArtifacts: List<String>
├── failedAttempts: List<FailureRecord>
└── policyConstraints: List<String>
```

**语义：换 Agent 时，新 Agent 必须获得完整的上下文，不能从空白开始。**

---

## 三、Plugin Contract

### 3.1 统一接口

```java
interface Plugin {
    String id();
    PluginType type();           // AGENT / TOOL / VERIFIER / MEMORY / INTEGRATION
    PluginMetadata metadata();
    PluginResult invoke(PluginContext context);
    default CompletableFuture<PluginResult> streamInvoke(PluginContext context, PluginChunkHandler handler)
    default void cancel();
    PluginHealth inspect();
    default List<PluginDependency> dependencies();
    default boolean attemptRecovery();
    default void onLoad();
    default void onUnload();
}
```

### 3.2 CLIAdapter（CLI Agent 专用）

```java
interface CLIAdapter extends Plugin {
    CLIConfig config();                     // 命令/参数/超时/环境变量模板
    void startProcess(String prompt, String workDir) throws IOException;
    Optional<ProcessHandle> getProcessHandle();
    boolean isAlive();
    void kill();
    void parseOutput(String line, String taskId, PluginChunkHandler handler);
}
```

### 3.3 Plugin 健康态七态机

```
DISCOVERED → AVAILABLE → READY ↔ DEGRADED
     ↓          ↓           ↓        ↓
  BLOCKED    HEALTHY     HEALTHY   UNHEALTHY → DOWN
```

- **DISCOVERED**：已注册，依赖未检查
- **AVAILABLE**：依赖检查通过
- **READY**：可用，可以路由
- **DEGRADED**：可用但性能下降（延迟高/成功率低）
- **UNHEALTHY**：部分功能不可用
- **DOWN**：完全不可用
- **BLOCKED**：被策略/审批阻止

---

## 四、Event Protocol Contract

### 4.1 顶层结构

```typescript
interface TeamMindEvent {
  type: EventType;
  timestamp: number;
  taskId: string;
  stepId?: string;
  pluginId: string;
  agentId: string;
  role: string;
  metadata: Record<string, any>;
}
```

### 4.2 事件类型（50+）

**生命周期：** `task.started/completed/failed/cancelled/retrying`
**Agent：** `agent.started/thinking/idle/completed/failed/handoff`
**执行：** `agent.chunk/tool.called/tool.result/file.changed/command.running`
**产物：** `artifact.created/updated`
**验证：** `evidence.verifying/verified/failed/test.started/passed/failed/result`
**审查：** `review.requested/started/finding.created/resolved/completed/approved/rejected`
**决策：** `decision.made/requires_approval/granted/denied/auto_approved`
**路由：** `routing.decided/skipped/handoff.requested/accepted`
**异常：** `error.critical/recoverable/retry.initiated/fallback.triggered/plugin.unhealthy/down`
**进化：** `profile.updated/drift.detected/recommendation.generated/lesson.learned`

### 4.3 Adapter 映射原则

```
CLI 原始输出 ──[Adapter.parseOutput]──▶ TeamMindEvent ──[EventBus]──▶ 所有订阅者
     ↑                                                                  ↓
  知道具体格式                                              不知道具体 CLI
```

**前端永远不需要知道** Claude 的 JSON 格式或 Codex 的事件类型。

---

## 五、Storage Contract

### 5.1 四类存储及其职责

| 数据类别 | 存储 | 理由 |
|---------|------|------|
| Project description | `.teammind/project.md` | 人类+Agent 可读可编辑 |
| Team configuration | `.teammind/team.yaml` | git diff 可追溯 |
| Policies | `.teammind/policy.yaml` | 决策可审计 |
| ADR / decisions | `.teammind/decisions/ADR-*.md` | 长期记忆 |
| Lessons / routing | `.teammind/memory/routing.md` | Agent 知识库 |
| Task brief | `.teammind/tasks/T-*.md` | 人类可读上下文 |
| **Execution state** | **SQLite (`runtime.db`)** | 高频变化，需要事务一致性 |
| **Approval state** | **SQLite** | 状态机驱动，需原子更新 |
| **Event index** | **SQLite** | 有序可查询，支持 replay |
| **Performance metrics** | **SQLite** | 聚合查询，索引依赖 |
| Git diff | Git（直接） | 不可变，版本控制原生 |
| Agent raw output | Filesystem (`.teammind/artifacts/`) | 结构化内容，按需加载 |
| Large artifacts | Filesystem / Git | 避免 SQLite TEXT 列膨胀 |

### 5.2 关键约束

- **SQLite 是 Runtime State Store，不是 Project Knowledge Base**
- Project 知识用 Markdown/YAML，Agent 直接读取文件
- SQLite 只存"发生了什么"（执行事实），不存"这个项目是什么"（项目知识）
- Flyway migration 管理 schema 演进

---

## 六、Persistence Contract（Storage Interface）

> **这些接口是 Java 和 Rust Provider 之间的契约边界。**

```java
// Java 中定义，Rust 中实现等价接口

interface TaskStore {
    Task create(Task task);
    Task findById(String id);
    Task updateState(String id, TaskState state);
    List<Task> findByProject(String projectId);
    List<Task> findByState(TaskState state);
}

interface ExecutionStore {
    TaskExecution create(TaskExecution execution);
    TaskExecution findById(String id);
    TaskExecution updateState(String id, TaskExecutionState state, Map<String,Object> extra);
    List<ExecutionStep> findSteps(String executionId);
    void saveStep(ExecutionStep step);
}

interface EventStore {
    void append(RuntimeEvent event);
    List<RuntimeEvent> findByTask(String taskId, int limit, long afterTs);
    List<RuntimeEvent> replayFrom(String taskId, long fromTs);
    void deleteOlderThan(LocalDateTime cutoff);  // COLD tier 清理
}

interface EvidenceStore {
    Evidence collect(Evidence evidence);
    Evidence verify(String id, VerificationResult result);
    List<Evidence> findByExecution(String executionId);
}

interface ArtifactStore {
    Artifact save(Artifact artifact);
    List<Artifact> findByExecution(String executionId);
}

interface StateStore {        // 统一入口，组合以上所有
    TaskStore tasks();
    ExecutionStore executions();
    EventStore events();
    EvidenceStore evidence();
    ArtifactStore artifacts();
}
```

**Rust 实现替代方案：**
- `StateStore` → `rusqlite` + 手动 SQL（或直接 sea-orm）
- `EventStore` → 追加写入 + 定期归档到文件系统
- `EvidenceStore` / `ArtifactStore` → 同 StateStore

---

## 七、Feature Flag 切换机制

```yaml
# application.yml
teammind:
  runtime:
    provider: java          # ← 当前默认
    # provider: rust        # ← 切换至此即使用 Rust Runtime
```

**过渡期支持混合模式：**
```yaml
teammind:
  runtime:
    providers:
      process: rust         # Process Supervisor 已迁移
      workspace: java       # Workspace 还在 Java
      persistence: java     # 持久化还在 Java
      event: rust           # 事件流已迁移
```

**最终状态：**
```yaml
teammind:
  runtime:
    provider: rust          # 全部迁移完成
```

---

## 八、验证策略

### 8.1 Contract Equivalence Test

对同一个 Task，在 Java 和 Rust Provider 下分别执行：

```
Test: RealTask equivalence
Given: 同一份任务 prompt 和 project state
When:  Java Provider 执行
And:   Rust Provider 执行
Then:  以下指标完全一致：
  - TaskExecutionState 转移序列
  - RuntimeEvent 序列（type + timestamp delta ≤ 1s）
  - Evidence 列表（type + verification result）
  - Artifact 列表（path + size）
  - 最终退出码
```

### 8.2 Java Exit Gate

```
[ ] Rust Runtime 覆盖全部 16 项硬标准
[ ] Contract Equivalence Test 100% 通过
[ ] Java traffic = 0（至少 7 天）
[ ] Legacy API usage = 0
[ ] Real CLI E2E 在 Rust Provider 下通过（Codex + Claude + Atomcode）
```

---

## 九、文档版本

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-08-18 | 初始冻结，基于 Phase 4 实现 |

---

**关联文档：**
- [Task State Machine](../runtime/task-state-machine.md)
- [Event Protocol](../runtime/event-protocol.md)
- [Plugin System](../runtime/plugin-system.md)
- [Control Modes](../runtime/control-modes.md)
- [Architecture Invariants](../architecture/invariants.md)
