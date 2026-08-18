# QwenPaw ACP Integration POC

> **目标：** 验证 TeamMind ↔ QwenPaw 通过 ACP 协议的集成可行性
> **状态：** POC 阶段（Mock Agent，待 QwenPaw 完整依赖安装后切换）
> **日期：** 2026-08-18

---

## 架构设计

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
  │                        POC: MockAgent    Real: qwenpaw acp
  │                        (Echo responses)  (ACP stdio server)
  │                              │              │
  └──────────────────────────────┴──────────────┘
                          ↓
                    JSONL Events → EventBus
```

## 交付物

| 文件 | 说明 |
|------|------|
| `scripts/qwenpaw-acp-bridge.py` | Python bridge（stdin/stdout JSONL 协议） |
| `backend/.../transport/QwenPawACPTransport.java` | Java transport 实现 |
| `backend/.../resources/cli-adapters/qwenpaw.yaml` | QwenPaw CLI 适配器配置 |
| `backend/.../transport/AgentTransportFactory.java` | 已更新：支持 QwenPaw 路由 |

## Bridge 协议

### 输入（stdin，JSONL）
```json
{"action":"prompt","prompt":"...","cwd":"/path"}
{"action":"cancel"}
{"action":"permission_response","request_id":"...","option_id":"allow_once"}
{"action":"ping"}
```

### 输出（stdout，JSONL）
```json
{"type":"ready","agent":"qwenpaw","mode":"auto|echo-fallback"}
{"type":"chunk","text":"..."}
{"type":"tool","name":"...","input":{...}}
{"type":"permission","request_id":"...","title":"...","options":[...]}
{"type":"done","stop_reason":"end_turn|cancelled"}
{"type":"error","message":"..."}
{"type":"closed"}
```

## 5 个验证场景

### 场景 1: Consultant（顾问模式）
Codex 执行任务时，用户问"为什么采用这个方案？"
→ TeamMind fork 一个 QwenPaw session，通过 ACP 提供咨询，不打断 Codex

```yaml
# pipeline 示例
steps:
  - name: implement
    agent: codex
    gate: ask
  - name: consult  # 并行 side-consultant
    agent: qwenpaw
    gate: auto
    parallel: true
```

### 场景 2: Research（研究）
QwenPaw 分析项目文档、历史 ADR、相关文件，返回 Artifact

```
Input:  "分析项目中所有 security-related ADR，总结关键决策"
Output: markdown report → .agents/knowledge/security-adr-summary.md
```

### 场景 3: Planning（规划）
用户和 QwenPaw 讨论后产出 plan.md + acceptance.yaml，再交给 Codex 执行

```
User → QwenPaw (planning mode)
     → plan.md
     → acceptance.yaml
     ↓
     Codex (implementation mode)
```

### 场景 4: Independent Review 辅助意见
QwenPaw 读取 Git diff + Ledger + Evidence，给 Claude Reviewer 二次意见

```
Claude Reviewer → 初评
QwenPaw        → 补充分析（从 Ledger 获取上下文）
Human          → 综合决策
```

### 场景 5: 环境能力验证
测试 QwenPaw 的 workspace、doctor、skill/plugin 等能力

```bash
# 诊断 QwenPaw 环境
echo '{"action":"prompt","prompt":"/doctor","cwd":"/tmp"}' | python bridge.py
```

## 当前状态

| 组件 | 状态 |
|------|------|
| Python Bridge (mock) | ✅ 工作正常 |
| Java Transport | ✅ 编译通过 |
| ACP SDK 连接 QwenPaw | ⏳ 需完整依赖安装 |
| QwenPaw ACP Server | ⏳ `pip install -e .` 后验证 |

## 下一步

1. **完成 QwenPaw 依赖安装** → 替换 mock agent 为真实 `qwenpaw acp`
2. **场景 1-5 逐项验证** → 记录每个场景的 input/output/latency
3. **Performance Profile** → 开始记录 QwenPaw 在各项任务中的表现
4. **Role Assignment** → 将 QwenPaw 注册为 `consultant` 角色

## POC 测试命令

```bash
# 测试 mock bridge
echo '{"action":"prompt","prompt":"Say hello","cwd":"."}' \
  | python scripts/qwenpaw-acp-bridge.py

# 预期输出:
# {"type":"ready","agent":"qwenpaw-mock","mode":"echo-fallback"}
# {"type":"chunk","text":"Hello! I'm QwenPaw (mock)..."}
# {"type":"done","stop_reason":"end_turn"}
```
