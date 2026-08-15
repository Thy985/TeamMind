# TeamMind CLI Adapter Protocol Spec (v0.1 Draft)

> 本文档定义 TeamMind 与本地 AI Agent CLI 的适配协议。
> 
> 目标：让任何 AI Agent CLI（Claude Code / Codex / Gemini CLI / OpenCode / Aider / ...）都能被快速接入 TeamMind 编排控制台。
> 
> 状态：草案 v0.1 —— 欢迎在 GitHub Issue 提意见。

---

## 一、设计原则

| 原则 | 说明 |
|---|---|
| **零适配成本** | CLI 是模型天生就会用的东西，不需要解析对方输出 |
| **可靠率 100%** | 直接通过子进程 stdin/stdout/stderr 与 CLI 通信 |
| **流式优先** | stdout 必须流式推送，不等完整结果 |
| **进程隔离** | 每个 CLI 调用独立子进程，避免互相干扰 |
| **可中断** | 用户可随时停止一个 CLI 的执行 |

---

## 二、CLI 探测（Discovery）

TeamMind 启动时自动扫描用户 PATH 上已安装的 Agent CLI。

### 探测规则

每个 CLI 用一个 **Adapter** 描述：

```yaml
# adapters/opencode.yaml
id: opencode
name: OpenCode
description: 全球星标最高的开源 AI Agent CLI
binary: opencode          # 可执行文件名（which/where 查找）
homepage: https://github.com/opencode-ai/opencode
version_args: ["--version"]
install_hint: |
  macOS:   brew install opencode
  Linux:   curl -fsSL https://opencode.ai/install | bash
  Windows: scoop install opencode
```

### 探测流程

```
1. TeamMind 启动 → 读取 bundled adapters/*.yaml
2. 对每个 adapter，调用 `which <binary>`（Windows: `where`）
3. 找到 → 调用 `<binary> --version` 获取版本号
4. 找不到 → 该 adapter 标记为 "not installed"，但保留入口让用户安装
5. 探测结果存入 SQLite 缓存
```

### 内置 Adapter 清单（首批 5 个）

| ID | Binary | 状态 |
|---|---|---|
| `opencode` | `opencode` | 19.5 万 Stars，优先适配 |
| `claude-code` | `claude` | 14.1 万 Stars |
| `codex` | `codex` | 10.5 万 Stars |
| `gemini-cli` | `gemini` | 10.5 万 Stars |
| `aider` | `aider` | 4.8 万 Stars |

---

## 三、CLI 调用（Invocation）

### 基本调用

TeamMind 通过子进程方式调用 CLI：

```java
ProcessBuilder pb = new ProcessBuilder("claude", "--print", "--output-format", "stream-json");
pb.directory(workDir);
pb.redirectErrorStream(false); // stderr 单独处理
Process proc = pb.start();
```

### 输入模式

每个 CLI 接受任务的方式不同：

| CLI | 输入方式 | Adapter 配置 |
|---|---|---|
| Claude Code | 命令行参数 + stdin | `input_mode: "args+stdin"` |
| Codex CLI | 命令行参数 | `input_mode: "args"` |
| Gemini CLI | 命令行参数 + stdin | `input_mode: "args+stdin"` |
| OpenCode | 命令行参数 | `input_mode: "args"` |
| Aider | 命令行参数 + 文件 | `input_mode: "args+files"` |

### 输出格式

TeamMind 期望 CLI 输出为 **流式文本**（每行一次 stdout 写出）。如果 CLI 输出 JSON 行（推荐），Adapter 解析为：

```json
{"type":"text","content":"..."}
{"type":"tool_call","name":"...","args":{...}}
{"type":"tool_result","name":"...","result":{...}}
{"type":"done","exit_code":0}
```

**关键约束**：TeamMind **不解析 LLM 输出内容**，只关心 type 字段用于前端展示。

---

## 四、流式推送（Streaming）

### 协议

TeamMind 通过 WebSocket (STOMP) 将每个 CLI 的 stdout 推送到前端：

```
Topic: /topic/cli/{cliId}
Message: {
  "cli_id": "opencode",
  "session_id": "uuid",
  "type": "stdout" | "stderr" | "exit",
  "content": "...",
  "timestamp": "2026-08-14T..."
}
```

### 前端订阅

```javascript
ws.subscribe('/topic/cli/opencode', (msg) => {
  if (msg.type === 'stdout') appendOutput(msg.content);
  if (msg.type === 'exit') markDone(msg.exit_code);
});
```

---

## 五、工作流编排（Orchestration）

### 5.1 单 Agent 调用

用户在 Web UI 选择一个 CLI + 输入任务：

```
┌─────────────────────────────────────┐
│ [OpenCode ▼]  [任务描述...]  [Run]   │
└─────────────────────────────────────┘
```

TeamMind 在后端启动对应的 CLI 子进程，stdout 实时推到前端。

### 5.2 多 Agent 编排（核心差异化）

用户拖拽多个 CLI 到画布，定义执行顺序：

```
┌─────────┐    ┌─────────┐    ┌─────────┐
│OpenCode │ →  │ Claude  │ →  │  Codex  │
│(写代码) │    │(审查)   │    │(测试)   │
└─────────┘    └─────────┘    └─────────┘
```

执行流程：
1. Node 1（OpenCode）启动，用户任务 + 输出文件
2. Node 1 完成 → 输出作为 Node 2（Claude Code）的输入上下文
3. Node 2 完成 → 输出作为 Node 3（Codex）的输入上下文
4. ...

每个 Node 的状态（running / success / failed）实时推到前端，画布上显示。

### 5.3 数据传递协议

```
Node 1 完成时：
{
  "node_id": "opencode-1",
  "output_files": ["/path/to/code.py"],
  "output_text": "...",
  "exit_code": 0
}

Node 2 输入构造：
{
  "cli": "claude",
  "prompt": "<原任务> + <Node 1 输出>",
  "files": ["/path/to/code.py"],
  "context": { "previous_node": "opencode-1" }
}
```

---

## 六、错误处理

| 错误 | 处理 |
|---|---|
| CLI 未安装 | 前端提示用户安装，提供 install_hint |
| CLI 启动失败 | 标记 Node 为 "failed"，WebSocket 推送错误 |
| CLI 超时 | 默认 5 分钟超时，可在 Adapter 配置中调整 |
| 用户取消 | 发送 SIGTERM，5 秒后 SIGKILL |
| 输出格式异常 | 流式透传原始内容，不解析 |

---

## 七、安全约束

- **进程隔离**：每个 CLI 调用在独立进程；崩溃不影响 TeamMind
- **超时保护**：所有 CLI 调用强制超时
- **工作目录限制**：CLI 在用户指定的工作目录执行，不逃逸到 TeamMind 安装目录
- **审计日志**：所有 CLI 调用记录到 SQLite（CLI / 参数 / 输出 / 退出码 / 时间戳）

---

## 八、贡献指南

### 如何添加新 Adapter

1. Fork TeamMind 仓库
2. 在 `backend/src/main/resources/adapters/<id>.yaml` 添加 Adapter 描述
3. 在 `backend/src/main/java/com/teammind/cli/adapters/<Id>Adapter.java` 实现 CLI 特定逻辑（如果默认 stdin/stdout 不够用）
4. 添加单元测试
5. 提 PR，附：
   - 1 张 CLI 输出截图
   - 1 段 30 秒演示视频

### 优先级

| Adapter | 优先级 | 原因 |
|---|---|---|
| OpenCode | P0 | 用户量最大 |
| Claude Code | P0 | 推理质量最高 |
| Codex CLI | P0 | 用户量第二大 |
| Gemini CLI | P0 | 免费 |
| Aider | P1 | Git 工作流 |
| Qwen Code | P1 | 国内用户 |
| 飞书 CLI | P2 | 国内协同 |
| 自定义 | P2 | 通过 PR |

---

## 九、未来扩展

| 想法 | 优先级 |
|---|---|
| Adapter 协议版本协商 | v0.2 |
| CLI 输出 diff 视图 | v0.3 |
| 工作流模板市场 | v0.5 |
| Adapter 远程贡献（动态加载） | v1.0 |
| 跨机器 CLI 调度（SSH） | v1.0 |

---

**Spec 版本**：v0.1
**最后更新**：2026-08-14
**反馈渠道**：https://github.com/yourname/teammind/issues