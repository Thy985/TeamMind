# QwenPaw ACP Integration POC — Gate 1 完成报告

> **目标：** Gate 1 — 验证真实 QwenPaw ACP agent 可用
> **日期：** 2026-08-19
> **状态：** ✅ Gate 1 通过（协议层） / ⚠️ 需解决 workspace 启动时间

---

## Gate 1 测试结果

### 1. QwenPaw ACP（完整实现）

```
✅ CONNECTED
✅ INIT OK: agent=qwenpaw ver=1
✅ NEW_SESSION: session_id generated
⚠️  Prompt: workspace 启动 >180s（9 个服务初始化）
✅ Session 管理：close_session 正常
✅ Streaming: agent_message_chunk 事件正常
```

**限制：** QwenPaw workspace 启动需要 >180s，无法用于快速任务。

### 2. OpenCode ACP（不完整实现）

```
✅ CONNECTED
✅ INIT OK: agent=OpenCode
✅ NEW_SESSION: session_id generated
✅ Prompt: 完成（~20s）
❌ Streaming: 无 session_update 事件
❌ Response: PromptResponse 无文本内容（只有 stop_reason + usage）
```

**结论：** OpenCode ACP 实现不完整，不支持流式响应。

---

## 架构验证结果

```
TeamMind → AgentTransport → QwenPawACPTransport → Python Bridge → JSONL → QwenPaw ACP
```

| 层级 | 状态 | 说明 |
|------|------|------|
| Contract | ✅ | ACP 协议接口成立 |
| Transport | ✅ | Bridge 正常连接 QwenPaw |
| Product | ⏳ | 需解决 workspace 启动时间 |

---

## Bridge 能力矩阵

| 模式 | 后端 | 流式 | 响应内容 | 启动时间 |
|------|------|------|---------|---------|
| `--mode mock` | EchoAgent | ❌ | 预定义文本 | <100ms |
| `--mode real --backend qwenpaw` | QwenPaw ACP | ✅ | 完整 | >180s |
| `--mode real --backend opencode` | OpenCode ACP | ❌ | 无文本 | ~5s |

---

## Gate 1 结论

**ACP 接入架构验证成立。** TeamMind 可以通过 `AgentTransport` 抽象层接入不同 ACP agent：

```java
// AgentTransportFactory 路由逻辑
if ("qwenpaw".equals(agentId)) {
    return new QwenPawACPTransport(eventBus, bridgeScript);
}
```

**但真实 QwenPaw agent 需要解决启动性能问题。** 可行路径：

1. **Ephemeral Runtime**：使用 `--runtime-provider openai-env` 跳过 workspace
2. **Persistent Session**：保持 QwenPaw 进程常驻，复用 session
3. **Pre-warm**：在项目启动时预启动 QwenPaw ACP server

---

## 下一步：Gate 2

**目标：** 真实工程任务验证

```
Task: 分析 TeamMind 中现有安全相关 ADR，生成 security-summary.md
验证链: QwenPaw → Artifact → Execution Ledger → Evidence → Human review
```

**前提：** 解决 QwenPaw workspace 启动性能问题（Persistent Session 方案最可行）
