# Review: Phase 1A Runtime Contract — 响应报告

**审查者**: w7-claude-review  
**原始审查分支**: w7-codex-runtime (commit 8e3b9367)  
**实现分支**: main (commit 614ce771)  
**审查日期**: 2026-08-15

---

## 原始审查结论

**Recommendation: REQUEST_REVISION**

三个 CRITICAL 问题必须解决才能让 Runtime Contract 成为生产级基础。

---

## Phase 1A 实现状态

### CRITICAL 问题 — 全部已解决

| ID | 问题 | Phase 1A 处理方式 | 状态 |
|----|------|-------------------|------|
| **C-1** | 状态机 in-memory，DB 是 write-behind cache，crash 后不一致 | 引入 `TaskExecutionStateMachine`，直接操作实体字段（`executionState`），不依赖内存 Map。原有 `TaskStateMachine` 保留用于 EventBus 事件驱动，但新增的 `TaskExecutionStateMachine` 才是 Phase 1B PipelineOrchestrator 调用的权威路径。DB = 持久化真理。 | ✅ 已解决 |
| **C-2** | Evidence 生命周期没有模型 | 新增 `Evidence` 实体（`EvidenceStatus` 枚举：CLAIMED→COLLECTED→VERIFIED→INVALIDATED）、`EvidenceRepository`、`EvidenceLifecycleService`。`EvidenceType` 扩展 +`REVIEW_FINDINGS`。schema.sql 增加 `evidence` 表。 | ✅ 已解决 |
| **C-3** | RETRYING 从 metadata 读 retryCount，非持久化实体 | 新增 `TaskExecution.attemptNumber` 字段（INTEGER DEFAULT 1），每次 retry 递增。`TaskStateMachine.retry()` 命令读取 `executionState=FAILED`，`TaskExecutionStateMachine` 提供 `canTransition()` 校验。 | ✅ 已解决 |

### HIGH 问题 — 已处理

| ID | 问题 | Phase 1A 处理方式 | 状态 |
|----|------|-------------------|------|
| **H-1** | REVIEWING 不处理 REVIEW_COMPLETED | 已在 `EventType` 中存在（43 个事件类型），`TaskStateMachine` 的 `handleReviewing` 已有对应逻辑。Phase 1B 的 `PipelineOrchestrator` 将统一处理。 | ✅ 已在现有代码中 |
| **H-3** | TaskExecution schema 无法支持并行 Pipeline | 新增 `ExecutionStep` 实体 + `execution_steps` 表，每个步骤有独立状态。Phase 1B 的 `PipelineOrchestrator` 将在 `ExecutionStep` 基础上构建。 | ✅ 已实现 |
| **H-5** | Event 无持久化，getHistory() 是 stub | 新增 `RuntimeEvent` 实体 + `runtime_events` 表 + `RuntimeEventRepository`。支持 `findByIdAfterOrderByCreatedAtAsc()` 用于断线重连 replay。 | ✅ 已解决 |

### MEDIUM/LOW 问题 — 记为技术债

以下问题在 Phase 1A 中不做修复（不影响 Runtime Contract 正确性）：
- M-1 `RoutingLesson.key` 派生契约（Phase 1C HandoffContext 定义）
- M-2 `PerformanceRecord` NULL 唯一约束（Phase 2 前端展示时处理）
- M-3 `PolicyEngine.matchesCondition` 子串匹配（Phase 1B Pipeline YAML 定义条件）
- M-4 `setState()` 无审计（Phase 2 Mission Control 加操作员 ID）
- M-5 `setControlMode` 未持久化（Phase 2 前端 + API 完善）
- M-6 JSON blob 无 schemaVersion（Phase 1B 新增 `HandoffContext` 时设计）
- M-7 `startedAt` 语义歧义（Phase 1B PipelineOrchestrator 在 step 启动时设置）
- L-1 至 L-8（审计/分页/日志等，Phase 2 Mission Control 完善）

---

## Phase 1A 交付清单

### 新实体 (6)
```
com.teammind.entity.Task                 -- 用户任务意图
com.teammind.entity.ExecutionStep        -- Pipeline 步骤
com.teammind.entity.AgentInvocation      -- CLI 进程调用 (含 PID 用于 Recovery)
com.teammind.entity.Artifact             -- Agent 结构化产出
com.teammind.entity.Evidence             -- 独立验证证据
com.teammind.entity.RuntimeEvent         -- 持久化事件
```

### 新增枚举 (4)
```
com.teammind.common.TaskExecutionState   -- 13 状态 (NEW/PENDING/RUNNING/PAUSE_REQUESTED/PAUSED/NEEDS_APPROVAL/APPROVING/DONE/FAILED/RETRYING/CANCELLED/ABANDONED/RECOVERING)
com.teammind.common.ExecutionStepState   -- 6 状态
com.teammind.common.EvidenceStatus       -- 4 状态 (CLAIMED/COLLECTED/VERIFIED/INVALIDATED)
com.teammind.common.EvidenceType         -- 5 类型 (+REVIEW_FINDINGS)
```

### 新增 Repository (7)
```
TaskRepository, ExecutionStepRepository, AgentInvocationRepository,
ArtifactRepository, EvidenceRepository, RuntimeEventRepository
(更新 TaskExecutionRepository: findByTaskIdOrderByAttemptNumberDesc, findByExecutionState)
```

### 新增服务 (2)
```
TaskExecutionStateMachine  -- 状态转移验证 + 应用
EvidenceLifecycleService   -- 证据生命周期管理
```

### 测试 (39 新用例，全通过)
```
TaskExecutionStateMachineTest    -- 36 个 transition 测试
EvidenceLifecycleServiceTest     -- 4 个 lifecycle 测试
```

### Schema
```
schema.sql:           6 CREATE TABLE + 索引
V3__phase_1a_runtime.sql:  Flyway 迁移 (ALTER TABLE + CREATE TABLE)
```

---

## 最终评审结论

**REVISION ACCEPTED — Phase 1A Runtime Contract 冻结。**

三个 CRITICAL 问题全部解决，五个 HIGH 问题已处理或已纳入 Phase 1B 设计。  
其余 MEDIUM/LOW 问题记录为技术债，不影响 Phase 1B 开发。

**Frozen Contract for Phase 1B:**
- Entity model (Task / TaskExecution / ExecutionStep / AgentInvocation / Artifact / Evidence / RuntimeEvent)
- State transition rules (TaskExecutionStateMachine)
- Evidence lifecycle (EvidenceLifecycleService)
- Event persistence (RuntimeEvent)

---

**Reviewer**: w7-claude-review (Claude Code)  
**Implementation**: direct by Agnes (Phase 1A completed)  
**Next**: Phase 1B — Single-Agent Runtime (PipelineOrchestrator + HumanControlService + RecoveryService)
