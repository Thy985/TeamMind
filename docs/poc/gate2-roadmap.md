# Gate 2: 垂直切片 — 架构决策与开发路线

> **日期：** 2026-08-19
> **状态：** 基础设施完成 / 垂直切片进行中

---

## 架构决策

### 核心原则

> **Provider 内部复杂度必须对 TeamMind Runtime 透明。**

TeamMind 不关心 QwenPaw 内部有 1 个进程还是 9 个服务。
只看到：

```
Provider: qwenpaw
  State: STARTING → READY
  Capabilities: research, planning, consultation
  Transport: ACP
```

### QwenPaw workspace >180s 的定位

```
❌ 不是 TeamMind P0
✅ 是 Provider limitation / readiness issue
```

解决方案：
- Provider Readiness 状态机
- Pre-warm（异步，不阻塞）
- Router 优先调度 READY Provider

---

## 已交付

### Provider State 机器

| 文件 | 状态 |
|------|------|
| `ProviderState.java` | ✅ DISCOVERED / CONFIGURED / STARTING / READY / DEGRADED / UNAVAILABLE / STOPPED |
| `ProviderStatus.java` | ✅ 完整状态快照（state + startupMs + error + capabilities） |
| `AgentTransport.readiness()` | ✅ 默认实现，可覆盖 |
| `AgentTransport.prewarm()` | ✅ 默认返回 -1（不支持预热） |

### ReadinessManager 集成

```java
// QwenPawACPTransport.prewarm() 调用
readinessManager.setProviderStatus(ProviderStatus.starting("qwenpaw"));
// → bridge 启动 → ready 事件 → 
readinessManager.setProviderStatus(ProviderStatus.ready("qwenpaw", elapsed, caps));
// OR on failure
readinessManager.setProviderStatus(ProviderStatus.unavailable("qwenpaw", error));
```

```java
// ReadinessManager.check() 优先检查 Provider State
if (providerStatus.isStarting()) return CONFIGURED; // 预热中，可调但降权
if (providerStatus.isUnavailable()) return UNAVAILABLE;
if (providerStatus.isDegraded()) return DEGRADED;
// 然后走原有 dependency check 逻辑
```

### Performance Profile 数据契约

```java
// AgentPerformanceRecord — 单次任务记录
record (
    projectId, agentId, transport, role, taskType,
    startedAt, completedAt, durationMs,
    result, verificationResult, artifacts[],
    reworkCount, reviewFindings, acceptedFindings, humanAccepted,
    evidenceQuality
)

// ProjectAgentProfile — 项目级聚合
record (
    projectId, agentId, primaryRole, capabilities[],
    totalTasks, successfulTasks, successRate,
    avgDurationMs, avgReworkCount, humanAcceptanceRate,
    avgEvidenceQuality,
    taskTypeSuccessRates{taskType → rate},
    providerState, readyAtMs
)
```

---

## 开发优先级

```
P0
├── 真实 Vertical Slice（Codex/Claude → Artifact → Ledger → Reviewer → Verifier → Human）
├── Execution Ledger 最小闭环
└── ProviderState → Router 真正接通

P1
├── Performance Profile schema（已完成）
├── Provider Comparison harness
└── Evidence 生成自动化

P2
└── QwenPaw workspace 优化（独立支线，不阻塞主线）
```

---

## 路由决策逻辑（已就绪）

```
Task 到达
    ↓
CapabilityRouter.route(capability, plugins, projectId, taskDescription)
    ↓
ReadinessManager.check(pluginId)
    ├─ Provider State = STARTING → CONFIGURED（降权，不调度）
    ├─ Provider State = READY   → READY（正常评分）
    └─ Provider State = UNAVAILABLE → UNAVAILABLE（排除）
    ↓
PolicyEngine.filter(violations)
    ↓
8-factor scoring
    ↓
最高分 Plugin + RoutingDecision
```

---

## Gate 2 验收标准

### QwenPaw 路径（workspace 就绪后）

```
QwenPaw ACP
    ↓
真实 Prompt
    ↓
真实文件读取
    ↓
真实分析
    ↓
security-summary.md artifact
    ↓
Execution Ledger 记录
    ↓
Evidence 生成
    ↓
Human Review
```

### Codex/Claude 路径（立即可用）

```
User Task
    ↓
CapabilityRouter → codex/claude-code（READY）
    ↓
Executor 执行
    ↓
Artifact 生成
    ↓
Execution Ledger
    ↓
Reviewer（Claude）审查
    ↓
Verifier 验证
    ↓
Human Review
```

---

## 垂直切片测试任务

```
Task: 分析 docs/research/ 下的 ADR 文件，生成 security-summary.md
Expected:
  - security-summary.md 存在且包含安全相关内容
  - Execution Ledger 记录文件读取/写入
  - Evidence: ArtifactExistenceEvidence + FileContentEvidence
  - Human Review UI 显示 Accept/Reject
```

---

## 状态总结

| Gate | 状态 |
|------|------|
| Gate 1 | ✅ ACP 协议层验证通过 |
| Gate 2 基础设施 | ✅ Provider Readiness + Pre-warm + Performance Profile |
| Gate 2 真实任务 | ⏳ 等待 workspace 就绪或改用 Codex/Claude 验证链路 |
| Gate 3 | ⏳ 待 Gate 2 完成后进行 Provider Comparison |

---

## 下一步

1. **P0**：实现真实 Vertical Slice（使用 Codex/Claude 作为 Executor）
2. **P0**：验证 ProviderState → Router 调度行为
3. **P1**：将 AgentPerformanceRecord 写入 Execution Ledger
4. **P2**：QwenPaw workspace 优化（独立支线）
