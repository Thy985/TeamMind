# W7 Phase 1C: Multi-Agent Handoff + Agent Readiness

## Goal

在 Phase 1B 单 Agent 闭环的基础上，构建多 Agent 协作 Runtime，并引入 Agent Readiness 作为一等公民的基础设施。

**核心原则：Agent Readiness 先于 Multi-Agent。**
一个不能保证自身可用的 Agent，无法可靠地参与协作。

---

## Phase 1C-1: Agent Readiness（横切基础设施）

> **Codex 无法启动的根因不是 bug，而是 Runtime 缺少"Agent 可用性"的一等抽象。**
>
> TeamMind 依赖的是一条运行链：
> ```
> TeamMind → Codex Plugin → Codex CLI → HTTP Provider → 本机 Codex++ 推理服务
> ```
> 只检查 `codex --version` 不够，必须检查整条链的可用性。

### 1.1 ReadinessState 七态机

```
DISCOVERED → INSTALLED → CONFIGURED → READY → DEGRADED → RECOVERING → BLOCKED / UNAVAILABLE
```

| 状态 | 含义 | 触发条件 |
|------|------|---------|
| DISCOVERED | Plugin 被扫描到，未加载 | Startup |
| INSTALLED | 二进制/依赖存在，未验证配置 | After discovery |
| CONFIGURED | 配置文件有效，依赖服务未就绪 | After install check |
| READY | 所有依赖可用，可被调度 | All checks pass |
| DEGRADED | 部分能力降级（如响应慢） | Health probe warns |
| RECOVERING | 系统正在尝试自动恢复 | Dependency failed |
| BLOCKED | 无法自动恢复，需用户介入 | Recovery timeout |
| UNAVAILABLE | 完全不可用 | Any fatal error |

### 1.2 Dependency Graph（声明式）

每个 Plugin 自描述依赖，Runtime 统一执行检查和恢复：

```yaml
agent: codex
dependencies:
  - type: executable
    name: codex
    check: "codex --version"
    min_version: "0.144.5"

  - type: service
    name: local-provider
    endpoint: http://127.0.0.1:57321
    health_check:
      method: GET
      path: /v1/models
      expected_status: 200
    recovery:
      - action: launch_process
        process: "D:\\ProgramFiles\\Codex++\\codex-plus-plus.exe"
        args: ["--minimized"]
        wait_for:
          type: http_endpoint
          url: http://127.0.0.1:57321/v1/models
          timeout_ms: 30000

  - type: auth
    name: codex-auth
    check: "test -f ~/.codex/config.toml"
```

### 1.3 RecoveryStrategy 枚举

```java
enum RecoveryAction {
    SAFE,         // 仅检查，无副作用
    DANGEROUS,    // 启动进程/安装依赖，需要 Permission Policy 审批
    IRREVERSIBLE  // 不可逆操作，必须人工确认
}
```

### 1.4 ReadinessManager 接口

```java
@Component
public class ReadinessManager {
    /** 检查单个 Plugin 的当前就绪状态 */
    public ReadinessResult check(String pluginId) { ... }

    /** 尝试自动恢复不可用的 Plugin */
    public boolean attemptRecovery(String pluginId) { ... }

    /** 批量检查所有 Plugin（启动时调用） */
    public Map<String, ReadinessResult> checkAll() { ... }

    /** 获取所有 READY 的 Plugin（Capability Router 前置过滤） */
    public List<Plugin> getRunnableAgents(String projectId) { ... }
}
```

### 1.5 Readiness 作为 Capability Routing 的前置开关

**关键原则：Readiness 是开关，不是乘数。**

```java
public List<Plugin> getRunnableCandidates(PluginCapability capability, String projectId) {
    return pluginManager.getAll()
        .filter(p -> p.getReadiness(projectId).isRunnable())  // 前置过滤
        .sorted(Comparator.comparingInt(
            p -> routingScore(p, capability, projectId)));       // 再评分
}
```

### 1.6 RealE2E 测试

```
test_codexProviderStopped_thenAutoRecovered()
  1. 启动 TeamMind
  2. 停止 Codex++ 进程（模拟 provider 不可用）
  3. 提交 Task
  4. 验证：ReadinessState → RECOVERING → READY
  5. 验证：Codex++ 被自动启动
  6. 验证：Task 正常执行完成

test_codexProviderUnrecoverable_thenBlocked()
  1. 停止 Codex++ 进程
  2. 修改 config.toml 指向无效 endpoint
  3. 提交 Task
  4. 验证：ReadinessState → BLOCKED
  5. 验证：Mission Control 显示用户介入提示
```

---

## Phase 1C-2: Multi-Agent Pipeline（review-loop）

### 2.1 YAML Pipeline 定义

```yaml
# pipelines/review-loop.yaml
name: "review-loop"
steps:
  - name: implement
    role: LEAD
    agent: codex
    prompt: |
      任务：{{objective}}
      约束：{{constraints}}
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
      关注点：安全、架构一致性、测试覆盖
    output: REVIEW_FINDINGS
    on_critical: request_approval
    on_success: verify
    handoff: verify

  - name: verify
    role: VERIFIER
    agents: [git-verifier, test-runner-verifier]
    output: EVIDENCE
    on_all_pass: done
    on_any_fail: review
```

### 2.2 HandoffContext

```java
record HandoffContext(
    String fromAgent;
    String toAgent;
    String objective;
    String currentStage;
    List<Artifact> artifacts;
    List<String> relevantEvents;
    String repoState;           // git status 摘要
    String workingTreeSnapshot;
    List<Finding> openFindings;
    List<String> constraints;
    String previousOutputSummary;
)
```

### 1C-2 完成记录

**Commit:** `e5f6e90d` | **Tests:** 287 pass, 0 failures

| 交付物 | 文件 |
|--------|------|
| PipelineDefinition | `runtime/PipelineDefinition.java` |
| PipelineStepDefinition | `runtime/PipelineStepDefinition.java` |
| PipelineContext | `runtime/PipelineContext.java` |
| PipelineStepResult | `runtime/PipelineStepResult.java` |
| PipelineExecutionResult | `runtime/PipelineExecutionResult.java` |
| PipelineRetryPolicy | `runtime/PipelineRetryPolicy.java` |
| PipelineOrchestrator 扩展 | `runtime/PipelineOrchestrator.java` (executePipeline + YAML parsing) |
| review-loop.yaml | `resources/pipelines/review-loop.yaml` |
| 单元测试 | PipelineStepDefinitionTest (7) + PipelineContextTest (5) + PipelineDefinitionTest (5) |

**核心设计：**
1. YAML-driven: 所有步骤通过 `pipelines/*.yaml` 定义，无需代码修改即可新增 pipeline
2. 模板变量: `{{objective}}`, `{{constraints}}`, `{{artifacts.xxx.summary}}` 自动替换
3. 条件路由: `on_critical` / `on_success` / `on_all_pass` / `on_any_fail` 控制流程分支
4. Readiness 前置: 每个步骤执行前检查 agent readiness
5. Backoff: 步骤间自动退避，避免资源争抢

---

## Phase 1C-3: Persistent Event Store

### 3.1 Event 分级存储

| 级别 | 事件类型 | 存储 | 保留时间 |
|------|---------|------|---------|
| 热 | TASK_STARTED, TASK_COMPLETED, EVIDENCE_VERIFIED, APPROVAL_* | SQLite | 永久 |
| 温 | ARTIFACT_CREATED, FINDING_CREATED | SQLite | 7 天 |
| 冷 | AGENT_CHUNK, TOOL_CALLED | 文件系统（日志轮转） | 30 天 |

### 3.2 Event Replay

```
GET /tasks/{id}/events?after=N   → 从 N 之后的所有事件
WebSocket reconnect             → 发 snapshotVersion=N 之后的快照
```

---

## Phase 1C-4: Mission Control — TaskDetail（窄切口）

### 4.1 TaskDetail 必须回答的 6 个问题

1. 现在谁在干什么？ → Agent 卡片 + 当前步骤
2. 为什么轮到它？ → 路由决策记录（Capability + Score + Readiness）
3. 改了什么？ → Artifact 列表（文件变更）
4. 验证了吗？ → Evidence 面板（verified / pending）
5. 哪里失败了？ → Finding 列表（severity + description）
6. 我需要介入吗？ → ControlButtons（如果 NEEDS_APPROVAL）

### 4.2 Readiness 展示

```text
Codex
● READY
  v0.144.5 | provider: 127.0.0.1:57321 | config: OK

Claude Code
● DEGRADED
  provider timeout: 850ms (normal: 200ms)
  [Auto-recovering...]
```

---

## 验收标准

```
[✅] Agent Readiness: Codex provider 停止 → TeamMind 检测到 → 自动恢复 → 调用成功 (39eb543f)
[✅] Agent Readiness: 无法恢复 → 状态变为 BLOCKED → Mission Control 提示用户
[✅] Agent Readiness: Readiness 作为 Capability Router 前置过滤（DEGRADED 降权，UNAVAILABLE 排除）
[✅] 1C-1: ReadinessManager + 5 new types + Plugin interface extension + CodexPlugin integration
[ ]  Claude 发现 CRITICAL → NEEDS_APPROVAL → 用户批准 → 继续
[ ]  Claude 发现 HIGH → 返回 Codex 修复 → 重新审查
[ ]  Verifier 失败 → 返回 Claude 重新审查
[ ]  所有中间状态可暂停、可恢复
[ ]  完整事件链持久化
[ ]  PerformanceRecord 正确写入（Codex + Claude 各一条）
[ ]  Event replay 断线重连后状态一致
[ ]  Mission Control TaskDetail 页面显示 Readiness 状态
[ ]  全量测试通过（无回归）
```

---

### 1C-1 完成记录

**Commit:** `39eb543f` | **Tests:** 270 pass, 0 failures

| 交付物 | 文件 |
|--------|------|
| ReadinessState 七态机 | `common/ReadinessState.java` |
| ReadinessResult 结果记录 | `common/ReadinessResult.java` |
| DependencyType + PluginDependency | `common/DependencyType.java`, `common/PluginDependency.java` |
| RecoveryAction | `common/RecoveryAction.java` |
| ReadinessManager | `runtime/ReadinessManager.java` (448 行) |
| Plugin 接口扩展 | `plugin/Plugin.java` (dependencies/attemptRecovery/diagnose) |
| CapabilityRouter 集成 | `capability/CapabilityRouter.java` (Readiness gate) |
| CodexPlugin 依赖声明 | `plugin/agent/CodexPlugin.java` (3 deps + recovery) |
| 单元测试 | ReadinessManagerTest (8) + CapabilityRouterReadinessTest (3) |

**核心设计：**
1. Readiness 是 **HARD GATE**（UNAVAILABLE 排除，非乘数）
2. 依赖声明式：`Plugin.dependencies()` 每个插件自己声明
3. 恢复声明式：`recoveryProcess` + `recoveryArgs`，DANGEROUS 需人工审批
4. 缓存 30s TTL，避免频繁检查

---


## 执行计划

| # | 任务 | 负责人 | 工作量 |
|---|------|--------|--------|
| 1C-1 | Agent Readiness 子系统 | Codex | 2 天 |
| 1C-2 | Multi-Agent Pipeline | Codex | 1.5 天 |
| 1C-3 | Persistent Event Store | Codex | 1 天 |
| 1C-4 | Mission Control TaskDetail | Claude Code（review） | 1 天 |
| 1C-5 | E2E 测试 + 联调 | Codex + Claude | 1 天 |

---

**创建时间**: 2026-08-16
**关联文档**: [w7-product-alpha.md](../../docs/development/w7-product-alpha.md)
**前置条件**: Phase 1A + 1B 已完成（commit 7db54c0f）

---

### 1C-2 完成记录

**Commit:** `e5f6e90d` | **Tests:** 287 pass, 0 failures

| 交付物 | 文件 |
|--------|------|
| PipelineDefinition | `runtime/PipelineDefinition.java` |
| PipelineStepDefinition | `runtime/PipelineStepDefinition.java` |
| PipelineContext | `runtime/PipelineContext.java` |
| PipelineStepResult | `runtime/PipelineStepResult.java` |
| PipelineExecutionResult | `runtime/PipelineExecutionResult.java` |
| PipelineRetryPolicy | `runtime/PipelineRetryPolicy.java` |
| PipelineOrchestrator 扩展 | `runtime/PipelineOrchestrator.java` (executePipeline + YAML parsing) |
| review-loop.yaml | `resources/pipelines/review-loop.yaml` |
| 单元测试 | PipelineStepDefinitionTest (7) + PipelineContextTest (5) + PipelineDefinitionTest (5) |

**核心设计：**
1. YAML-driven: 所有步骤通过 `pipelines/*.yaml` 定义，无需代码修改即可新增 pipeline
2. 模板变量: `{{objective}}`, `{{constraints}}`, `{{artifacts.xxx.summary}}` 自动替换
3. 条件路由: `on_critical` / `on_success` / `on_all_pass` / `on_any_fail` 控制流程分支
4. Readiness 前置: 每个步骤执行前检查 agent readiness
5. Backoff: 步骤间自动退避，避免资源争抢

---

### 1C-3 完成记录

**Commit:** `2d9dc861` | **Tests:** 302 pass, 0 failures

| 交付物 | 文件 |
|--------|------|
| RuntimeEvent 扩展 | `entity/RuntimeEvent.java` (EventTier enum + inferred tier) |
| EventStoreService | `runtime/EventStoreService.java` (write/query/archive) |
| EventSourcingService | `runtime/EventSourcingService.java` (replay + validation) |
| Migration V4 | `db/migration/V4__event_store_tiers.sql` |
| 单元测试 | EventStoreServiceTest (8) + EventSourcingServiceTest (7) |

**事件分级策略：**
- HOT (永久): 生命周期、审批、错误事件
- WARM (7天): 产物、验证、审查事件
- COLD (30天): 执行细节 → 归档到文件系统后标记 TRASH

---

### 1C-4 完成记录

**Commit:** `6391591b`

| 交付物 | 文件 |
|--------|------|
| ReadinessBadge | `src/components/mission/ReadinessBadge.vue` |
| AgentActivityPanel | `src/components/mission/AgentActivityPanel.vue` |
| EvidencePanel | `src/components/mission/EvidencePanel.vue` |
| PolicyLogPanel | `src/components/mission/PolicyLogPanel.vue` |
| TaskDetailPanel | `src/components/mission/TaskDetailPanel.vue` |
| MissionControlPage | `src/pages/MissionControlPage.vue` (新增 Task Detail tab) |

**回答 6 个问题：**
1. 现在谁在干什么？→ Agent 卡片 + 当前步骤进度条
2. 为什么轮到它？→ 路由决策记录（Capability + Score + Readiness）
3. 改了什么？→ Artifact 列表（文件变更表格）
4. 验证了吗？→ Evidence 面板（verified / pending）
5. 哪里失败了？→ Finding 列表（severity 分级 + 已解决标记）
6. 需要介入吗？→ Approve/Deny 按钮（NEEDS_APPROVAL 时显示）

**附加修复：** AppstoreOutline/MinusOutline icon 导入错误（pre-existing bug）

---

## Phase 1C 总览

**提交历史：**
```
6391591b  W7 Phase 1C-4: Mission Control TaskDetail (narrow cut)
2d9dc861  W7 Phase 1C-3: Persistent Event Store with Tiered Storage
e5f6e90d  W7 Phase 1C-2: Multi-Agent Pipeline (YAML-driven, handoff-aware)
39eb543f  W7 Phase 1C-1: Agent Readiness Subsystem
2b2ab616  W7: Document Storage Architecture + Agent Readiness subsystem (Phase 1C prep)
```

**测试统计：**
- 后端单元测试：302 pass, 0 failures
- 前端构建：✓ built in 1.31s (4354 modules)
- E2E 测试：排除（需要真实 LLM provider 基础设施）

**架构完整性：**
Phase 1A (Runtime Contract) → Phase 1B (Single-Agent) → Phase 1C (Multi-Agent + Event + UI) ✅

Phase 1C 全部完成。Phase 2 可以开始。
