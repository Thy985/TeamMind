# Task State Machine + Policy Engine

> Agent 之间什么时候交接？谁接？什么条件下重试？什么时候停？
> 这不是 workflow，是 **事件驱动的状态机 + Policy 决策引擎**。

---

## 一、为什么不是 workflow diagram

传统 workflow 是：

```
User → Claude → Codex → Claude → Done
```

这种线性模型的问题：

```
❌ 无法处理失败
❌ 无法处理循环（Claude 让 Codex 修改后再次 review）
❌ 无法处理用户介入
❌ 无法处理并行（多个 Member 同时跑）
❌ 无法处理条件分支（测试通过 vs 失败走不同路径）
```

**TeamMind 用状态机 + Policy**：

```
状态定义明确 + 事件驱动转移 + Policy 决定下一个动作
```

---

## 二、Task 状态机

### 2.1 完整状态图

```
                    ┌─────────────┐
                    │   SUBMITTED  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                ┌──▶│  ORCHESTRAT- │◀──────────────────────────────┐
                │   │    ING      │                               │
                │   └──────┬──────┘                               │
                │          │                                       │
                │    ┌────▼────┐                                   │
                │    │  EXECUT- │                                │
                │    │   ING   │                                │
                │    └────┬────┘                                │
                │         │                                     │
                │    ┌────▼────┐                                │
                │    │ VERIFY- │                                │
                │    │  ING    │                                │
                │    └────┬────┘                                │
                │         │                                     │
                │    ┌────▼────┐                                │
                │    │  REVI   │ ◀──────────────────────────────┤
                │    │ EWING   │                                │
                │    └────┬────┘                                │
                │         │                                     │
                │    ┌────▼────┐                                │
                │    │  NEEDS- │                                │
                │    │ APPROVAL│                                │
                │    └────┬────┘                                │
                │         │                                     │
                │    ┌────▼────┐                                │
                │    │ AP-     │                                │
                │    │ PROVED  │                                │
                │    └────┬────┘                                │
                │         │                                     │
                │    ┌────▼────┐                                │
                │    │ DONE    │                                │
                │    └─────────┘                                │
                │                                               │
                │    ┌─────────────────────────────────────────┤
                │    │                                         │
                │    │      ┌──────────────┐                   │
                │    └─────▶│  FAILED       │◀──────────────────┘
                │           └──────────────┘
                │                 │
                │           ┌─────▼─────┐
                │           │ RETRYING  │
                │           └─────┬─────┘
                │                 │
                │           (retry count exhausted)
                │                 │
                │           ┌─────▼─────┐
                │           │  ABANDONED│
                │           └───────────┘
                │
                │    ┌──────────────────┐
                └───▶│  CANCELLED       │
                     └──────────────────┘
```

### 2.2 状态定义

```typescript
enum TaskState {
    SUBMITTED = 'SUBMITTED',        // 用户提交，尚未调度
    ORCHESTRATING = 'ORCHESTRATING', // 正在调度 Lead Agent
    EXECUTING = 'EXECUTING',        // 当前 Agent 正在执行
    VERIFYING = 'VERIFYING',        // Evidence 验证中
    REVIEWING = 'REVIEWING',        // Review Agent 在审查
    NEEDS_APPROVAL = 'NEEDS_APPROVAL', // 需要用户审批
    APPROVED = 'APPROVED',          // 用户/系统已批准
    DONE = 'DONE',                  // 完成
    FAILED = 'FAILED',              // 失败（可重试）
    RETRYING = 'RETRYING',          // 正在重试
    ABANDONED = 'ABANDONED',        // 重试耗尽，放弃
    CANCELLED = 'CANCELLED',        // 用户取消
}
```

### 2.3 状态转移表

| 当前状态 | 触发事件 | 下一状态 | Policy |
|---|---|---|---|
| SUBMITTED | `task.started` | ORCHESTRATING | 直接转移 |
| ORCHESTRATING | `routing.decided` | EXECUTING | 选择最佳 Agent |
| EXECUTING | `agent.chunk` | EXECUTING | 保持（流式输出） |
| EXECUTING | `handoff.requested` | REVIEWING | 根据 nextAction.role 判断 |
| EXECUTING | `finding.created` | REVIEWING | 有发现需审查 |
| EXECUTING | `test.failed` | VERIFYING | 进入验证阶段 |
| EXECUTING | `evidence.failed` | RETRYING | 验证失败，重试 |
| EXECUTING | `task.completed` | VERIFYING | Agent 完成，进入验证 |
| VERIFYING | `evidence.verified` | DONE | 验证通过 |
| VERIFYING | `evidence.failed` | RETRYING | 验证失败 |
| VERIFYING | `finding.created` | REVIEWING | 验证中发现新问题 |
| REVIEWING | `review.approved` | APPROVED | 审查通过 |
| REVIEWING | `review.rejected` | EXECUTING | 返回 Lead 修复 |
| REVIEWING | `finding.created` | NEEDS_APPROVAL | 发现高风险问题 |
| NEEDS_APPROVAL | `approval.granted` | APPROVED | 用户批准 |
| NEEDS_APPROVAL | `approval.denied` | CANCELLED | 用户拒绝 |
| NEEDS_APPROVAL | `approval.auto_approved` | APPROVED | 自动批准（Policy） |
| APPROVED | `task.completed` | DONE | 最终完成 |
| RETRYING | `task.completed` | VERIFYING | 重试成功 |
| RETRYING | `task.failed` (retry max) | ABANDONED | 重试耗尽 |
| 任何状态 | `task.cancelled` | CANCELLED | 用户取消 |

---

## 三、Policy Engine

### 3.1 核心职责

State Machine 决定"现在在哪"，Policy Engine 决定"下一步去哪"。

```
TaskState.REVIEWING + evidence.failed
        ↓
Policy Engine:
  - 检查 retry count < max_retries?
  - 检查当前 retry 策略?
  - 检查用户模式 (Autopilot / Supervised / Manual)?
        ↓
返回决策:
  - { action: 'RETRY', pluginId: 'codex', delayMs: 2000 }
  - { action: 'ESCALATE', toRole: 'LEAD', reason: 'Retry limit reached' }
  - { action: 'NEED_APPROVAL', question: 'Tests failing. Retry?' }
```

### 3.2 Policy 规则定义

**位置**：`com.teammind.policy.TaskPolicy`

```typescript
interface TaskPolicy {
    /** 判断在当前状态下遇到某个事件时的行为 */
    decide(TaskState currentState, EventType eventType, Context ctx): PolicyDecision;
    
    /** 检查是否允许此转移 */
    canTransition(TaskState from, TaskState to, Context ctx): boolean;
}

record PolicyDecision(
    String nextState,
    String action,           // 'PROCEED' | 'RETRY' | 'ESCALATE' | 'NEED_APPROVAL' | 'CANCEL'
    String pluginId,         // 如果指定了 Agent
    String reason,
    Map<String, Object> params
) {}

record Context(
    TaskState currentState,
    TaskExecution task,
    Project project,
    ProjectAgentProfile profile,
    UserPreferences preferences,
    int retryCount,
    long startTime
) {}
```

### 3.3 核心 Policy 规则

```typescript
class DefaultTaskPolicy implements TaskPolicy {
    
    @Override
    public PolicyDecision decide(TaskState state, EventType event, Context ctx) {
        return switch (state) {
            case EXECUTING -> handleExecuting(event, ctx);
            case VERIFYING -> handleVerifying(event, ctx);
            case REVIEWING -> handleReviewing(event, ctx);
            case NEEDS_APPROVAL -> handleApproval(event, ctx);
            case RETRYING -> handleRetrying(event, ctx);
            default -> PolicyDecision.proceed();
        };
    }
    
    private PolicyDecision handleVerifying(EventType event, Context ctx) {
        if (event == EventType.EVIDENCE_FAILED) {
            if (ctx.retryCount() < MAX_RETRIES) {
                return new PolicyDecision(
                    "RETRYING", "RETRY",
                    ctx.currentAgentId(),
                    "Evidence verification failed, retrying",
                    Map.of("delayMs", 2000)
                );
            } else {
                return new PolicyDecision(
                    "NEEDS_APPROVAL", "NEED_APPROVAL",
                    null,
                    "Evidence failed after " + MAX_RETRIES + " retries. User intervention needed.",
                    Map.of("question", "Evidence verification failed. Retry or cancel?")
                );
            }
        }
        return PolicyDecision.proceed();
    }
    
    private PolicyDecision handleReviewing(EventType event, Context ctx) {
        if (event == EventType.FINDING_CREATED) {
            Finding finding = extractFinding(event);
            if (finding.severity() == Finding.Severity.CRITICAL) {
                // 严重问题 → 需要用户介入
                return new PolicyDecision(
                    "NEEDS_APPROVAL", "NEED_APPROVAL",
                    null,
                    "Critical security finding detected",
                    Map.of("finding", finding)
                );
            } else {
                // 一般问题 → 自动返工
                return new PolicyDecision(
                    "EXECUTING", "RETRY",
                    ctx.originalAgentId(),  // 返回给原始 Agent
                    "Review found non-critical issues, returning to implementation",
                    Map.of("findings", List.of(finding))
                );
            }
        }
        return PolicyDecision.proceed();
    }
    
    private PolicyDecision handleRetrying(EventType event, Context ctx) {
        if (event == EventType.TASK_COMPLETED) {
            return new PolicyDecision("VERIFYING", "PROCEED", null, "Retry succeeded", Map.of());
        }
        if (event == EventType.TASK_FAILED && ctx.retryCount() >= MAX_RETRIES) {
            return new PolicyDecision("ABANDONED", "ABANDON", null, 
                "Max retries exceeded", Map.of());
        }
        return PolicyDecision.proceed();
    }
}

record PolicyDecision(
    String nextState,
    String action,
    String pluginId,
    String reason,
    Map<String, Object> params
) {
    static PolicyDecision proceed() {
        return new PolicyDecision(null, "PROCEED", null, "", Map.of());
    }
}
```

### 3.4 控制模式影响 Policy

同一 Policy 在不同模式下行为不同：

```typescript
enum ControlMode { AUTOMATED, SUPERVISED, MANUAL }

// AUTOMATED：所有决策自动，无需用户介入
// SUPERVISED：关键决策（CRITICAL finding）需用户批准
// MANUAL：每一步都需要用户确认

class PolicyWithMode {
    PolicyDecision decide(TaskState state, EventType event, Context ctx, ControlMode mode) {
        PolicyDecision decision = policy.decide(state, event, ctx);
        
        // 根据模式调整
        if (mode == ControlMode.MANUAL && decision.action == "PROCEED") {
            return new PolicyDecision(
                decision.nextState,
                "NEED_APPROVAL",  // 即使自动决定也需要用户确认
                decision.pluginId,
                "Manual mode: user confirmation required",
                decision.params()
            );
        }
        if (mode == ControlMode.SUPERVISED 
            && decision.params.containsKey("finding")
            && ((Finding)decision.params.get("finding")).severity() == CRITICAL) {
            return new PolicyDecision(
                decision.nextState,
                "NEED_APPROVAL",
                null,
                "Critical finding in supervised mode requires approval",
                decision.params()
            );
        }
        return decision;
    }
}
```

---

## 四、典型任务流程示例

### 4.1 正常流程：重构 auth 为 JWT

```
[用户] 提交任务："把 auth 从 session 改成 JWT"
    ↓
TaskState: SUBMITTED
Event: task.started
Policy: direct → ORCHESTRATING
    ↓
TaskState: ORCHESTRATING
Event: routing.decided (capability=implementation, plugin=codex)
Policy: direct → EXECUTING
    ↓
TaskState: EXECUTING
Events: agent.chunk × N, tool.called × M, file.changed × 7
    ↓
Event: task.completed (Codex says done)
Policy: → VERIFYING
    ↓
TaskState: VERIFYING
Event: evidence.verified (git diff: 7/7 files present)
    ↓
Event: test.result (42 passed, 0 failed)
    ↓
Event: evidence.verified (tests passed)
Policy: → DONE
    ↓
TaskState: DONE
```

### 4.2 有审查的流程

```
... (同上)
    ↓
Event: task.completed (Codex says done)
Policy: → VERIFYING
    ↓
Event: routing.decided (capability=code_review, plugin=claude-code)
Policy: → REVIEWING
    ↓
TaskState: REVIEWING
Events: agent.chunk, tool.called (Read: src/auth/jwt.ts)
    ↓
Event: finding.created (severity=HIGH, "Session cookie still referenced in jwt.ts:45")
Policy: finding severity HIGH → return to LEAD (Codex)
    ↓
Event: handoff.requested (from=claude-code, to=codex, reason="Fix HIGH finding")
Policy: → EXECUTING
    ↓
TaskState: EXECUTING
Events: agent.chunk, file.changed (jwt.ts line 45)
    ↓
Event: task.completed
Policy: → VERIFYING
    ↓
Event: evidence.verified
    ↓
Event: routing.decided (capability=code_review, plugin=claude-code)
Policy: → REVIEWING
    ↓
Event: review.approved
Policy: → APPROVED → DONE
```

### 4.3 测试失败的回环

```
...
    ↓
Event: task.completed
Policy: → VERIFYING
    ↓
Event: test.result (39 passed, 3 failed)
Policy: → RETRYING (retryCount=0)
    ↓
TaskState: RETRYING
Event: handoff.requested (to=codex, reason="Fix failing tests")
Policy: → EXECUTING
    ↓
TaskState: EXECUTING
Events: file.changed × 2, agent.chunk
    ↓
Event: task.completed
Policy: → VERIFYING
    ↓
Event: test.result (42 passed, 0 failed)
Event: evidence.verified
Policy: → DONE
```

### 4.4 用户介入流程

```
...
    ↓
Event: finding.created (severity=CRITICAL, "JWT secret should not be hardcoded")
Policy: CRITICAL → NEEDS_APPROVAL
    ↓
TaskState: NEEDS_APPROVAL
WebSocket 推送: approval.required
    ↓
[用户点击 Approve]
WebSocket 接收: approval.granted (approvalId=app-xxx)
Policy: → APPROVED
    ↓
Event: task.completed → DONE
```

---

## 五、并发与并行

### 5.1 简单串行

```
Lead → Member1 → Lead → Member2 → Lead → Done
```

### 5.2 Fan-out 并行（未来）

```
Lead 收到 "分析三个模块"
    ↓
Fan-out: 同时启动 3 个 Member
    ↓
Member1 (Codex) → result1
Member2 (Claude) → result2
Member3 (Aider) → result3
    ↓
Lead 聚合三个结果 → 输出
```

```typescript
// 并行的状态机扩展
enum ConcurrencyMode {
    SEQUENTIAL,   // 串行
    PARALLEL_FANOUT,  // 扇出并行
    PIPELINE     // 流水线
}

// TaskStep 可以有多条 concurrent
record TaskExecution(
    String id,
    String projectId,
    List<TaskStep> steps,           // 串行步骤
    List<ParallelGroup> parallelGroups,  // 并行组
    ConcurrencyMode concurrencyMode
) {}

record ParallelGroup(
    String groupId,
    List<String> stepIds,           // 并发执行的步骤
    MergeStrategy mergeStrategy,     // 如何合并结果
    String waitForAll,               // 是否等全部完成
    long timeoutMs
) {}
```

---

## 六、超时与降级

### 6.1 超时处理

```typescript
interface TimeoutPolicy {
    long leadTimeoutMs();        // Lead 总超时（默认 10 分钟）
    long stepTimeoutMs();         // 单步骤超时（默认 3 分钟）
    long evidenceTimeoutMs();     // 验证超时（默认 30 秒）
    
    OnTimeout onTimeout(TaskState state, EventType event);
}

enum OnTimeout {
    RETRY,       // 重试
    Fallback_TO_PLUGIN,  // 切换 Plugin
    NEED_APPROVAL,  // 请求用户
    CANCEL       // 取消
}
```

### 6.2 降级策略

```typescript
class FallbackPolicy {
    /** 当主 Agent 失败时，尝试备选 */
    Optional<Plugin> findFallback(String failedPluginId, String capability) {
        return capabilityRegistry
            .findByCapabilityAndQuality(capability, CapabilityQuality.FAIR)
            .stream()
            .filter(p -> !p.metadata().id().equals(failedPluginId))
            .findFirst();
    }
    
    /** 当两个都行不通时，请求用户决策 */
    PolicyDecision askUser(String context, List<String> options) {
        return new PolicyDecision(
            "NEEDS_APPROVAL",
            "NEED_APPROVAL",
            null,
            "No capable Agent found, user decision needed",
            Map.of("context", context, "options", options)
        );
    }
}
```

---

## 七、Event Bus 集成

```typescript
class TaskStateMachine {
    private final EventBus eventBus;
    private final TaskPolicy policy;
    private final TaskExecutionRepository executionRepo;
    
    public void handleEvent(RuntimeEvent event) {
        TaskExecution task = loadTask(event.taskId());
        TaskState currentState = task.getState();
        
        // 1. 执行 Policy 决策
        PolicyDecision decision = policy.decide(
            currentState, 
            event.type(), 
            buildContext(task, event)
        );
        
        // 2. 状态转移
        if (decision.nextState() != null) {
            task.setState(TaskState.valueOf(decision.nextState()));
            task.setLastEvent(event);
            executionRepo.save(task);
            
            // 3. 发出状态转移事件
            eventBus.emit(new RuntimeEvent("task.state_changed", Map.of(
                "taskId", event.taskId(),
                "from", currentState.name(),
                "to", decision.nextState(),
                "reason", decision.reason()
            )));
            
            // 4. 如果需要新的 Agent，调度
            if ("EXECUTING".equals(decision.nextState()) && decision.pluginId() != null) {
                scheduleNextAgent(task, decision.pluginId(), decision.params());
            }
        }
        
        // 5. 如果有审批需求，广播
        if (decision.action().equals("NEED_APPROVAL")) {
            eventBus.emit(new RuntimeEvent("approval.required", Map.of(
                "taskId", event.taskId(),
                "question", decision.params().get("question"),
                "context", decision.params().get("context")
            )));
        }
    }
}
```

---

## 八、验收清单

- [ ] 状态机定义完整（所有状态和转移）
- [ ] Policy Engine 覆盖所有关键事件
- [ ] 三种控制模式正确影响 Policy
- [ ] 超时和降级策略生效
- [ ] 并发状态（FANOUT / PIPELINE）可用
- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 端到端流程能通过（用 mock Plugin）

---

## 九、踩坑记录

> 实现过程中遇到的问题。

---

## 十、接下来

- 读 [web-ui-architecture.md](web-ui-architecture.md)，了解前端如何消费这些事件
- 或读 [control-modes.md](control-modes.md)，了解三级控制模式的具体实现

---

**版本**：v0.1 Draft
**最后更新**：2026-08-14