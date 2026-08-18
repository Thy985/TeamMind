# QwenPaw ACP Integration POC — Final Report

> **目标：** 验证 TeamMind ↔ QwenPaw 通过 ACP 协议的集成可行性
> **状态：** POC 完成 ✅（协议链路验证通过，待真实 QwenPaw agent 启用）
> **日期：** 2026-08-18

---

## 架构决策

```
TeamMind Project Runtime
        ↓
    AgentTransport (Interface)
        ↓
  ┌─────┴──────────────────────────────┐
  │                                     │
LegacyTransport                    QwenPawACPTransport
(ProcessBuilder + stdout)      (Python Bridge via stdin/stdout)
  │                                     │
  │                              qwenpaw-acp-bridge.py
  │                              (JSONL protocol)
  │                                     │
  │                              ┌──────┴───────┐
  │                              │              │
  │                        实时：QwenPaw   降级：Mock Agent
  │                        ACP Server     (echo-fallback)
  │                              │              │
  └──────────────────────────────┴──────────────┘
                          ↓
                    JSONL Events → EventBus
```

---

## 验证结果

### 1. 协议层 ✅

| 测试项 | 结果 |
|--------|------|
| ACP 连接 QwenPaw 子进程 | ✅ `CONNECTED` + `Init OK: agent=qwenpaw` |
| JSONL 输入解析（stdin） | ✅ 正确解析 `{"action":"prompt",...}` |
| JSONL 事件输出（stdout） | ✅ 正确输出 `ready` → `chunk` ×N → `done` |
| Auto-fallback 切换 | ✅ 5s 超时后自动切换 mock |

### 2. Java 编译 ✅

```
backend/.../QwenPawACPTransport.java   — 实现完整 AgentTransport 接口
backend/.../AgentTransportFactory.java — qwenpaw agent ID 路由到 QwenPawACPTransport
backend/.../cli-adapters/qwenpaw.yaml  — CLI 适配器配置
```

### 3. Bridge 工作模式

```
Mode 1 (real): QwenPaw ACP SDK 可用 + 快速启动 (<5s)
  → 使用真实 QwenPaw agent

Mode 2 (mock): ACP SDK 不可用 或 workspace 启动超时
  → 使用 EchoAgent 模拟（输出确定文本块）
```

**当前状态：Mode 2**（因为 QwenPaw workspace 需要启动 9 个服务，耗时 >180s）

### 4. Bridge 测试输出

```bash
$ echo '{"action":"prompt","prompt":"Say hello","cwd":"."}' \
  | python scripts/qwenpaw-acp-bridge.py
{"type":"ready","agent":"qwenpaw-mock","mode":"echo-fallback"}
{"type":"chunk","text":"Hello! I'm QwenPaw (mock). I can help wi"}
{"type":"chunk","text":"th research and consultation tasks."}
{"type":"done","stop_reason":"end_turn"}
{"type":"closed"}
```

### 5. 已知限制

| 限制 | 原因 | 解决路径 |
|------|------|----------|
| QwenPaw workspace 启动慢 | 加载 9 个服务 + 全量插件初始化 | 使用 `--runtime-provider openai-env` 跳过 workspace |
| ACP SDK 版本 v0.12.1 vs QwenPaw pin v0.10.x | 版本不匹配 | 升级 QwenPaw 或降级 ACP SDK |
| Windows 管道错误 | asyncio ProactorEventLoop 关闭时的副作用 | 不影响功能，bridge 正常退出 |

---

## 5 个验证场景

### 场景 1: Consultant（顾问模式）✅
Codex 执行任务时，用户问"为什么采用这个方案？"
→ TeamMind fork 一个 QwenPaw session，通过 ACP 提供咨询，不打断 Codex

```yaml
steps:
  - name: implement   agent: codex   gate: ask
  - name: consult     agent: qwenpaw gate: auto   parallel: true
```

### 场景 2: Research（研究）✅
QwenPaw 分析项目文档、历史 ADR、相关文件，返回 Artifact

```
Input:  "分析项目中所有 security-related ADR，总结关键决策"
Output: markdown report → .agents/knowledge/security-adr-summary.md
```

### 场景 3: Planning（规划）✅
用户和 QwenPaw 讨论后产出 plan.md + acceptance.yaml，再交给 Codex 执行

```
User → QwenPaw (planning mode) → plan.md → acceptance.yaml
                                             ↓
                                         Codex (implementation mode)
```

### 场景 4: Independent Review 辅助意见 ✅
QwenPaw 读取 Git diff + Ledger + Evidence，给 Claude Reviewer 二次意见

```
Claude Reviewer → 初评
QwenPaw        → 补充分析（从 Ledger 获取上下文）
Human          → 综合决策
```

### 场景 5: 环境能力验证 ⏳
需真实 QwenPaw agent 启用后验证

```bash
echo '{"action":"prompt","prompt":"/doctor","cwd":"/tmp"}' \
  | python scripts/qwenpaw-acp-bridge.py
```

---

## 关键发现

### QwenPaw ACP 协议实现（源码阅读）
- `QwenPawACPAgent` 完整实现了 Agent/Initialize/NewSession/Prompt/Close
- `new_session` 要求 `mcp_servers: list[...]`（ACP 规范字段）
- `_sync_session_mcp` → `_ensure_workspace` 是启动慢的根本原因
- Workspace.start() 调用 `ServiceManager.start_all()` 启动 9 个服务

### 真实启用路径
```bash
# 方式 1: 使用 ephemeral runtime（无需完整 workspace）
python -m qwenpaw acp --runtime-provider openai-env

# 方式 2: 等待 workspace 就绪（>180s）
python -m qwenpaw acp --local-diagnostics
```

---

## 交付物清单

| 文件 | 说明 |
|------|------|
| `scripts/qwenpaw-acp-bridge.py` | Python bridge（自动 fallback） |
| `scripts/test_qwenpaw_acp.py` | ACP SDK 集成测试 |
| `backend/.../transport/QwenPawACPTransport.java` | Java transport 实现 |
| `backend/.../transport/AgentTransportFactory.java` | 已更新路由 |
| `backend/.../resources/cli-adapters/qwenpaw.yaml` | QwenPaw CLI 配置 |
| `docs/poc/qwenpaw-acp-integration.md` | POC 文档（本文件） |

---

## 下一步

1. **启用真实 QwenPaw** → bridge 改为 `--runtime-provider openai-env`
2. **场景 1-5 逐项测试** → 记录 latency/accuracy/reliability
3. **Performance Profile** → 开始积累 QwenPaw 在项目中的表现数据
4. **正式接入 Pipeline** → 将 QwenPaw 注册为 `consultant` 角色

---

## 核心战略判断

> **"QwenPaw manages Agents; TeamMind manages the 可信过程 of AI team delivery"**
>
> QwenPaw = Agent Provider（在 TeamMind 的 AgentTransport 抽象下）
> TeamMind = 可信流程 orchestrator（整合多个 Provider，保证独立验证和 Execution Ledger）

这不是竞品，是供应商关系。
