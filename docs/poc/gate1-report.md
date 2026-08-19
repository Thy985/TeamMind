# Gate 1: QwenPaw ACP Integration — Final Report

> **日期：** 2026-08-19
> **状态：** ✅ 协议层验证通过 / ⚠️ 运行时性能问题待解决

---

## 测试结果

### QwenPaw ACP（完整实现）

| 测试项 | 结果 | 耗时 |
|--------|------|------|
| CONNECT | ✅ | <1s |
| Initialize | ✅ `agent=qwenpaw ver=1` | ~10s |
| NewSession | ⚠️ 超时 | >180s（workspace boot） |
| Prompt + Streaming | ✅ `agent_message_chunk` | ~20s（warm） |
| Session Close | ✅ | <1s |

### OpenCode ACP（不完整实现）

| 测试项 | 结果 | 耗时 |
|--------|------|------|
| CONNECT | ✅ | <1s |
| Initialize | ✅ `agent=OpenCode` | ~5s |
| NewSession | ✅ | ~5s |
| Prompt | ⚠️ 无文本 | ~20s |
| Streaming | ❌ 无事件 | - |
| Response | ❌ 只有 stop_reason + usage | - |

### 关键发现

**QwenPaw workspace boot 慢的原因：**

```
Workspace.start()
  → ensure_skill_pool_initialized()     # ~1s
  → load_agent_config('default')        # ~0.1s
  → ServiceManager.start_all()          # >120s ← 瓶颈
      → 9 services (ChatManager, Memory, etc.)
      → MCP clients (tavily_search fails: Node.js module error)
      → WebSocket channels
      → File watcher
```

**QwenPaw ACP `new_session` 卡住的位置：**

```python
# QwenPawACPAgent.new_session
async def new_session(self, cwd, mcp_servers=None, **kwargs):
    # 1. Register session           # ~0.1s
    session_id = uuid4().hex
    self._sessions[session_id] = ...

    # 2. Sync MCP drivers          # ~0.1s (returns early if no cards)
    await self._sync_session_mcp(session_id, mcp_servers)

    # 3. Advertise commands        # background task (non-blocking)
    asyncio.create_task(self._advertise_commands(session_id))
    #   → _ensure_workspace() ← HANGS HERE (blocks prompt but not new_session)

    # 4. Build model state         # ~0s (33 providers, instant)
    models = await self._build_model_state()

    return NewSessionResponse(...)   # ← Should return immediately!
```

**实际测试发现：** `new_session` 本身应该很快返回，但测试中卡住了。可能是：
- `_build_model_state()` 内部的 `ProviderManager.list_provider_info()` 有网络请求
- 某些 provider 的 `get_info()` 调用超时
- ACP SDK 的 `send_request` 在等待响应时没有正确处理超时

---

## 架构验证结论

### ✅ 已验证

```
TeamMind → AgentTransport → QwenPawACPTransport → Python Bridge → ACP JSONL → QwenPaw
```

1. **协议层成立** — ACP JSON-RPC over stdio 正常工作
2. **抽象层成立** — `AgentTransport` 接口可以封装不同 backend
3. **Bridge 层成立** — stdin/stdout JSONL 协议正常工作
4. **Streaming 成立** — QwenPaw `agent_message_chunk` 事件正常接收

### ⚠️ 问题

| 问题 | 影响 | 解决路径 |
|------|------|---------|
| QwenPaw workspace 启动 >180s | 首次 prompt 慢 | Persistent session / pre-warm |
| OpenCode ACP 无 streaming | 无法用于实时任务 | 等待 OpenCode 更新 |
| MCP tavily_search 失败 | Node.js 模块缺失 | 修复 npx 缓存 |

### ❌ 未验证（需要后续 Gate）

- Gate 2: 真实工程任务（QwenPaw → Artifact → Ledger → Evidence）
- Gate 3: Provider Comparison（QwenPaw vs Claude vs Codex）

---

## Bridge 当前能力

```bash
# Mock 模式（POC 测试）
python scripts/qwenpaw-acp-bridge.py --mode mock

# 真实 QwenPaw（需等待 workspace 启动）
python scripts/qwenpaw-acp-bridge.py --mode real --backend qwenpaw

# 真实 OpenCode（快速但无文本）
python scripts/qwenpaw-acp-bridge.py --mode real --backend opencode
```

### JSONL 协议

```
IN:  {"action":"prompt","prompt":"...","cwd":"/path"}
OUT: {"type":"chunk","text":"..."}
OUT: {"type":"done","stop_reason":"end_turn"}
OUT: {"type":"error","message":"..."}
OUT: {"type":"ready","agent":"...","mode":"real|mock"}
```

---

## Java 集成状态

| 文件 | 状态 |
|------|------|
| `QwenPawACPTransport.java` | ✅ 编译通过 |
| `AgentTransportFactory.java` | ✅ qwenpaw 路由 |
| `qwenpaw.yaml` | ✅ CLI 适配器配置 |

---

## Gate 1 评分

| 维度 | 评分 | 说明 |
|------|------|------|
| ACP 接入架构 | 9/10 | 协议链路完整，抽象层干净 |
| Provider 抽象 | 9/10 | AgentTransport 接口可扩展 |
| TeamMind 解耦 | 9/10 | QwenPaw 无需进入 Core |
| 真实 QwenPaw 验证 | 5/10 | 协议通，但 workspace 启动慢 |
| 产品价值验证 | 2/10 | 未见真实任务产出 |

---

## 下一步：Gate 2 前置条件

**需要解决的问题：**

1. **QwenPaw workspace 启动优化** — 考虑 persistent session 或 pre-warm
2. **MCP tavily_search 修复** — `npm cache clean --force` + reinstall
3. **OpenCode ACP 功能补全** — 与 OpenCode 团队反馈 streaming 缺失问题

**Gate 2 测试任务：**

```
Task: 分析 TeamMind/docs/research/ 下的 ADR 文件
Output: security-summary.md
验证: Artifact → Execution Ledger → Evidence → Human review
```

---

## 核心战略判断

> **"QwenPaw manages Agents; TeamMind manages the 可信过程 of AI team delivery."**
>
> ACP 接入架构已验证成立。QwenPaw 可以作为 Provider 接入 TeamMind。
> 真实任务能力验证需要解决 workspace 启动性能问题后继续。
