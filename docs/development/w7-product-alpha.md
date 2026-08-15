# W7: Product Alpha — Runtime Core + Mission Control

> **目标：把 TeamMind 从"零件集合"变成一个可真实使用的 Project AI Team Control Plane。**
>
> **核心原则：先把单任务闭环做到铁一样可靠，再做多 Agent 协作。**
>
> **危险信号：做出一个能 Demo、但不能可靠运行的工作流引擎。**

---

## 一、方向确认

### 原计划的问题

之前的计划把重点放在"功能清单"上：

```
PipelineOrchestrator + YAML DSL + 6 个 Control API + Vue 页面
```

**低估了以下问题：**

| # | 问题 | 影响 |
|---|------|------|
| 1 | 进程生命周期（kill / SIGTERM / 超时 / 子进程） | 取消可能卡死或残留进程 |
| 2 | Task vs Execution 分离 | Retry / Reroute 无法建模 |
| 3 | EventBus vs DB 谁是 Source of Truth | Crash recovery 不可能 |
| 4 | WebSocket 断线重连无持久事件 | 前端状态永远滞后 |
| 5 | Pause 不是瞬时的（等待 tool 完成） | 状态机需要 PAUSE_REQUESTED 中间态 |
| 6 | Reroute 需要完整 HandoffContext | 换 Agent 后上下文丢失 |
| 7 | Evidence 没有生命周期 | "验证过"不等于"永远有效" |
| 8 | 重启后不知道哪个进程还活着 | 状态不一致 |
| 9 | 多 Agent 共享工作目录有竞态 | worktree 是基础设施，不是可选 |
| 10 | 前端直接订阅事件猜状态 | 状态漂移，UI 不可信 |

### 修正后的方向

```
正确顺序：
Runtime Contract（数据模型 + 状态语义）
    ↓
Single-Agent Runtime（单任务闭环，非常可靠）
    ↓
Multi-Agent Handoff（在可靠 Runtime 基础上做增量）
    ↓
Persistent Event + State Projection（WebSocket 不再丢事件）
    ↓
Mission Control（窄切口：TaskDetail）
    ↓
Recovery + Worktree（让系统真正可运行在生产环境）
```

---

## 二、Phase 1A：Runtime Contract（地基）

> **这是整个 Phase 的核心。地基不稳，后面全部白做。**

### 2.1 数据模型（必须严格定义）

#### 核心实体关系

```
Project
  ↓ 1:N
Task                              ← 用户提交的目标（不变）
  ↓ 1:N
TaskExecution                     ← 每次尝试（可变，有生命周期）
  ↓ 1:N
ExecutionStep                     ← Pipeline 的每个步骤
  ↓ 1:N
AgentInvocation                   ← 一次 CLI 进程调用
  ↓ 1:N
Event                             ← 持久化事件（用于重放）
  ↓ 1:N
Artifact                          ← Agent 产出的结构化产物
  ↓ 1:N
Evidence                          ← 独立验证的证据（有生命周期）
  ↓ 1:N
Finding                           ← Review 发现的问题
  ↓
ApprovalRequest                   ← 人工审批请求
```

#### Task vs TaskExecution vs ExecutionStep

```java
// Task：用户的意图，不变
record Task(
    String id;
    String projectId;
    String objective;           // "Add UserService with JWT validation"
    String taskTypeId;
    TaskState state;            // SUBMITTED / RUNNING / DONE / FAILED / CANCELLED
    LocalDateTime createdAt;
)

// TaskExecution：每次具体执行尝试（可有多次）
record TaskExecution(
    String id;
    String taskId;              // FK → Task
    String pipelineId;          // 使用哪个 Pipeline 定义
    TaskExecutionState state;   // NEW / PENDING / RUNNING / PAUSED / NEEDS_APPROVAL / DONE / FAILED / CANCELLED / ABANDONED
    Integer attemptNumber;      // 第几次尝试（retry 递增）
    
    // 执行上下文快照
    Map<String, Object> context;
    
    // 最终产物
    String summary;
    Long durationMs;
    
    LocalDateTime createdAt;
    LocalDateTime startedAt;
    LocalDateTime completedAt;
)

// ExecutionStep：Pipeline 的一个步骤
record ExecutionStep(
    String id;
    String executionId;         // FK → TaskExecution
    String stepName;            // "implement" / "review" / "verify"
    String agentId;             // "codex" / "claude-code" / "git-verifier"
    String role;                // "LEAD" / "REVIEWER" / "VERIFIER"
    ExecutionStepState state;   // PENDING / STARTED / RUNNING / COMPLETED / FAILED / SKIPPED
    String prompt;              // 实际发送给 Agent 的 prompt（含上下文注入）
    String outputSummary;       // Agent 输出的摘要
    LocalDateTime startedAt;
    LocalDateTime completedAt;
)
```

#### AgentInvocation vs ExecutionStep

```java
// AgentInvocation：一次真实的 CLI 进程调用
record AgentInvocation(
    String id;
    String stepId;              // FK → ExecutionStep
    String pluginId;            // "codex" / "claude-code"
    String command;             // 实际执行的命令
    int exitCode;               // -1 = 未执行 / 超时 / kill
    Long durationMs;
    
    // 进程状态（用于恢复）
    ProcessHandle processHandle; // 重启后可查是否还活着
    LocalDateTime startedAt;
    LocalDateTime completedAt;
)
```

**为什么需要 AgentInvocation 单独表？**
- 重启后需要知道哪个进程还活着（`ProcessHandle.isAlive()`）
- 超时、kill、正常完成的判定
- 便于展示"最近一次调用花了多久、结果是什么"

#### Evidence 生命周期

```
CLAIMED（Agent 自报结果）
    ↓ 通过 GitVerifier / TestRunner 独立验证
COLLECTED（已收集到证据）
    ↓ 人工/自动判定
VERIFIED（证据可信）
    ↓
   ┌──────┐
   │      │ 后续 Agent 又修改了相关文件
INVALIDATED（证据失效，需要重新验证）
```

```java
// Evidence：独立验证的证据，有明确生命周期
record Evidence(
    String id;
    String invocationId;        // FK → AgentInvocation
    EvidenceType type;          // GIT_DIFF / TEST_EXECUTION / FILE_EXISTENCE / COMMAND_EXIT
    EvidenceStatus status;      // CLAIMED / COLLECTED / VERIFIED / INVALIDATED
    
    // 证据内容
    String description;         // "3 files changed, +42/-8"
    Map<String, Object> data;   // 结构化数据
    
    // 时效性绑定
    String baseCommit;          // diff 的基准 commit
    String artifactHash;        // 对应的 artifact hash（变化后自动 invalid）
    
    LocalDateTime collectedAt;
    LocalDateTime verifiedAt;
    LocalDateTime invalidatedAt;
)

enum EvidenceStatus {
    CLAIMED,     // Agent 声称完成了
    COLLECTED,   // Verifier 已收集到证据
    VERIFIED,    // 证据可信
    INVALIDATED  // 因后续变更而失效
}
```

#### Artifact 绑定

```java
// Artifact：Agent 产出的结构化产物
record Artifact(
    String id;
    String invocationId;        // FK → AgentInvocation
    String type;                // CODE_DIFF / REVIEW_FINDINGS / IMPLEMENTATION_PLAN / EVIDENCE
    String summary;
    Map<String, Object> data;   // 结构化内容
    
    // 关联的证据
    List<String> evidenceIds;
    
    // 何时生成的（用于版本管理）
    LocalDateTime createdAt;
)
```

**关键约束：Artifact 和 Evidence 都绑定到 Invocation，不是绑定到 Task。**
- 同一个 Task 可以多次 Invocation（retry）
- 每次 Invocation 有自己的 Artifact 和 Evidence
- Evidence 失效时，对应的 Artifact 不会自动失效（需要上层逻辑处理）

---

### 2.2 状态机（严格定义）

#### TaskState（用户可见的最终状态）

```
SUBMITTED → ORCHESTRATING → EXECUTING → VERIFYING → DONE
                                       ↓
                              NEEDS_APPROVAL
                              ↓         ↓
                            APPROVED   DENIED
                              ↓         ↓
                            EXECUTING → ... → DONE / FAILED
                                       ↓
                              RETRYING → EXECUTING
                                       ↓
                              CANCELLED / ABANDONED
```

#### TaskExecutionState（内部执行状态，更细粒度）

```
NEW
  ↓ submit()
PENDING（等待执行资源）
  ↓ start()
RUNNING
  ├─ pauseRequested() → PAUSE_REQUESTED
  │       ↓ 等待当前 tool 完成
  │     PAUSED
  │       ↓ resume()
  │     RUNNING
  │
  ├─ complete() → DONE
  ├─ fail(error) → FAILED
  ├─ needApproval() → NEEDS_APPROVAL
  │       ↓ approve() → APPROVING → RUNNING
  │       ↓ deny() → ABANDONED
  ├─ cancel() → CANCELLED
  └─ retry() → RETRYING → PENDING
```

**关键决策：PAUSE_REQUESTED 是中间态，不是最终态。**
- 当用户点 Pause，状态变为 `PAUSE_REQUESTED`
- 当前正在运行的 tool 继续执行完
- Tool 完成后进入 `PAUSED`
- 这样可以避免"Agent 正在写文件时强制 kill"的数据损坏

#### State Transition Rules

```
RUNNING  → PAUSE_REQUESTED  (triggered by user)
PAUSE_REQUESTED → PAUSED    (triggered when current tool completes)
PAUSED → RUNNING            (triggered by resume())
RUNNING → DONE              (triggered when all steps complete)
RUNNING → NEEDS_APPROVAL    (triggered by Critical finding)
NEEDS_APPROVAL → APPROVING → RUNNING (triggered by approve())
NEEDS_APPROVAL → ABANDONED  (triggered by deny())
RUNNING → FAILED            (triggered by unhandled exception)
ANY → CANCELLED             (triggered by user cancel, with process cleanup)
```

---

### 2.3 Event-Command-State 边界（严格分离）

```
┌──────────────────────────────────────────────────────────────┐
│                       Command Layer                           │
│  (API: POST /tasks/{id}/pause, POST /tasks/{id}/cancel)     │
│  → 读取当前状态                                                │
│  → 验证状态转移合法（State Machine）                           │
│  → 写入新的持久状态（DB）                                      │
│  → 发出 Domain Event                                          │
└──────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│                      Event Bus Layer                          │
│  (Internal: EventBus.emit(TASK_STATE_CHANGED))               │
│  → 通知所有订阅者                                              │
│  → TaskEventBridge 捕获 → 推送到 WebSocket                   │
│  → PerformanceTracker 捕获 → 更新 PerformanceRecord          │
│  → RecoveryService 捕获 → 记录 recovery point                │
└──────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│                    State Projection Layer                     │
│  (WebSocket sends current state snapshot, not just events)   │
│  → GET /tasks/{id} returns full current state                │
│  → WebSocket sends state diff on every change                │
│  → Browser rebuilds state from events + snapshot             │
└──────────────────────────────────────────────────────────────┘
```

**核心原则：Database = 持久化真理；EventBus = 瞬时传播；WebSocket = 表现层投影。**

---

### 2.4 Handoff Protocol（Reroute 的基础）

```java
// 当 Agent A 切换到 Agent B 时，传递的上下文
record HandoffContext(
    String fromAgent;          // "codex"
    String toAgent;            // "claude-code"
    String objective;          // 原始任务目标
    String currentStage;       // "IMPLEMENTATION" / "REVIEW" / "VERIFICATION"
    
    // Agent A 已经产生的产物
    List<Artifact> artifacts;
    
    // Agent A 执行过程中的事件（摘要，不是全文）
    List<String> relevantEvents;
    
    // 当前工作目录状态
    String repoState;          // git status 摘要
    String workingTreeSnapshot; // 文件变更摘要
    
    // 已知问题和限制
    List<Finding> openFindings;
    List<String> constraints;
    
    // 上一个 Agent 的输出摘要
    String previousOutputSummary;
)
```

**Reroute 时的行为：**
1. 停止当前 Agent 进程（优雅，等 tool 完成）
2. 收集 `HandoffContext`（Artifacts + Findings + Repo State）
3. 注入到下一个 Agent 的 prompt 中
4. 创建新的 `AgentInvocation` + `ExecutionStep`
5. 状态从 `RUNNING` → `REROUTING` → `RUNNING`

---

### 2.5 Recovery 模型（重启后恢复）

**启动时检查：**

```java
@Component
public class RecoveryService implements CommandLineRunner {

    @Override
    public void run(String... args) {
        // 1. 找出所有 RUNNING / PAUSE_REQUESTED 状态的 TaskExecution
        List<TaskExecution> running = executionRepo.findByStateIn(
            List.of(RUNNING, PAUSE_REQUESTED, REVERTING));
        
        for (TaskExecution exec : running) {
            // 2. 检查对应的 CLI 进程是否还活着
            Optional<ProcessHandle> aliveProcess = findAliveProcess(exec);
            
            if (aliveProcess.isPresent()) {
                // 进程还活着 → 标记为 RECOVERING，等待用户决定
                exec.setState(RECOVERING);
                execRepo.save(exec);
                eventBus.emit(TASK_RECOVERED, exec.getId());
            } else {
                // 进程已死 → 检查是否有未完成步骤
                List<ExecutionStep> incompleteSteps = 
                    stepRepo.findByExecutionIdAndStateIn(
                        exec.getId(), List.of(RUNNING, STARTED));
                
                if (!incompleteSteps.isEmpty()) {
                    // 有未完成步骤 → 标记 FAILED，等待用户 retry
                    exec.setState(FAILED);
                    exec.setSummary("Crashed during execution");
                    execRepo.save(exec);
                    eventBus.emit(TASK_FAILED, exec.getId(), 
                        Map.of("reason", "PROCESS_DIED"));
                } else {
                    // 没有未完成步骤 → 可能是已完成但事件没落地
                    // 通过 Artifact + Evidence 反推最终状态
                    inferFinalState(exec);
                }
            }
        }
        
        // 3. 找出所有 NEEDS_APPROVAL 的 Task
        // → 恢复 ApprovalRequest 列表，通知用户
        List<ApprovalRequest> pending = approvalRepo.findByResult(PENDING);
        for (ApprovalRequest req : pending) {
            eventBus.emit(APPROVAL_PENDING_RECOVERED, req.getTaskId(), req.getId());
        }
    }
}
```

---

### 2.6 Storage Architecture Principles

> **SQLite 是 TeamMind 的 Runtime State Store，不是唯一存储层。**
>
> 核心原则："Markdown/YAML 是项目记忆和配置，SQLite 是运行时事实。"

#### 四类存储及其职责

\\\	ext
                    TeamMind Storage
                           │
         ┌─────────────────┼─────────────────┐
         ↓                 ↓                 ↓
   Human/AI readable   Runtime            Artifact
         │                 │                 │
    Markdown / YAML     SQLite          Git / Filesystem
\\\

| 数据类别 | 存储位置 | 理由 |
|---------|---------|------|
| Project description | \project.md\ | 人类 + Agent 可读可编辑 |
| Team configuration | \	eam.yaml\ | git diff 可追溯，Agent 可解析 |
| Policies | \policy.yaml\ | 决策可审计，可直接 git diff |
| ADR / decisions | \.teammind/decisions/ADR-*.md\ | 长期记忆，支持 grep + review |
| Lessons / routing | \.teammind/memory/routing.md\ | Agent 直接读取的知识库 |
| Task brief | \.teammind/tasks/T-*.md\ | 人类可读的任务上下文 |
| Execution state | SQLite (\	ask_executions\) | 高频变化，需要事务一致性 |
| Approval state | SQLite (\pproval_requests\) | 状态机驱动，需原子更新 |
| Event index | SQLite (\untime_events\) | 有序可查询，支持 replay |
| Performance metrics | SQLite (\performance_records\) | 聚合查询，索引依赖 |
| Git diff | Git (直接) | 不可变，版本控制原生 |
| Agent raw output | Filesystem (\.teammind/artifacts/\) | 结构化内容，按需加载 |
| Large artifacts | Filesystem / Git | 避免 SQLite TEXT 列膨胀 |

#### \.teammind/\ 目录结构

\\\	ext
project/
├── .teammind/
│   ├── team.yaml                  ← 团队配置（角色→Agent绑定）
│   ├── policy.yaml                ← 项目治理规则
│   ├── project.md                 ← 项目描述（Architecture, ADRs, rules）
│   │
│   ├── decisions/                 ← 长期决策记录
│   │   ├── ADR-001.md
│   │   └── ADR-002.md
│   │
│   ├── memory/                    ← Agent 可读知识库
│   │   ├── lessons.md             ← 通用经验
│   │   └── routing.md             ← "这个类型任务适合用什么 Agent"
│   │
│   ├── tasks/                     ← 任务草稿/brief
│   │   ├── T-001.md
│   │   └── T-002.md
│   │
│   ├── artifacts/                 ← Agent 产出（按 task 组织）
│   │   ├── T-001/
│   │   │   ├── diff.patch         ← 代码变更
│   │   │   ├── review.json        ← 审查结果（机器协议）
│   │   │   └── test-report.json   ← 测试报告
│   │   └── ...
│   │
│   └── runtime.db                 ← SQLite：仅运行时状态
│
├── src/
├── tests/
└── ...
\\\

#### 分层理由

**Markdown/YAML 的优势（SQLite 没有）：**
- \git diff .teammind/\ 可追溯团队配置、决策演进
- Agent 和人类都能直接阅读、grep、编辑
- 无需 ORM 映射，天然版本控制
- 推荐文件（如 routing.md）直接服务于 Capability Routing

**SQLite 的适用场景（Markdown 不适合）：**
- 高频写（每次 tool call 都产生事件）
- 事务一致性（证据验证 + 状态转移必须原子）
- 复杂查询（"过去30天 Codex 在 implementation 角色的成功率"）
- 有序事件（event replay 依赖 id 排序）

**分层设计让两者互补，而非互斥。**

#### Storage Provider 接口（Phase 1C 引入）

\\\java
public interface StorageProvider {
    StateStore state();         // SQLite — 事务性运行时状态
    KnowledgeStore knowledge(); // Markdown/YAML — 项目记忆和配置
    ArtifactStore artifacts();  // 混合 — 元数据 SQLite，大文件 FS/Git
    ConfigStore config();       // YAML/JSON — 团队配置和 Policy
}
\\\

**Phase 1A/1B 阶段**：只实现 \StateStore\（SQLite），KnowledgeStore 留接口不实现。
**Phase 1C**：开始引入 KnowledgeStore，将 routing.md、lessons.md 写入文件系统。

## 三、Phase 1B：Single-Agent Runtime（先做好一条链）

> **不急着做 Codex → Claude → Verifier，先把 Codex 一条链做到可靠。**

### 3.1 最小可行 Pipeline（单 Agent）

```yaml
# pipelines/single-agent.yaml
name: "single-agent"
steps:
  - name: implement
    role: LEAD
    agent: codex
    prompt: "{{objective}}"
    timeout: 300000          # 5 minutes
    on_failure: retry_or_fail
    verification:
      - type: git-verifier
      - type: test-runner-verifier
```

### 3.2 单 Agent 必须支持的控制操作

| 操作 | 触发时机 | 状态变化 | 验证点 |
|------|---------|---------|--------|
| **Pause** | 用户点击 | RUNNING → PAUSE_REQUESTED → PAUSED | 当前 tool 完成后才真正 pause |
| **Resume** | 用户点击 | PAUSED → RUNNING | 从上次步骤继续 |
| **Cancel** | 用户点击 | RUNNING → CANCELLED | kill 进程 + 清理 worktree |
| **Retry** | 用户点击 / 自动 | FAILED → RETRYING → PENDING | 新 Invocation，保留 HandoffContext |
| **Timeout** | 系统触发 | RUNNING → FAILED | 强制 kill 进程 |

### 3.3 验收标准（不是"能跑"，而是"可靠"）

```
[ ] 正常完成：Task → DONE，Evidence VERIFIED，Artifact 持久化
[ ] Cancel 中：进程被 kill，状态变为 CANCELLED，无残留进程
[ ] Timeout：超时时强制 kill，状态变为 FAILED
[ ] 重启恢复：进程死了 → FAILED；进程还活着 → RECOVERING 等用户决策
[ ] Pause → Resume：tool 完成后才 pause，resume 后继续执行
[ ] Retry：创建新的 Execution，保留 Objective 和 Context
[ ] 全量测试通过（无回归）
```

---

## 四、Phase 1C：Multi-Agent Handoff（在可靠基础上做增量）

### 4.1 第一个 Multi-Agent Pipeline

```yaml
# pipelines/review-loop.yaml
name: "review-loop"
steps:
  - name: implement
    role: LEAD
    agent: codex
    prompt: |
      任务：{{objective}}
      
      注意：代码必须符合以下约束：
      {{constraints}}
      
      完成后输出文件变更列表。
    output: CODE_DIFF
    handoff: review

  - name: review
    role: REVIEWER
    agent: claude-code
    prompt: |
      请审查以下实现：
      
      任务目标：{{objective}}
      变更文件：{{artifacts.implement.files}}
      变更摘要：{{artifacts.implement.summary}}
      
      关注点：
      1. 安全问题（SQL 注入、XSS、权限）
      2. 架构一致性
      3. 测试覆盖
      
      输出格式：每项 finding 包含 severity、file、line、description、suggestion
    output: REVIEW_FINDINGS
    on_critical: request_approval
    on_success: verify
    handoff: verify

  - name: verify
    role: VERIFIER
    agents: [git-verifier, test-runner-verifier]
    output: EVIDENCE
    on_all_pass: done
    on_any_fail: review  # 带回 review 步骤
```

### 4.2 验收标准

```
[ ] Codex 实现 → Claude 审查 → Verifier 验证 → DONE
[ ] Claude 发现 CRITICAL → NEEDS_APPROVAL → 用户批准 → 继续
[ ] Claude 发现 HIGH → 返回 Codex 修复 → 重新审查
[ ] Verifier 失败 → 返回 Claude 重新审查
[ ] 所有中间状态可暂停、可恢复
[ ] 完整事件链持久化
[ ] PerformanceRecord 正确写入（Codex + Claude 各一条）
```

---

## 五、Phase 2：Mission Control（窄切口启动）

### 5.1 第一条 Vertical Slice（只做 TaskDetail）

```
frontend/src/views/
└── TaskDetail.vue    ← 唯一必须的页面
```

**TaskDetail 必须回答的 6 个问题：**

```
1. 现在谁在干什么？        → Agent 卡片 + 当前步骤
2. 为什么轮到它？          → 路由决策记录（Capability + Score）
3. 改了什么？              → Artifact 列表（文件变更）
4. 验证了吗？              → Evidence 面板（verified / pending）
5. 哪里失败了？            → Finding 列表（severity + description）
6. 我需要介入吗？          → ControlButtons（如果 NEEDS_APPROVAL）
```

### 5.2 状态投影（不是事件流）

**错误做法：前端收到事件自己猜状态**
```typescript
// ❌ 前端维护自己的状态，容易漂移
events.forEach(e => {
  if (e.type === 'TASK_STARTED') setState('running')
  if (e.type === 'TASK_COMPLETED') setState('done')
})
```

**正确做法：后端发状态快照，前端只负责渲染**
```typescript
// ✅ 后端发完整状态，前端信任后端
GET /tasks/{id}           → 初始快照
WebSocket /tasks/{id}     → 状态变更时推送完整快照
```

WebSocket 消息格式：
```json
{
  "type": "STATE_UPDATE",
  "taskId": "abc-123",
  "state": {
    "taskState": "EXECUTING",
    "executionState": "RUNNING",
    "currentStep": "implement",
    "currentAgent": "codex",
    "controlMode": "SUPERVISED",
    "pendingApprovals": [],
    "latestArtifact": { "type": "CODE_DIFF", "files": 3 },
    "evidenceSummary": { "verified": 2, "pending": 1 }
  },
  "snapshotVersion": 42
}
```

### 5.3 Event Replay（断线恢复）

```
浏览器第一次连接：
  GET /tasks/{id}/events?limit=100   → 最近 100 条事件（用于补历史）

之后实时：
  WebSocket → STATE_UPDATE（状态快照）

断线重连：
  GET /tasks/{id}/events?after=42    → 从 snapshotVersion=42 之后的所有事件
  然后用这些事件重放，直到追上最新快照
```

---

## 六、Phase 3：持久化事件 + Worktree

### 6.1 持久化事件存储

```java
// Event 实体：所有事件持久化到 DB
@Entity
@Table(name = "events")
public class Event {
    @Id
    private Long id;                    // 自增，有序
    
    @Column(nullable = false)
    private EventType type;
    
    @Column(nullable = false)
    private String taskId;
    
    @Column(nullable = false)
    private String pluginId;
    
    @Column(nullable = false)
    private String role;
    
    @Column(columnDefinition = "TEXT")
    private String payload;             // JSON
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
```

**EventBus 改为双写：**
```java
public void emit(EventType type, String taskId, ...) {
    Event event = new Event(type, taskId, ...);
    eventRepo.save(event);            // 先持久化
    internalBus.emit(event);          // 再广播给内存订阅者
}
```

这样即使 WebSocket 断了，浏览器重连时可以 `GET /tasks/{id}/events?after=N` 补全。

### 6.2 Worktree 隔离（多 Agent 共享工作区的基础设施）

```
TeamMind repo
│
├── .agents/
│   ├── codex/          git worktree — codex 在这里修改代码
│   ├── claude/         git worktree — claude 在这里 review（只读）
│   └── main/           主工作区 — 合并后的最终代码
│
└── backend/data/worktrees/   worktree 元数据
    ├── codex.json         { branch, path, lastSync }
    └── claude.json        { branch, path, lastSync }
```

**Worktree 操作：**
```
1. Task 开始 → 为 Lead Agent 创建 worktree
2. Codex 在 worktree 中执行 → git add/commit
3. Review 阶段 → Claude 在 main worktree 中 review（只读）
4. 审批通过 → 将 worktree 的 commit 合并到 main
5. 审批拒绝 → discard worktree
```

---

## 七、Exit Criteria（Product Alpha 的完成标准）

### 硬标准（不满足就不能算 Alpha）

```
[ ] H1: 创建 Project → 提交 Task → Codex 执行 → Verifier 验证 → DONE
      整个链路完整跑通，状态正确，数据持久化

[ ] H2: Pause / Resume / Cancel 在真实执行中正常工作
      （不是空跑，是有 CLI 进程在执行时操作）

[ ] H3: 服务重启后，正在执行的 Task 状态可恢复
      （进程死了 → FAILED；用户能看到并 retry）

[ ] H4: WebSocket 断线重连后，前端状态与后端一致
      （通过 event replay 补全缺失事件）

[ ] H5: Mission Control TaskDetail 页面能实时显示：
        当前状态、当前 Agent、Live Events、
        Artifact、Evidence、Control Buttons

[ ] H6: Evidence 验证结果与 Task 最终状态绑定
        （Verifier PASS → Evidence VERIFIED → Task 可完成）

[ ] H7: 全量测试通过（无回归，217+ 测试）
```

### 软标准（Alpha 后可迭代）

```
[ ] S1: Multi-Agent Pipeline（Codex → Claude → Verifier）跑通
[ ] S2: Approval Workflow（CRITICAL finding → 人工审批）
[ ] S3: Reroute（换 Agent 时传递 HandoffContext）
[ ] S4: Performance Profile 在 UI 上展示
[ ] S5: Team Recommendation 可解释、可审计
[ ] S6: Worktree 隔离机制
[ ] S7: 前端有 ProjectList、History、Analytics 页面
```

---

## 八、执行顺序

```
Week 1:  Phase 1A（Runtime Contract）
         - 数据模型定稿
         - 状态机文档
         - HandoffProtocol 定义
         - Recovery 模型设计
          - Storage Architecture Principles（定义边界，不实现）
          → 产出：docs/w7-runtime-contract.md

Week 2:  Phase 1B（Single-Agent Runtime）
         - TaskExecution + ExecutionStep + AgentInvocation 实体 + Repository
         - PipelineOrchestrator（单 Agent 版）
         - HumanControlService（pause/resume/cancel/retry）
         - RecoveryService
         - 单元测试 + 集成测试
         → 产出：backend 代码 + 测试

Week 3:  Phase 1C（Multi-Agent + Agent Readiness）
          - [1C-1] Agent Readiness 子系统（横切基础设施，第一优先）
            * ReadinessState 七态机（DISCOVERED→BLOCKED）
            * ReadinessManager（扫描 + 状态机 + 恢复）
            * Dependency Graph（声明式依赖，每个 Plugin 自描述）
            * RecoveryStrategy（SAFE / DANGEROUS / IRREVERSIBLE）
            * Readiness 作为 Capability Router 前置过滤开关
            * RealE2E: provider 停止 → 自动恢复 → 调用成功
          - [1C-2] Multi-Agent Pipeline（review-loop）
          - [1C-3] HandoffContext（传递 objective + artifacts + findings + repo state）
          - [1C-4] Persistent Event Store（有序 event replay）
          - [1C-5] Mission Control — TaskDetail（窄切口）
          → 产出：完整 runtime + E2E + 状态投影

Week 4:  Phase 2（Mission Control 完整）
         - Vue 3 脚手架
         - TaskDetail.vue（唯一必须的页面）
         - WebSocket 状态投影
         - Event replay
         - 前后端联调
         → 产出：可用的 Mission Control

Week 5:  Phase 3（Recovery + Worktree + Storage 分层完善）
          - Recovery 完整实现（进程 kill + worktree cleanup）
          - Worktree 隔离机制
          - KnowledgeStore 开始实现（routing.md, lessons.md 写入文件系统）
          - ArtifactStore 分层（元数据 SQLite + 大文件 FS/Git）
          - S1-S7 软标准补齐
          - README + Demo 视频
          - GitHub Release v0.2

---

## 九、关键设计决策记录

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| 1 | Task vs Execution 是否分离 | **分离** | Retry/Reroute 需要独立 Execution |
| 2 | EventBus 是否是 Source of Truth | **否** | DB 是真理，EventBus 只是传播 |
| 3 | WebSocket 发事件还是发状态 | **发状态快照** | 前端不猜状态，避免漂移 |
| 4 | Pause 是即时还是延迟 | **延迟** | 等当前 tool 完成后才 pause |
| 5 | Evidence 的生命周期 | **四态** | CLAIMED→COLLECTED→VERIFIED→INVALIDATED |
| 6 | 多 Agent 共享还是隔离工作区 | **隔离（worktree）** | 避免竞态，支持并行 |
| 7 | 重启后进程死了怎么办 | **标记 FAILED，等用户 retry** | 不自动恢复，避免重复执行 |
| 8 | 重启后进程还活着怎么办 | **标记 RECOVERING，等用户决策** | 不假设，让用户决定 |
| 9 | Pipeline 用 YAML 还是代码定义 | **YAML（初期）** | 灵活，但 runtime contract 先定 |
| 10 | 前端先做完整 UI 还是窄切口 | **窄切口（TaskDetail）** | 先跑通一条链，再扩展 |
| 11 | SQLite 是否是唯一存储 | **否** | Runtime State → SQLite; Knowledge → Markdown/YAML; Large Artifacts → FS/Git |
| 12 | Agent Readiness 是 bug 修复还是架构能力 | **架构能力（一等公民）** | 不是 plugin bug，是 Runtime 对 Agent 可用性负责 |
| 13 | Recovery 策略如何声明 | **声明式（dependency graph）** | 不硬编码，每个 Plugin 自描述依赖和恢复方式 |
| 14 | Readiness 与 Capability 的关系 | **Readiness 是开关** | UNAVAILABLE → 不进候选集；DEGRADED → 降权；READY → 正常评分 |

---

**最后更新**：2026-08-16
**版本**：v1.1（新增 Storage Architecture + Agent Readiness）
**关联文档**：[core-model.md](../runtime/core-model.md)、[task-state-machine.md](../runtime/task-state-machine.md)、[event-protocol.md](../runtime/event-protocol.md)
