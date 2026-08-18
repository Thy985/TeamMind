# TeamMind ACP 集成方案 v1

> **目标：** 为现有 CLI Agent 插件提供 ACP（Agent Client Protocol）结构化事件路径，
> 与现有 text/NDJSON 路径并行共存，通过 YAML `transport` 字段选择。

---

## 一、现状分析

### 当前事件解析链路

```
CLI Agent (Codex/Claude/Atomcode)
  ↓ stdout (text / NDJSON)
  ↓ 手写正则/字符串匹配
AgentPlugin.parseOutput()
  ↓ 猜测事件类型
EventBus.emit(TeamMindEvent)
  ↓
Execution Ledger
```

**问题：**
- CodexPlugin：硬编码 `">>> STEP:"` / `"✓ Done"` / `"✗ Error"` 前缀匹配
- ClaudeCodePlugin：硬编码 NDJSON `{type: "assistant"/"tool"/"result"}` 解析
- 每新增一个 CLI，就要手写一套 parser
- 无法区分 tool call vs 普通输出 vs 错误
- 文件变更、权限请求等结构化信息丢失

### ACP 可用状态

| Agent | ACP/JSON 模式 | 事件丰富度 | 稳定性 |
|-------|--------------|-----------|--------|
| **Codex** | `--json` (ACP 兼容) | terminal_output, file_change, tool_call, tool_result, completion, permission_request | ✅ 官方稳定 |
| **Claude Code** | `--output-format=stream-json --verbose` | assistant text, tool use, tool result, thinking, step, permission | ✅ 官方稳定 |
| **Atomcode** | 需调研 | 未知 | ⚠️ 待确认 |

---

## 二、方案设计

### 2.1 分层架构

```
┌─────────────────────────────────────────────────────────┐
│              TeamMind Runtime                           │
│  TaskState | Router | Evidence | Ledger | Supervision   │
├─────────────────────────────────────────────────────────┤
│              Plugin Layer                               │
│                                                         │
│  Plugin<T> interface (不变)                             │
│    │                                                    │
│    ├── LegacyCliPlugin                                 │
│    │    ├── CodexPlugin        (text parser, 不动)      │
│    │    ├── ClaudeCodePlugin   (NDJSON parser, 不动)    │
│    │    └── AtomcodePlugin     (text parser, 不动)      │
│    │                                                    │
│    └── ACPPlugin    ← 新增                              │
│         ├── CodexACPPlugin    (ACPEventMapper)          │
│         └── ClaudeACPPlugin   (ACPEventMapper)          │
│                                                          │
├─────────────────────────────────────────────────────────┤
│              Transport Layer                            │
│                                                         │
│  ACP SDK (官方):                                        │
│  ├── codex-acp          (TypeScript, NPM)              │
│  ├── claude-agent-acp   (TypeScript, NPM)              │
│  └── rust-sdk           (Rust, crates.io)              │
│                                                         │
│  Java 层通过 ProcessBuilder 调用 CLI + --json 标志      │
│  不使用 ACP SDK (避免 JNI/FFI 复杂性)                    │
└─────────────────────────────────────────────────────────┘
```

### 2.2 YAML 配置扩展

在现有 `CLIConfig` 基础上新增 `transport` 字段：

```yaml
# 现有配置（text/NDJSON 路径，不变）
agent: codex
transport: legacy    # 或省略（默认 legacy）
command: "codex"
args: ["exec", "-c", "approval_policy=never", "<prompt>"]
output_format: "text"

# 新增 ACP 路径
agent: codex-acp
transport: acp       # 新字段
command: "node"
args: ["<cx-path>", "exec", "--json", "--approve-for-me",
       "--sandbox", "danger-full-access", "<prompt>"]
output_format: "ndjson"  # ACP 输出是 JSONL
```

### 2.3 事件映射完整对照表

#### Codex ACP → TeamMind

| ACP 事件 | TeamMind 事件 | metadata 关键字段 |
|----------|--------------|------------------|
| `session.started` | `PROCESS_STARTED` | session_id, agent |
| `terminal_output` | `AGENT_CHUNK` | text, is_error |
| `file_change` | `FILE_CHANGED` | path, action (create/modify/delete) |
| `tool_call` | `TOOL_CALLED` | tool_name, input |
| `tool_result` | `TOOL_RESULT` | tool_name, is_error, result_length |
| `permission_request` | `DECISION_REQUIRES_APPROVAL` | description, request_id, tool_name |
| `completion` exit_code=0 | `TASK_COMPLETED` + `EVIDENCE_VERIFIED` | exit_code, summary_length |
| `completion` exit_code≠0 | `TASK_FAILED` | exit_code |
| `error` critical=true | `ERROR_CRITICAL` | message |
| `error` critical=false | `ERROR_RECOVERABLE` | message |
| `subagent.start` | `AGENT_STARTED` | agent |

#### Claude Code stream-json → TeamMind

| Claude JSON 事件 | TeamMind 事件 | metadata 关键字段 |
|-----------------|--------------|------------------|
| `{"type":"start"}` | `PROCESS_STARTED` | session_id |
| `{"type":"text","delta":"..."}` | `AGENT_CHUNK` | text |
| `{"type":"thinking","delta":"..."}` | `AGENT_CHUNK` | text, source=thinking |
| `{"type":"tool_use","name":"Read","input":{...}}` | `TOOL_CALLED` | tool=name, input |
| `{"type":"tool_result","is_error":false,"result":"..."}` | `TOOL_RESULT` | tool, is_error, result_length |
| `{"type":"step","sub_type":"end"}` | `TASK_COMPLETED` | step_name |
| `{"type":"step","sub_type":"error"}` | `TASK_FAILED` | error |
| `{"type":"permission_request",...}` | `DECISION_REQUIRES_APPROVAL` | description |

---

## 三、实现步骤

### Step 1: 扩展 CLIConfig（1 个文件）

**文件：** `backend/src/main/java/com/teammind/plugin/adapter/CLIConfig.java`

新增字段：
```java
public record CLIConfig(
    String cliId,
    String command,
    List<String> args,
    Map<String, String> env,
    String workingDir,
    int timeoutMinutes,
    OutputFormat outputFormat,
    HealthCheck healthCheck,
    List<PluginDependency> dependencies,
    Transport transport        // ← 新增：legacy | acp
) {
    public enum Transport {
        LEGACY,  // 原有：text/NDJSON 解析
        ACP      // 新增：ACPEventMapper
    }
}
```

`fromMap()` 支持读取 `transport` 字段，默认 `legacy`。

### Step 2: 扩展 ACPEventMapper（1 个文件）

**文件：** `backend/src/main/java/com/teammind/event/mapper/ACPEventMapper.java`

在现有 Codex ACP 映射基础上，新增 Claude Code stream-json 映射：

```java
switch (type) {
    // ... 现有 Codex ACP 事件 ...
    case "start" -> events.add(mapSessionStarted(node, taskId, pluginId, role));
    case "text" -> events.add(mapTextDelta(node, taskId, pluginId, role));
    case "thinking" -> events.add(mapThinking(node, taskId, pluginId, role));
    case "tool_use" -> events.addAll(mapToolUse(node, taskId, pluginId, role));
    case "tool_result" -> events.addAll(mapToolResult(node, taskId, pluginId, role));
    case "step" -> events.addAll(mapStep(node, taskId, pluginId, role));
    case "permission_request" -> events.add(mapApprovalRequest(node, taskId, pluginId, role));
    case "error" -> events.add(mapError(node, taskId, pluginId, role));
    default -> events.add(mapUnknownEvent(...));
}
```

### Step 3: 新增 ClaudeACPPlugin（1 个文件）

**文件：** `backend/src/main/java/com/teammind/plugin/adapter/ClaudeACPPlugin.java`

```java
public class ClaudeACPPlugin extends ACPCLIPlugin {
    public ClaudeACPPlugin(CLIConfig config, EventBus eventBus) {
        super(config, eventBus);
    }

    @Override
    public String id() { return "claude-acp"; }

    @Override
    public PluginMetadata metadata() {
        return new PluginMetadata(
            "claude-acp", "Claude Code (ACP)", VERSION,
            "Claude Code via ACP stream-json transport",
            List.of("security_review", "code_review", "architecture_design"),
            List.of("safety", "controlled_action"),
            List.of("security_review", "code_review"),
            List.of("bulk_refactor"),
            45_000L, 0.92, 0.05
        );
    }
}
```

### Step 4: 注册 ACP 插件（修改 1 个文件）

**文件：** `backend/src/main/java/com/teammind/plugin/PluginRegistry.java`

```java
public void registerAll() {
    register(new ClaudeCodePlugin(eventBus));       // legacy (不变)
    register(new CodexPlugin(eventBus));            // legacy (不变)
    register(new GitVerifier(eventBus));
    register(new TestRunnerVerifier(eventBus));

    // 新增：ACP transport plugins
    register(new CodexACPPlugin(buildCodexACPConfig(), eventBus));
    register(new ClaudeACPPlugin(buildClaudeACPConfig(), eventBus));
}

private CLIConfig buildCodexACPConfig() {
    return CLIConfig.of("codex-acp", "node", CLIConfig.OutputFormat.NDJSON)
        .withTransport(CLIConfig.Transport.ACP)
        .withArgs(List.of(
            System.getenv("CX_PATH") != null ? System.getenv("CX_PATH") : "codex",
            "exec", "--json", "--approve-for-me",
            "--sandbox", "danger-full-access",
            "--skip-git-repo-check", "<prompt>"
        ));
}

private CLIConfig buildClaudeACPConfig() {
    return CLIConfig.of("claude-acp", "claude", CLIConfig.OutputFormat.NDJSON)
        .withTransport(CLIConfig.Transport.ACP)
        .withArgs(List.of(
            "--print", "--output-format", "stream-json",
            "--verbose", "--permission-mode", "acceptEdits",
            "<prompt>"
        ));
}
```

### Step 5: ACPCLIPlugin 根据 transport 选择 mapper

**文件：** `backend/src/main/java/com/teammind/plugin/adapter/ACPCLIPlugin.java`

```java
@Override
protected void parseNDJSON(String line, String taskId, PluginChunkHandler handler) {
    // 已有逻辑：解析 JSON → ACPEventMapper
    // 新增：根据 transport 类型选择 mapper
    if (config().transport() == Transport.ACP) {
        // 使用 ACPEventMapper（已实现）
        var events = eventMapper.map(cliEvent, ctx);
        events.forEach(getEventBus()::emit);
    } else {
        // fallback: 原有 extractField 逻辑
        super.parseNDJSON(line, taskId, handler);
    }
}
```

### Step 6: 测试（3 个文件）

| 测试文件 | 内容 |
|---------|------|
| `ClaudeACPEventMapperTest.java` | 测试 Claude stream-json → TeamMind 映射 |
| `ACPTransportIntegrationTest.java` | 端到端：启动 codex-acp/claude-acp，验证事件流 |
| `ACPCLIPluginTest.java` | 单元测试：verify 继承关系 + transport 选择 |

---

## 四、文件变更清单

| 操作 | 文件 |
|------|------|
| 修改 | `CLIConfig.java` — 新增 Transport 枚举 + 字段 |
| 修改 | `ACPEventMapper.java` — 新增 Claude stream-json 映射 |
| 新增 | `ClaudeACPPlugin.java` |
| 修改 | `PluginRegistry.java` — 注册 ACP 插件 |
| 新增 | `ACPTransportIntegrationTest.java` |
| 新增 | `ClaudeACPEventMapperTest.java` |
| 新增 | `backend/src/main/resources/cli-adapters/claude-acp.yaml` |

**不改动的文件：**
- `CodexPlugin.java` — legacy 路径不动
- `ClaudeCodePlugin.java` — legacy 路径不动
- `GenericCLIPlugin.java` — 已在 M2.5 改为 protected

---

## 五、对比验证计划

每个 ACP 插件上线后，运行对比测试：

```
同一 prompt → 走 codex (legacy)  vs  codex-acp (ACP)
  ↓                                            ↓
stdout 行数 / 事件数 / 错误率            JSON 行数 / 事件数 / 错误率

断言：
1. ACP 路径事件数 ≥ legacy 路径（更多结构化事件）
2. ACP 路径 FILE_CHANGED 覆盖率 > legacy（legacy 无法检测）
3. ACP 路径执行成功率 ≥ legacy
4. ACP 路径无额外超时风险
```

---

## 六、风险与约束

| 风险 | 缓解措施 |
|------|---------|
| Claude `--output-format=stream-json` 需要 `--verbose` | 已在配置中添加 |
| Claude ACP 事件格式可能变化 | ACPEventMapper 有 default 分支兜底 |
| ACP 路径与 legacy 路径行为不完全一致 | 通过对比测试验证，不一致时优先 legacy |
| 现有 336 个测试不能破坏 | 所有改动不触碰现有 Plugin 类 |
| Codex/Claude 版本差异导致 ACP 格式不同 | transport 字段控制，legacy 不受影响 |

---

## 七、后续演进（M6+）

当 Rust Runtime 迁移到 M6 时，ACP Client 可以直接用 Rust SDK：

```
Tauri App
  ↓
Rust Runtime (M6)
  ├── Task State Machine
  ├── Capability Router
  ├── Evidence Collector
  └── ACP Client (rust-sdk)
         ↓
      Codex / Claude / Aider
```

这样 M2.5 的 Java 层 ACPEventMapper 可以被 Rust 层的事件处理器替代，
但 **接口语义完全对齐**（same TeamMind Event types）。

---

*方案设计：Agnes*
*日期：2026-08-18*
*关联：M2.5 ACP POC → M6 ACP Rust Runtime*
