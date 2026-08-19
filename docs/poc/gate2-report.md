# Gate 2: Real Task Execution — Status Report

> **日期：** 2026-08-19
> **状态：** 基础设施完成 / 真实任务待 QwenPaw workspace 就绪

---

## Gate 2 目标

```
Task: 分析 docs/research/ 下的 ADR → 生成 security-summary.md
验证链: QwenPaw ACP → Artifact → Execution Ledger → Evidence → Human Review
```

---

## 已完成的基础设施

### 1. Provider Readiness 状态机 ✅

```java
// ProviderState.java
public enum ProviderState {
    DISCOVERED, CONFIGURED, STARTING, READY, DEGRADED, UNAVAILABLE, STOPPED
}

// ProviderStatus.java
public record ProviderStatus(
    String providerId,
    ProviderState state,
    Instant stateChangedAt,
    long startupMs,
    String lastError,
    String[] capabilities
) {
    public boolean isRunnable() { ... }
    public boolean isStarting() { ... }
}
```

### 2. AgentTransport 扩展 ✅

```java
// AgentTransport.java
default ProviderStatus readiness() { ... }
default long prewarm(AgentConfig config) { ... }
```

### 3. QwenPawACPTransport Prewarm ✅

```java
// 启动时调用
transport.prewarm(config);  // 返回耗时(ms) 或 -1

// 状态追踪
providerStatus = ProviderStatus.starting("qwenpaw");
// → 后台启动 bridge
// → 等待 ready 事件
// → providerStatus = ProviderStatus.ready("qwenpaw", elapsed, caps)
```

### 4. 新事件类型 ✅

```java
EventType.PROVIDER_STATE_CHANGED   // 状态转移广播
EventType.PROVIDER_WARMING_UP      // 预热中广播
```

---

## Gate 2 测试结果

### OpenCode ACP（快速但无文件写入）

| 指标 | 结果 |
|------|------|
| 连接时间 | 4.1s |
| Events 数量 | 3 |
| Ready 事件 | ✅ |
| Chunk 事件 | ❌ 无 streaming |
| Done 事件 | ⚠️ 未捕获（subprocess 提前退出） |
| Artifact | ❌ 不存在（OpenCode ACP 不写文件） |

### QwenPaw ACP（完整但有启动延迟）

| 指标 | 结果 |
|------|------|
| 连接时间 | ~10s |
| Initialize | ✅ |
| NewSession | ⏳ >180s（workspace boot） |
| Streaming | ✅ `agent_message_chunk` |
| Prompt Response | ✅ 有文本 |

---

## 架构决策

### 不因 Provider 问题阻塞主线

```
QwenPaw workspace >180s
    ↓
记录为 Provider limitation
    ↓
由 Readiness + Prewarm 处理
    ↓
TeamMind 继续用 Codex/Claude 执行任务
```

### Provider Boundary 原则

> **Provider 内部复杂度必须对 TeamMind Runtime 透明。**

TeamMind 只看到：
```
Provider: qwenpaw
  State: STARTING
  Reason: workspace_initializing
  Elapsed: 47s
  
Provider: codex
  State: READY
```

---

## Gate 2 完整验证路径

```
1. TeamMind startup
   ↓
2. QwenPaw prewarm (async, 不阻塞)
   ↓
3. Provider 状态: STARTING → READY (或 DEGRADED if timeout)
   ↓
4. Router 选择 Provider:
   - 快速任务 → Codex (READY)
   - Research 任务 → QwenPaw (if READY)
   ↓
5. Task execution with Ledger recording
   ↓
6. Artifact verification
   ↓
7. Evidence generation
   ↓
8. Human review
```

---

## 下一步

### 立即（不阻塞主线）
- [ ] 修复 MCP tavily_search Node.js 错误 → 可能加速 workspace boot
- [ ] 测试 `--runtime-provider openai-env` 是否跳过 workspace

### Gate 2 完整验证（QwenPaw workspace 就绪后）
- [ ] 真实 ADR 分析任务
- [ ] security-summary.md artifact 验证
- [ ] Execution Ledger 自动记录
- [ ] Evidence 自动生成
- [ ] Human Review UI 验证

### Gate 3（Provider Comparison）
- [ ] 同一任务：QwenPaw vs Claude vs Codex
- [ ] 比较：latency / quality / evidence completeness
- [ ] 生成 Project Agent Performance Profile

---

## 核心结论

> **Gate 1 验证了 ACP 接入架构成立。**
> **Gate 2 的核心挑战不是协议，而是 Provider 启动性能。**
> **TeamMind 的主线开发不应被任何单一 Provider 的冷启动问题阻塞。**

Provider Readiness 状态机已就位，可以开始处理 STARTING 状态的 Provider，
同时用 READY 的 Provider 继续执行任务。
