# 三级控制模式：Autopilot / Supervised / Manual

> 用户不只是"看"，而是可以根据信任程度和控制欲，在三个层级之间切换。
> 系统不是完全自主，也不是完全手动，而是**按需介入**。

---

## 一、设计哲学

### 1.1 核心原则

```
不是"要么全自动，要么全手动"。
而是"根据任务风险、项目历史信任度、用户状态，自动选择最合适的控制级别"。
```

### 1.2 三种模式的本质区别

| 模式 | 核心理念 | 用户角色 | 适用场景 |
|---|---|---|---|
| **Autopilot** | 信任系统 | Observer | 熟悉的项目、低风险任务、时间紧 |
| **Supervised** | 信任系统 + 安全网 | Gatekeeper | 新项目、敏感操作、关键决策 |
| **Manual** | 自己主导 | Director | 学习阶段、极端敏感操作、调试 |

---

## 二、Autopilot 模式

### 2.1 行为定义

```
Agent 完全自主调度，只在以下情况停下来：

1. 高风险操作（生产配置修改、数据库迁移）
2. 所有 Agent 都失败且无 fallback
3. 证据验证全部失败
4. 超时未完成任务
```

### 2.2 自动审批规则

```yaml
# autopilot-rules.yaml
auto_approval:
  # 自动批准的规则
  rules:
    - id: code-modification-small
      condition: "file_size < 500 lines AND not in production directory"
      action: APPROVE
      
    - id: test-generation
      condition: "artifact_type == 'TEST_REPORT' AND no_failures"
      action: APPROVE
      
    - id: documentation-update
      condition: "artifact_type == 'DOCUMENTATION'"
      action: APPROVE
      
  # 必须人工审批的规则
  manual_required:
    - id: production-config-change
      condition: "file_path matches '*config*production*'"
      
    - id: database-migration
      condition: "artifact_type == 'DATABASE_MIGRATION'"
      
    - id: security-critical-finding
      condition: "finding.severity == 'CRITICAL' AND artifact_type == 'CODE_DIFF'"
      
    - id: evidence-always-fails
      condition: "evidence.failed AND retry_count >= 2"
```

### 2.3 用户界面

```
┌─────────────────────────────────────────────────────────┐
│  🤖 AUTOMPILOT 运行中                                   │
│  系统自动调度，仅高风险操作需确认                        │
│  [切换模式] [查看日志] [强制停止]                        │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Codex (LEAD)  ⟳ 正在实现 JWT 认证                       │
│  Claude (REVIEW) ⏳ 等待 Code Review                      │
│  Aider (TEST)   ⏳ 等待测试生成                          │
│                                                          │
│  最近事件:                                                 │
│  08:24 Codex modified auth/jwt.ts (+47 lines)            │
│  08:25 Tests passed: 31/31 ✅                           │
│  08:25 Routing → Claude for security review             │
│                                                          │
│  自动审批: 12 次   人工介入: 0 次   已跳过: 3 次          │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 三、Supervised 模式

### 3.1 行为定义

```
Agent 自主调度，但在以下节点暂停等待用户确认：

1. 每个 Agent 开始执行前（简要预览）
2. 每个 Agent 完成后（查看结果）
3. 关键决策点（如：切换到哪个 Agent）
4. 任何 Agent 报告失败时
5. 需要跨 Agent 交接时（如果有新 Agent 介入）
```

### 3.2 审批点密度

```
Autopilot:   [启动]────[执行]────[执行]────[完成]
              几乎不停

Supervised:  [启动]→[执行]→[确认]→[执行]→[确认]→[完成]
              关键节点暂停

Manual:      [启动]→[执行]→[确认]→[执行]→[确认]→[确认]→[完成]
              每个动作确认
```

### 3.3 界面设计

```
┌─────────────────────────────────────────────────────────┐
│  👁 SUPERVISED 模式运行中                               │
│  关键节点需确认 — 下一步将路由到 Claude Code (REVIEW)    │
│  [切换模式] [查看更多] [强制停止]                        │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Codex (LEAD) ✅ 已完成                                   │
│    修改了 7 个文件，运行测试 31/31 通过                    │
│                                                          │
│  ▶ Claude Code (REVIEW) — 等待确认开始审查               │
│    将审查 src/auth/jwt.ts 的安全性                       │
│                                                          │
│  ⏳ Aider (TEST) — 等待 Claude 审查结果                  │
│                                                          │
├─────────────────────────────────────────────────────────┤
│  下一步操作:                                             │
│  [▶ 开始 Claude 审查]  [⏭ 跳过审查]  [✋ 取消任务]       │
└─────────────────────────────────────────────────────────┘
```

### 3.4 快速审批面板

```typescript
// 用户可以一键审批多个待审批项
interface BatchApproval {
  pendingApprovals: ApprovalRequest[];
  canBatchApprove: boolean;
  
  async batchApprove(ids: string[]): Promise<void> { ... };
  async batchDeny(ids: string[]): Promise<void> { ... };
  async quickApproveAll(): Promise<void> { ... };  // 快速批准全部
}
```

---

## 四、Manual 模式

### 4.1 行为定义

```
用户控制每一步：

1. 用户决定启动哪个 Agent
2. 用户查看 Agent 输出后决定是否继续
3. 用户决定切换到哪个 Agent
4. 用户可以直接向任何 Agent 提问
5. 用户随时可以接管任何一个 Agent 的控制权
```

### 4.2 界面设计

```
┌─────────────────────────────────────────────────────────┐
│  🎮 MANUAL 模式                                         │
│  完全手动控制 — 当前: Codex 正在实现 JWT                 │
│  [切换模式] [直接向 Agent 提问] [强制停止]               │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ━━━ Codex (LEAD) — 执行中                              │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│  I'll implement JWT authentication...                   │
│  Reading src/auth/session.ts...                         │
│  [View Full Output]                                      │
│                                                          │
│  ━━━ 下一步选择 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                          │
│  [▶ 让 Claude Code 进行安全审查]                         │
│  [▶ 让 Aider 补充测试]                                   │
│  [▶ 直接提问 Codex]                                      │
│  [⏸ 暂停 Codex]                                          │
│  [📋 查看当前 Agent 上下文]                              │
│  [✋ 取消整个任务]                                        │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 五、模式切换

### 5.1 切换规则

```typescript
enum ControlMode {
  AUTOMATED = 'AUTOMATED',    // Autopilot
  SUPERVISED = 'SUPERVISED',  // Supervised
  MANUAL = 'MANUAL'           // Manual
}

// 切换规则
const MODE_TRANSITIONS = {
  // 任何时候都可以切到 Manual
  ANY_TO_MANUAL: true,
  
  // 运行中的任务可以切到 Supverised
  AUTOMATED_TO_SUPERVISED: true,
  
  // 从 Supervised 可以回到 Autopilot（但只影响后续步骤）
  SUPERVISED_TO_AUTOMATED: true,
  
  // 不能直接从 Manual 跳到 Autopilot（需要先切到 Supervised）
  MANUAL_TO_AUTOMATED: false,
};
```

### 5.2 切换影响范围

```
切换模式时：
  ✅ 影响：未来的决策和审批行为
  ❌ 不影响：当前正在执行的操作
  
例如：
  Codex 正在实现 JWT（在 Manual 模式）
  → 用户切换到 Autopilot
  → Codex 继续执行（不受影响）
  → Codex 完成后，进入 VERIFYING 状态
  → 此时 Autopilot 规则生效（自动验证）
```

### 5.3 切换时的通知

```
[模式切换] 从 Manual 切换到 Supervised
  • 当前执行不受影响
  • 下个审批节点将暂停等待确认
  • 已自动审批的规则: 12 条
  • 下次审批: Claude Code security review（将在完成后触发）

[已应用] 新模式将在下一个决策点生效
```

---

## 六、Policy Engine 与模式联动

### 6.1 Policy 根据模式决定行为

```typescript
class ModeAwarePolicy implements TaskPolicy {
  private innerPolicy: TaskPolicy;
  private mode: ControlMode;
  
  decide(state: TaskState, event: EventType, ctx: Context): PolicyDecision {
    const baseDecision = this.innerPolicy.decide(state, event, ctx);
    
    // 根据模式调整决策
    return this.applyModeAdjustment(baseDecision, state, event, ctx);
  }
  
  private applyModeAdjustment(
    decision: PolicyDecision,
    state: TaskState,
    event: EventType,
    ctx: Context
  ): PolicyDecision {
    switch (this.mode) {
      case ControlMode.AUTOMATED:
        return this.applyAutopilotAdjustment(decision, state, event);
      case ControlMode.SUPERVISED:
        return this.applySupervisedAdjustment(decision, state, event);
      case ControlMode.MANUAL:
        return this.applyManualAdjustment(decision, state, event);
    }
  }
  
  private applyAutopilotAdjustment(
    decision: PolicyDecision,
    state: TaskState,
    event: EventType
  ): PolicyDecision {
    // Autopilot: 只有高风险才暂停
    if (decision.action === 'NEED_APPROVAL') {
      if (this.isHighRisk(event, state)) {
        return decision;  // 保持需要审批
      }
      // 低风险自动批准
      return new PolicyDecision(
        decision.nextState,
        'APPROVE',
        decision.pluginId,
        'Auto-approved by autopilot policy',
        decision.params
      );
    }
    return decision;
  }
  
  private applySupervisedAdjustment(
    decision: PolicyDecision,
    state: TaskState,
    event: EventType
  ): PolicyDecision {
    // Supervised: 所有 Agent 切换都需确认
    if (decision.action === 'PROCEED' && this.isAgentChange(event)) {
      return new PolicyDecision(
        decision.nextState,
        'NEED_APPROVAL',
        decision.pluginId,
        'Supervised mode: agent handoff requires confirmation',
        { ...decision.params, autoApprovable: true }  // 可批量审批
      );
    }
    return decision;
  }
  
  private applyManualAdjustment(
    decision: PolicyDecision,
    state: TaskState,
    event: EventType
  ): PolicyDecision {
    // Manual: 几乎所有动作都需要确认
    if (decision.action === 'PROCEED') {
      return new PolicyDecision(
        decision.nextState,
        'NEED_APPROVAL',
        decision.pluginId,
        'Manual mode: user confirmation required',
        decision.params
      );
    }
    return decision;
  }
}
```

---

## 七、项目级默认模式

### 7.1 首次使用

新用户新建项目时，默认 **Supervised** 模式：

```
New Project
  Control Mode: [Supervised ▼]
  说明: 适合大多数场景 — 自动调度但关键节点会确认
```

### 7.2 根据项目成熟度自动建议

```typescript
class ModeRecommendationService {
  
  recommendMode(projectId: string): ControlMode {
    const profile = loadProjectProfile(projectId);
    const taskCount = profile.totalTasks;
    const avgSuccessRate = profile.averageSuccessRate;
    
    if (taskCount < 5) {
      return ControlMode.MANUAL;  // 新项目，先手动了解
    }
    if (avgSuccessRate >= 0.9) {
      return ControlMode.AUTOMATED;  // 高度信任，自动模式
    }
    return ControlMode.SUPERVISED;  // 默认
  }
}
```

### 7.3 用户自定义默认值

```typescript
interface UserPreferences {
  defaultMode: ControlMode;
  autopilotRules: AutoApprovalRule[];
  supervisedCheckpoints: string[];  // 哪些事件强制暂停
}
```

---

## 八、紧急操作

### 8.1 用户紧急操作列表

```typescript
interface UserEmergencyActions {
  /** 立即停止当前所有 Agent */
  emergencyStop(): Promise<void>;
  
  /** 暂停当前任务，不完成 */
  pause(): Promise<void>;
  
  /** 恢复被暂停的任务 */
  resume(): Promise<void>;
  
  /** 切换到指定 Agent 提问 */
  askAgent(pluginId: string, question: string): Promise<void>;
  
  /** 直接向特定 Agent 发指令 */
  sendInstruction(pluginId: string, instruction: string): Promise<void>;
  
  /** 查看某个 Agent 的完整上下文 */
  viewAgentContext(pluginId: string): Promise<AgentContext>;
}
```

### 8.2 界面入口

```
┌─────────────────────────────────────────────────────────┐
│  紧急操作                                                │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ ⏸ 暂停   │ │ 🛑 停止  │ │ 💬 提问  │ │ 🎮 接管  │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
├─────────────────────────────────────────────────────────┤
│  直接向 Agent 提问:                                      │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Codex → "能帮我解释一下为什么用 refresh token？"    │ │
│  │                                    [发送]          │ │
│  └────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

## 九、三种模式的对比表格

| 维度 | Autopilot | Supervised | Manual |
|---|---|---|---|
| Agent 调度 | 完全自动 | 关键节点确认 | 每步确认 |
| 审批触发 | 仅高风险 | 每个 Agent 切换 + 高风险 | 所有动作 |
| 用户干预 | 极少 | 中等 | 频繁 |
| 适合场景 | 熟悉项目 / 低风险 | 大多数场景 | 新项目 / 调试 |
| 学习成本 | 低 | 中 | 高 |
| 控制权 | 系统主导 | 共享 | 用户主导 |
| 推荐状态 | 成功率高后自动启用 | 新项目默认 | 手动开启 |

---

## 十、与 Orca 的对比

| 维度 | Orca | TeamMind |
|---|---|---|
| 控制方式 | 用户手动启动每个 Agent | 系统自动调度 |
| 审批机制 | 无（人工 review） | 三级控制模式 |
| 任务中断 | 用户手动停止 | 随时暂停/恢复 |
| 用户介入 | 随时介入对话 | 结构化审批 + 随时提问 |
| 自动化程度 | 0%（手动） | 0-100%（可调节） |

**TeamMind 的差异化**：Orca 让用户手动管理，TeamMind 让用户**调节自动化程度**。

---

## 十一、实施建议

### Phase 1（v0.1）
- 实现 Supervised 模式（核心）
- 基本审批 UI
- Manual 模式（简化版）

### Phase 2（v0.2）
- Autopilot 模式
- 自动审批规则引擎
- 项目级默认模式

### Phase 3（v0.3）
- 智能模式建议（基于项目历史）
- 批量审批
- 模式切换通知优化

---

**版本**：v0.1 Draft
**最后更新**：2026-08-14