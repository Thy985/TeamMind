# Strangler Fig Migration — Migration Roadmap

> **不要迁移代码，迁移能力。**
> **不要替换框架，替换 Provider。**
> **不要重写系统，让旧系统逐渐失去存在的理由。**

---

## 概述

| 阶段 | 目标 | 风险 | 预计周期 |
|------|------|------|---------|
| M0 | 冻结 Runtime Contract | 低 | 1 周 |
| M1 | Tauri Host（双模式共存） | 低 | 2 周 |
| M2 | Process Supervisor → Rust | 中 | 1 周 |
| M3 | Provider State + Performance Profile → Rust | 低 | ✅ 完成 |
| M4 | Workspace/Git → Rust | 中 | ✅ 完成 |
| M5 | Event/Streaming → Rust | 中 | ✅ 完成 |
| M6 | State/Persistence → Rust | 高 | 2 周 |
| M6 | Plugin Runtime → Rust | 高 | 2 周 |
| M7 | Orchestrator → Rust | 高 | 2 周 |
| M8 | Recovery/Human Control → Rust | 高 | 1 周 |
| M9 | Kill Switch（Java 降级为 legacy） | 中 | 1 周 |
| M10 | Remove Spring Boot | 低 | 1 周 |

**总周期：约 13 周（3 个月）**

---

## M0：冻结 Runtime Contract ✅（进行中）

**目标：** 把所有隐含在 Java 代码中的语义明确定义成独立文档。

**已完成：**
- [x] `docs/runtime/core-model.md` — 核心数据模型
- [x] `docs/runtime/task-state-machine.md` — 状态机
- [x] `docs/runtime/event-protocol.md` — 事件协议
- [x] `docs/runtime/plugin-system.md` — Plugin 系统
- [x] `docs/architecture/invariants.md` — 架构不变量
- [x] `docs/contracts/runtime-contract.md` — **Runtime Contract v1**（新建）

**下一步：** 补充缺失的 Contract 文档

---

## M1：Tauri Host

**架构：**
```
Tauri App
├── Vue 3 UI（几乎不变）
├── Desktop Integration（tray / native menu）
└── Host Adapter
    ├── Web mode  → HTTP / WebSocket → Spring Boot
    └── Tauri mode → tauri::invoke   → Java Runtime（通过 JNI 或直接 spawn）
```

**关键决策：**
- Tauri 是新 Host，但 Runtime 仍然是 Java
- 前端增加 `HostAdapter` 抽象层，两套模式共存
- Spring Boot 不改动

**验收：**
- [ ] `tauri-app/` 目录初始化
- [ ] Vue UI 通过 Tauri dev mode 可启动
- [ ] 现有所有 API 仍然工作
- [ ] System tray / native menu 可用

---

## M2：Process Supervisor → Rust

**为什么先迁这个？**
这是 Rust 最能体现优势的地方——子进程管理、信号处理、超时控制、进程树清理。

**Java 现有实现：**
- `CLIProcessTracker.java` — PID 跟踪
- `GenericCLIPlugin.java` — ProcessBuilder + stream 处理
- `WindowsCommandHelper.java` — Windows 特殊处理

**Rust 新增：**
```rust
pub trait ProcessSupervisor {
    fn spawn(&self, command: &str, work_dir: &Path, env: &[(&str, &str)]) -> Result<ProcessId>;
    fn cancel(&self, pid: ProcessId) -> Result<()>;        // SIGTERM → wait → SIGKILL
    fn is_alive(&self, pid: ProcessId) -> bool;
    fn wait_exit(&self, pid: ProcessId, timeout: Duration) -> Result<i32>;
    fn read_stdout(&self, pid: ProcessId) -> Result<String>;
    fn read_stderr(&self, pid: ProcessId) -> Result<String>;
}
```

**Feature Flag：** `runtime.process.provider = java | rust`

**验证：** 同一个 Codex 调用，Java 和 Rust 的退出码、stdout、超时行为一致。

---

## M3：Provider State + Performance Profile → Rust ✅

**已完成（commit `85a392e5`）：**
- [x] `ProviderState` 枚举：DISCOVERED/CONFIGURED/STARTING/READY/DEGRADED/UNAVAILABLE/STOPPED
- [x] `ProviderStatus` + `ProviderStateStore`（in-memory，Tauri commands）
- [x] `AgentPerformanceRecord` + `PerformanceStore`（in-memory，Tauri commands）
- [x] Tauri commands: `provider_set_status`, `provider_get_status`, `provider_list_runnable`
- [x] Tauri commands: `perf_record`, `perf_get_by_agent`, `perf_aggregate`

**Java 对应实现已删除（迁移到 Rust）：**
- ~~`ProviderState.java` / `ProviderStatus.java`~~ → 删除
- ~~`AgentPerformanceRecord.java` / `ProjectAgentProfile.java`~~ → 删除
- ~~`*Repository.java`~~ → 删除
- `ReadinessManager.java` → 回滚原始实现（Rust 现在负责）

---

## M4：Workspace / Git → Rust

**Java 现有：** `GitWorktreeService.java`、`WorkspaceManager.java`

**Rust 新增：**
```rust
pub trait WorkspaceManager {
    fn create_worktree(base_path: &Path, branch: &str) -> Result<PathBuf>;
    fn delete_worktree(path: &Path) -> Result<()>;
    fn git_diff(worktree: &Path) -> Result<String>;
    fn git_status(worktree: &Path) -> Result<Vec<StatusEntry>>;
    fn git_commit(worktree: &Path, message: &str) -> Result<String>;
    fn snapshot_state(project: &Path) -> Result<RepoSnapshot>;
}
```

**关键语义：** Executor 和 Reviewer 必须在不同 worktree，不能共享写入权限。

---

## M4：Workspace / Git → Rust ✅

**已完成（commit `3761b4d3`）：**
- [x] `WorkspaceManagerState` — in-memory worktree registry
- [x] `workspace_create` — 为 Executor/Reviewer 创建隔离 worktree
- [x] `workspace_delete` — 清理 worktree
- [x] `workspace_git_status` — git status --porcelain
- [x] `workspace_git_diff` — git diff --stat
- [x] `workspace_commit` — git add -A + commit，返回 SHA
- [x] `workspace_snapshot` — 完整快照（branch + sha + files + status + diff）
- [x] `workspace_list` — 列出所有 managed worktrees
- [x] Windows 兼容（git worktree add + clone fallback）

**关键 invariant 已实现：**
```
Executor worktree: /project/__wt_task-abc123  ← 可写
Reviewer worktree: /project/__wt_review-abc123 ← 独立，不可写
```

---

## M5：Event / Streaming → Rust ✅

**已完成（commit `+M5`）：**
- [x] `RuntimeEvent` — 事件数据结构（eventType + timestamp + taskId + pluginId + metadata）
- [x] `EventBusState` — subscriber-based 事件分发（subscribe/unsubscribe/emit）
- [x] `EventStoreState` — in-memory append + query + replay
- [x] Tauri commands: `event_subscribe`, `event_unsubscribe`, `event_emit`
- [x] Tauri commands: `event_store_find`, `event_store_replay`, `event_store_count`

**已完成的 ACP 事件解析（M2.5 + M5）：**
- Codex: thread.started, turn.started/completed, item.completed
- Claude: system, assistant, result
- Generic: terminal_output, file_change, tool_call, error, permission_request

---

## M6：State/Persistence → Rust

**Java 现有：** JPA + SQLite + Flyway（4 个 migration 文件）

**Rust 新增：**
```rust
pub struct RusqliteStateStore { /* rusqlite + 手动 SQL */ }

impl StateStore for RusqliteStateStore {
    // 实现 TaskStore / ExecutionStore / EventStore / EvidenceStore / ArtifactStore
}
```

**关键：** 不翻译 JPA Entity，按 Runtime Contract 重新设计存储接口。

---

## M6：Plugin Runtime → Rust

**Java 现有：** `PluginManager.java`、`PluginRegistry.java`、`PluginBootstrap.java`

**Rust 新增：**
```rust
pub trait PluginHost {
    fn register(&mut self, plugin: Box<dyn Plugin>);
    fn get(&self, id: &str) -> Option<&dyn Plugin>;
    fn invoke(&self, id: &str, context: PluginContext) -> PluginResult;
    fn health_check(&self, id: &str) -> PluginHealth;
}
```

---

## M7：Orchestrator → Rust

**Java 现有：** `PipelineOrchestrator.java`、`TaskExecutionStateMachine.java`

**Rust 新增：**
```rust
pub struct PipelineOrchestrator { /* 状态机 + 步骤编排 */ }

impl PipelineOrchestrator {
    fn execute(&mut self, task: &Task) -> PipelineExecutionResult;
    fn pause(&mut self, execution_id: &str);
    fn resume(&mut self, execution_id: &str);
    fn cancel(&mut self, execution_id: &str);
}
```

---

## M8：Recovery / Human Control → Rust

**Java 现有：** `RecoveryService.java`、`HumanControlService.java`

**Rust 新增：**
```rust
pub struct RecoveryService {
    // 进程死亡 → 标记 FAILED
    // 服务重启 → 恢复 RECOVERING 状态的 Execution
    // 用户决策 → retry / cancel
}

pub struct HumanControlService {
    // pause / resume / cancel / approve / deny
}
```

---

## M9：Kill Switch

```yaml
# 最终配置
teammind:
  runtime:
    provider: rust    # 强制使用 Rust
  
  legacy:
    enabled: false    # Spring Boot 降级为 legacy mode
```

Spring Boot 只保留几个旧 API 用于数据迁移和验证。

---

## M10：Remove Spring

```
删除：
- backend/src/main/java/com/teammind/controller/
- backend/src/main/java/com/teammind/config/WebConfig.java
- backend/src/main/java/com/teammind/websocket/
- pom.xml 中的 spring-web、spring-boot-starter-websocket、spring-security

保留（仅用于测试兼容性）：
- backend/src/main/java/com/teammind/runtime/  ← Java Provider 作为 reference
- backend/src/test/  ← 现有测试作为 Contract 验证
```

---

## Java Exit Gate 详细标准

```
Rust Runtime 100% 覆盖以下 16 项：

 Execution
 ├─ [ ] Task lifecycle (SUBMITTED → DONE/FAILED)
 ├─ [ ] Pipeline orchestration (multi-step, handoff)
 ├─ [ ] Agent Invocation (Claude / Codex / Atomcode)
 ├─ [ ] Plugin lifecycle (load/unload/health)
 ├─ [ ] Process supervision (spawn/cancel/wait/tree cleanup)
 ├─ [ ] Workspace/Git (worktree/diff/branch/snapshot)
 ├─ [ ] Event stream (50+ event types, tiered storage)
 ├─ [ ] Persistence (SQLite, all tables, Flyway-compatible)
 ├─ [ ] Evidence (COLLECTED → VERIFIED, independent verification)
 ├─ [ ] Execution Ledger (ActivityExtractor, command folding)
 ├─ [ ] Recovery (crash → FAILED, restart → RECOVERING)
 ├─ [ ] Human Control (pause/resume/cancel/retry/approve)
 ├─ [ ] Capability Routing (Readiness 七态机)
 ├─ [ ] Headless execution (no HTTP server needed)
 ├─ [ ] Mission Control (all UI pages functional)
 └─ [ ] Real CLI E2E (Codex ✓ Claude ✓ Atomcode ✓)

同时满足：
 ✓ Java traffic = 0（至少 7 天）
 ✓ Legacy API usage = 0
 ✓ Contract Equivalence Test 100% pass
 ✓ 336+ unit tests pass (Rust 侧等价测试)
```

---

## 双轨 Runtime 验证策略

**Phase M5-M7 期间，两个 Runtime 同时运行：**

```
同一个 Task
    │
    ├── Java Runtime ──▶ state_A, events_A, evidence_A
    │
    └── Rust Runtime ──▶ state_B, events_B, evidence_B
                          │
                          ▼
                   Equivalence Check
                   state_A == state_B?
                   events_A ≈ events_B? (type match, timestamp ±1s)
                   evidence_A == evidence_B?
```

**如果等价检查失败：**
1. 定位差异（state drift / event missing / evidence mismatch）
2. 修复 Rust 实现或 Contract 定义
3. 重新验证
4. **不合并直到 100% 等价**

---

## 立即行动项

1. **M0 收尾**：完善 `docs/contracts/` 下的缺失文档
2. **M1 启动**：创建 `tauri-app/` 目录，初始化 Tauri 项目
3. **Feature Flag 框架**：在 `application.yml` 中添加 `teammind.runtime.provider` 配置项
4. **Interface 提取**：在 Java 中把 `ProcessSupervisor`、`WorkspaceManager` 等定义为独立接口
