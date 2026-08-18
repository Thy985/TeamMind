# TeamMind Architecture Invariants

> 这些不变量是 TeamMind 架构的硬约束。任何 PR 都不得违反。
> 违反任意一条 = 架构退化 = PR 拒绝。

## Invariant 1: Runtime Core 不依赖 Web Host

```
runtime/ 不得 import:
  - org.springframework.web.*
  - com.teammind.controller.*
  - com.teammind.websocket.*
  - jakarta.servlet.*
  - any HTTP / WebSocket class
```

**验证**：`HeadlessRuntimeE2ETest` — 不启动 HTTP Server，Runtime 正常工作。

**违反示例**：在 `PipelineOrchestrator` 中 import `HttpServletRequest`。

---

## Invariant 2: Host 只能依赖 Runtime Core，不反向污染

```
Host 层（controller/, websocket/, config/）可以依赖 runtime/
Runtime 层（runtime/）不得依赖 Host 层
```

**验证**：依赖分析 — `grep -r "import com.teammind.controller" runtime/` 必须返回 0 结果。

**违反示例**：`PipelineOrchestrator` 直接调用 `TaskDetailController`。

---

## Invariant 3: Agent Plugin 不直接修改 Project Task State

```
Plugin.invoke() → PluginResult
PluginResult → PipelineOrchestrator → TaskExecutionStateMachine → Task State

Plugin 不得直接:
  - 修改 Task.entity
  - 修改 TaskExecution.state
  - 调用 TaskRepository.save()
```

**原因**：Agent 是执行单元，不是状态管理者。状态变更必须经过 State Machine，确保状态转移合法、可追溯。

**违反示例**：`CodexPlugin.invoke()` 中直接 `taskRepo.save(task.setState(DONE))`。

---

## Invariant 4: Durable State 是事实来源，WebSocket 只是 Projection

```
SQLite / JPA = Source of Truth
WebSocket Events = Projection (可丢失、可重建)

查询状态 → 读 DB
订阅变化 → WebSocket（但不得作为唯一来源）
```

**验证**：断开 WebSocket 后，`GET /api/tasks/{id}` 仍返回完整状态。

**违反示例**：前端只从 WebSocket 事件累积状态，不调 REST API。

---

## Invariant 5: Reviewer 默认不能修改 Executor Workspace

```
Executor Workspace = /path/to/project
Reviewer Workspace = /path/to/project-review (read-only copy or git worktree)

Reviewer 的 PluginContext.projectPath ≠ Executor 的 projectPath
```

**原因**：Reviewer 如果能直接修改 Executor 的代码，就失去了 review 的独立性。

**违反示例**：Review 步骤使用 Executor 的 `projectPath`，Claude 直接修改源文件。

---

## Invariant 6: Agent 自报结果不能直接成为 Verification Evidence

```
Agent says: "I fixed the bug"     → ❌ 不是 Evidence
Git diff shows: +5 -2 lines       → ✅ Evidence (GIT_DIFF)
Test runner: 42 passed, 0 failed  → ✅ Evidence (TEST_EXECUTION)
File exists: hello.txt (120 bytes) → ✅ Evidence (FILE_EXISTENCE)
```

**原因**：Agent 自述不可信（可能幻觉、可能谎报）。Evidence 必须来自独立验证源（Git、FS、Test Runner）。

**违反示例**：`PluginResult.data("tests_passed", true)` 直接写入 Evidence 表。

---

## 验证机制

| Invariant | 验证方式 |
|-----------|---------|
| 1 | `HeadlessRuntimeE2ETest` + import 扫描 |
| 2 | 依赖分析脚本 |
| 3 | Code review + Plugin 接口约束 |
| 4 | 集成测试：断开 WS 后 REST 仍返回完整状态 |
| 5 | Pipeline YAML 检查：review 步骤的 projectPath ≠ implement 步骤 |
| 6 | Evidence 类型检查：只接受枚举类型，不接受 Agent 自由文本 |

---

## Host Profile

| Profile | HTTP | WebSocket | Security | 用途 |
|---------|------|-----------|----------|------|
| `web` (默认) | ✅ 8080 | ✅ | 可选 | 开发/部署 |
| `cli` | ❌ | ❌ | ❌ | CLI Runtime |
| `test` | ❌ | ❌ | ❌ | 测试 |

配置方式：
```yaml
# application.yml
teammind:
  security:
    enabled: ${TEAMMIND_SECURITY_ENABLED:false}

# application-cli.yml
spring:
  main:
    web-application-type: none
teammind:
  security:
    enabled: false
```