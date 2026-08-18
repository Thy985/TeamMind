# TeamMind AGENTS — 开发协议与约束

## Git 提交身份

所有 git commit 必须使用以下身份信息：

```
user.name  = Thy985
user.email = 1850833838@qq.com
```

此配置已通过 `git config --local` 设置，不需要每次手动指定。

> **原因：** TeamMind 是一个 AI Agent 协作系统，提交身份代表系统的"化身"。Thy985 是项目统一的作者标识，区分人类开发者与 Agent 自动生成的代码。

---

## Agent 分工

| Agent | 角色 | 工作目录 | 分支 |
|-------|------|---------|------|
| Agnes (本会话) | 实施 + 文档 | `D:\Projects\Active\TeamMind` (main) | `main` |
| Codex | 运行时实现 | `D:\DevWorkspaces\teammind-workspace\teammind-wt-codex` | `w7-codex-runtime` |
| Claude Code | 架构审查 | `D:\DevWorkspaces\teammind-workspace\teammind-wt-claude` | `w7-claude-review` |

## 工作流

1. **Codex** 在 worktree 中实现 → 提交到 `w7-codex-runtime`
2. **Claude Code** 审查 diff → 输出 review 到 `.agents/handoffs/`
3. **Agnes** 修复问题 → 合并到 `main`
4. **全量测试通过** → 才算完成

## 技术栈约束

- Java 17, Spring Boot 3.3.7, Lombok, JPA, SQLite
- Vue 3 + TypeScript + Naive UI (frontend)
- SnakeYAML (pipeline 定义)
- Maven (构建), pnpm (前端)

## Strangler Fig Migration（进行中）

> **目标：平滑演化到 Tauri + Vue 3 + Rust Runtime + SQLite，不一次性重写。**
>
> 完整路线图：[docs/migration/roadmap.md](docs/migration/roadmap.md)
> Contract 定义：[docs/contracts/runtime-contract.md](docs/contracts/runtime-contract.md)

| Phase | 目标 | 状态 |
|-------|------|------|
| **M0** | 冻结 Runtime Contract | ✅ 完成 — `docs/contracts/runtime-contract.md` |
| **M1** | Tauri Host（双模式共存） | ✅ 完成 — `tauri-app/` scaffold + HostAdapter |
| **M2** | Process Supervisor → Rust | ✅ 完成 — Java 接口 + Rust impl（`w7-codex-runtime`） |
| **M2.5** | ACP Event Mapper POC | ✅ 完成 — ACPEventMapper + AgentTransport 抽象层 + 25 tests |
| **M3** | Project Execution Model → Rust | ⏳ |
| **M4** | Execution Ledger (Task-level accountability) | ⏳ |
| **M5** | Independent Verification (Reviewer ≠ Verifier ≠ Executor) | ⏳ |
| **M6** | Role / Performance Profile | ⏳ |
| **M7** | Project Team Evolution | ⏳ |
| **M8** | Recovery/Human Control → Rust | ⏳ |
| **M9** | Kill Switch（Java → legacy） | ⏳ |
| **M10** | Remove Spring Boot | ⏳ |

> **基础设施兼容性层（非核心差异化）：** ACP / Plugin / Workspace / Sandbox / Memory / Multi-Agent / Approval / Context / Mission Loop — 按需提供 Provider/Adapter，不重复造轮子。参考：[docs/research/qwenpaw-comparison.md](docs/research/qwenpaw-comparison.md)

**核心原则：**
- 不迁移代码，迁移能力
- 不替换框架，替换 Provider
- 不重写系统，让旧系统逐渐失去存在的理由
- Runtime Contract v1 是 Java 和 Rust 的共同契约

## 存储架构原则

> **Markdown/YAML 是项目记忆和配置；SQLite 是运行时事实。**

- `.agents/` — agent 任务、交接文档（Markdown）
- `backend/src/main/resources/` — YAML pipeline 定义
- `backend/src/main/resources/db/migration/` — SQLite schema
- `backend/target/` — 构建产物（不入库）
- `.cache/` — 运行时缓存

## 当前状态

- **Phase 1A** ✅ Runtime Contract (commit `a0f458fd`)
- **Phase 1B** ✅ Single-Agent Runtime (commit `7db54c0f`)
- **Phase 1C** ✅ Multi-Agent + Event Store + Mission Control (commits `39eb543f` → `5deec127`)
- **Phase 2** ✅ Mission Control live integration (commits `afba917c` → `c28bef85`)
- **Phase 3** ✅ CLI Platform + Recovery Hardening + Real Pipeline (commits `e161c5b2` → `25e4a373`)
- **Phase 3-fix** ✅ E2E readiness-skip eliminated
- **Phase 4-Sprint 1** ✅ Activity Extractor Engine — Execution Ledger 核心聚合引擎
- **Phase 4-Fix** ✅ Frontend audit fixes — TaskDetailPanel 动态 taskId / Execution Ledger Tab / CLI Health Modal / HistoryPage real API
- **Phase 4-Sprint 2** ✅ Evidence extension — PACKAGE_INSTALLED / COMMAND_EXITED / ENV_VAR_MODIFIED / PROCESS_STARTED / FILE_DELETED
- **Phase 4-P0** ✅ Eliminate hardcoded fake data, dead TODO buttons, WebSocket hack
- **Phase 4-P1** ✅ MissionDetail Ledger tab / pipeline WS events / unified API layer
- **Phase 4-Sprint 3** ✅ Ledger UI — command folding + Knowledge Candidates + dual-column layout
- **Phase 4-Sprint 4** ✅ Knowledge Promotion — ADR/Lesson persistence + noise filtering
- **Phase 4-UI** ✅ Design system enforcement — hardcoded colors/fonts → CSS variables (184→0, 122→0)
- **Phase 4-CLI** ✅ Activate atomcode + fix pipeline data flow + E2E assertions (15 tests)
- **Gate Audit Pre-Alpha** ✅ Honest 10-dimension audit (commit `948a4e37`)
- **P0 Fix** ✅ Codex non-interactive mode + Cancel/Resume implementation (commit `b1a16ba4`)
- **P0 Fix** ✅ Real Handoff Verification — 6 tests pass, handoff chain verified (commit `2513f088`)
- **P0 Fix** ✅ Real CLI Integration — Claude Code verified (commit `a872f838`)
- **P0 Fix** ✅ Atomcode CLI verified — success=true, 4.5s (commit `2bee0b33`)
- **P0 Fix** ✅ Codex stdin issue fixed — direct node call bypasses PowerShell wrapper (commit `67ce21d0`)
- **Gate A Real Execution** ✅ Headless Runtime + EventPublisher SPI + 4 new E2E tests — 150 pass, 0 failures (commit `64a906a8`)

## 测试基线

- 后端单元测试：**336 pass, 0 failures**
- 后端 E2E 集成测试：**13 pass, 0 failures**（E2EIntegrationTest）
- 全量测试：**336 pass, 0 failures**
- 前端构建：`✓ built in 3.31s (4357 modules)`
- E2E 真实集成：Codex CLI ✓、Claude Code CLI ✓、Atomcode CLI ✓
- 全量测试：**336 pass, 0 failures**（+2 atomcode E2E）
- Gate A E2E: RealE2EIntegrationTest 8 pass ✅、HeadlessRuntimeE2ETest 5 pass ✅、FailureScenarioE2ETest 5 pass ✅（1 skipped）、GateARealExecutionTest 5 pass ✅（4 skipped）
