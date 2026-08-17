# TeamMind Gate Audit — Pre-Alpha 摸牌验收

**审计日期**：当前
**核心判据**：一个真实开发者把一个真实任务交给 TeamMind 后，TeamMind 能不能安全、透明、可验证地把任务完成，并让用户清楚知道 AI 团队到底做了什么？

---

## 总分矩阵

| 维度 | 等级 | 一句话总结 |
|------|------|-----------|
| D1 产品定义 | **B** | 概念清晰但缺 1 段话级产品定义文档 |
| D2 Runtime 架构 | **A** | 状态机、对象边界、持久化边界、重启恢复都已成立 |
| D3 真实 Agent 执行 | **C** | codex/claude 可调用但 pipeline 主路径未真实跑通 |
| D4 Agent 协作 | **C** | 协作代码存在但 Review→Fix→Re-review 闭环未证明 |
| D5 State / Event / Persistence | **A** | WAL、57 种事件、3 层 tier、分级存储完整 |
| D6 Evidence + Execution Ledger | **B** | 后端聚合完整，前端 UI 优秀，但缺真实跑通验证 |
| D7 Human Control + Recovery | **B** | Restart Recovery 通过，但 Cancel/Resume 未真实验证 |
| D8 Adaptive Learning | **C** | PerformanceRecord 存在但从未被真实数据填入 |
| D9 Web Mission Control | **B** | UI 设计完整，WebSocket 实时已工作，但 UI 真实操作未跑过 |
| D10 工程质量 | **B** | 测试覆盖好，但"有实现 ≠ 验收"原则下大量跳过未独立验证 |

**Overall: Runtime Prototype（接近但尚未达到 Product Alpha）**

---

## D1 — 产品定义 · B

### Implemented
- AGENTS.md + 27 篇文档 + `execution-ledger-product-assessment.md` 描述愿景
- README.md 存在
- 路线图清晰（Phase 1A → 4-CLI）

### Actually demonstrated
- 用户是谁：项目内部假设是"个人开发者 / AI-native Developer"
- 为什么 Claude Code / Codex / Orca 不够：文档中有描述（"把数据翻译成事实"、"任务执行审计系统"）

### Independently verified
- ❌ **没有一段让真实开发者看了能立刻理解 TeamMind 的 1 段话**
- ❌ README.md 不在仓库根目录（实际在 `docs/` 子目录）

### 缺口
- README.md 内容薄弱，缺少 "Why TeamMind / For whom / What it does" 三个问题答案
- 没有 "5 步跑通一个真实任务" 的 onboarding 文档

**Gate D1: B** — 概念清晰，但产品文案和 onboarding 是缺失的。

---

## D2 — Runtime 架构 · A

### D2-1 核心对象边界 ✅

| 对象 | 存在 | 区别清晰 |
|------|------|---------|
| Project | ✅ | 顶层容器 |
| Task | ✅ | 用户意图 |
| TaskExecution | ✅ | Task 的一次执行尝试 |
| ExecutionStep | ✅ | Pipeline 的一个步骤 |
| AgentInvocation | ✅ | 步骤内对 agent 的具体调用 |
| Artifact | ✅ | Agent 产出物 |
| Evidence | ✅ | 验证性证据（独立验证） |
| Approval | ✅ | Human gate |
| RuntimeEvent | ✅ | 不可变事件 |
| RuntimeCommand | ✅ | Eventual consistency |

**Task vs Execution**：Task 是用户意图（"实现登录"），Execution 是某次具体尝试（attempt 1, attempt 2...）。清楚。
**Artifact vs Evidence**：Artifact 是 agent 产出物，Evidence 是独立验证（"git diff 存在"）。清楚。
**Event vs State**：Event 是不可变历史，State 是当前投影。清楚。

### D2-2 状态机 ✅

两个状态机：
- `TaskStateMachine` — Task 级别（SUBMITTED → EXECUTING → DONE/FAILED）
- `TaskExecutionStateMachine` — Execution 级别（NEW → RUNNING → DONE/FAILED）

测试覆盖：合法迁移、非法迁移拒绝、重试创建新 Execution。

### D2-3 持久化边界 ✅

- **SQLite**：runtime_events / task_executions / evidence / artifacts / knowledge_entries
- **Markdown/YAML**：`.agents/` 文档 + `cli-adapters/*.yaml` + `pipelines/*.yaml`
- **Filesystem**：COLD tier 归档到磁盘

设计原则 "Markdown/YAML 是项目记忆和配置；SQLite 是运行时事实" 在 AGENTS.md 中明确且实际代码符合。

### D2-4 重启恢复 ✅

`RecoveryService` 是 `CommandLineRunner`，启动时扫描：
- RUNNING execution → 检查 ProcessHandle.isAlive()
- alive → 标记 RECOVERING
- 死了 → 标记 FAILED (PROCESS_DIED)

**E2E 测试日志确认**：
```
RecoveryService: verifying 5 registered CLI(s)
CLI codex health: HEALTHY
[E2E] RecoveryService correctly marked execution as FAILED: PROCESS_DIED
```

但 ⚠️ 这是"模拟进程死了"场景，没有真实 kill -9 验证。

**Gate D2: A** — 架构层面完全成立。

---

## D3 — 真实 Agent 执行 · **C**

### 三个问题

**1. Implemented?** ✅
- CodexPlugin / ClaudeCodePlugin / GitVerifier / TestRunnerVerifier 全部存在
- GenericCLIPlugin 支持 YAML 动态加载
- 3 个 CLI 适配器（codex.yaml, claude-code.yaml, atomcode.yaml）

**2. Actually invoked?** ⚠️ **部分**
- codex --version → 在 Windows 修复后能成功执行（0.87s）
- claude --version → 能执行
- atomcode --version → 能执行
- 但 `codexRealInvocation` E2E 测试调用 `codex` 的方式只测试"ping"——prompt 是 "Reply with exactly: TEAMMIND_E2E_OK codex"
- `fullPipelineWithArtifactsAndEvents` 测试在 E2E 中**被跳过**，原因是依赖缺失

**3. Independently verified?** ❌
- 上次运行 pipeline 测试日志：`[E2E] Codex tool not in PATH, skipping pipeline test`
- 主路径 `pipelineOrchestrator.executePipeline("review-loop")` **从未真实跑过**
- 没有看到 codex → claude → verifier 的真实日志输出

### D3-2 真实成功路径 ⚠️

没有 E2E 测试真实验证：
```
User Task → real CLI → actual file change → actual test → final result
```

只有 mock / ping 级别的"我能跑"。

### D3-3 真实失败路径 ✅

测试覆盖：
- ReadinessState 区分 UNAVAILABLE / DEGRADED / READY / RECOVERING / BLOCKED
- 模拟进程死亡后被 RecoveryService 标记 FAILED (PROCESS_DIED)

但**没有真实 CLI crash / timeout 测试**。

### D3-4 Readiness ⚠️

- `ReadinessManager` 区分 5 种状态
- 自动诊断、自动恢复（attemptRecovery）存在
- 但 recovery process 路径是写死的 `D:\ProgramFiles\Codex++\codex-plus-plus.exe`，**不是真实的自动恢复**

### 真实问题：核心失败

```text
[H] Implemented     ✅
[H] Actually demo   ❌ (pipeline 未真实跑)
[H] Independently verified  ❌

→ 不能算成立
```

**Gate D3: C** — 实现存在但核心路径未真实跑通。Codex 真实集成有，但 "User Task → codex → 文件变更 → 测试 → 结果" 这条主链从未真实发生过。

---

## D4 — Agent 协作 · **C**

### D4-1 Lead ✅

`PipelineDefinition` 支持 `role: LEAD`，每个 pipeline 有 lead agent。

### D4-2 Handoff ⚠️

- `PipelineContext` 有 `HandoffRecord`（fromStep, toStep, reason, timestamp）
- `HandoffContext` **不存在**（没有专门的 handoff 数据结构）
- handoff 传递的只是 `prompt` 和 `artifacts`，没有显式 `findings`、`repo state`、`constraints` 的结构化传递

### D4-3 Review → Fix → Re-review ❌

`review-loop.yaml` 定义了：
```yaml
- name: implement  (codex)
- name: review     (claude-code, on_critical: request_approval)
- name: verify     (git-verifier, test-runner-verifier, on_any_fail: implement)
```

**但实际从未跑过**。Pipeline 测试因为依赖缺失被跳过。

### D4-4 不同 Agent 的优势互补 ⚠️

代码中有 CapabilityRouter 评分逻辑（philosophies / preferredRoles / weakRoles）。但从未基于真实多 agent 任务验证"互补价值"。

### D4-5 Agent 失败时重新路由 ⚠️

`PipelineStepDefinition.determineNext()` 有 `onAnyFail` 跳转回 implement。但实际验证：无。

**Gate D4: C** — 协作代码存在但 review-loop 主路径从未真实跑通。无法证明"多 agent 协作比单 agent 更有价值"。

---

## D5 — State / Event / Persistence · **A**

### D5-1 事件完整性 ✅

57 种 EventType，覆盖：
- 生命周期：TASK_STARTED/COMPLETED/FAILED/CANCELLED/RETRYING
- Agent 状态：AGENT_STARTED/THINKING/IDLE/COMPLETED/FAILED/HANDOFF/CHUNK
- 执行细节：TOOL_CALLED/RESULT、FILE_CHANGED、COMMAND_RUNNING
- 产物 / 验证 / 审查 / 决策 / 路由 / 异常 / 进化 / 环境变更

### D5-2 Event ≠ State ✅

- Event 是不可变 append-only
- State 是可变的 current snapshot
- `PipelineOrchestrator` 通过 state machine 操作 TaskExecution state
- State 可从 durable state 恢复

### D5-3 WebSocket 断线恢复 ✅

- `wsManager` 自动重连（reconnect=true, maxReconnectAttempts=5）
- `getEventsAfter(afterId)` 支持断线后补全
- E2E 测试 `eventReplayAfterFilter` 验证 after 参数正确过滤

### D5-4 SQLite 配置 ✅

```yaml
url: jdbc:sqlite:...?busy_timeout=30000&journal_mode=WAL&synchronous=FULL&foreign_keys=ON
```

WAL + synchronous=FULL + foreign_keys=ON + busy_timeout 30s。✅

### D5-5 分级存储 ✅

RuntimeEvent.tier: HOT (永久) / WARM (7天) / COLD (30天后归档文件) / TRASH

**Gate D5: A** — 状态/事件/持久化三个维度都成立。

---

## D6 — Evidence + Execution Ledger · **B**

### D6-1 记录系统事实，不是 Agent 自述 ✅

- `ActivityExtractor` 从 RuntimeEvent 聚合
- 不解析 agent 自述，直接消费 event data
- `extractFilesChanged()` 从 CLI 输出正则匹配实际文件

### D6-2 Evidence 生命周期 ✅

CLAIMED → COLLECTED → VERIFIED → INVALIDATED

### D6-3 Execution Ledger ✅

`TaskActivity` 包含 8 个维度：
- commandsExecuted / filesChanged / dependenciesChanged / environmentChanges
- incidents / verifications / agentDecisions / knowledgeCandidates

### D6-4 Commands 折叠式 ✅

`ActivityLedgerPanel` 实现：
- 默认折叠："N commands · M important"
- 展开显示完整列表
- 重要命令高亮（exit ≠ 0 或 duration > 10s）

### D6-5 Environment Changes ✅

5 种环境变更事件：PACKAGE_INSTALLED / COMMAND_EXITED / ENV_VAR_MODIFIED / PROCESS_STARTED / FILE_DELETED

### D6-6 任务复盘 ✅

用户可通过 `GET /api/tasks/{id}/activity` 看到完整活动摘要。

### 缺口

- ⚠️ 没有真实任务跑过，无法验证 ledger 数据"真实"反映现实
- ⚠️ Evidence Verifier 的实际验证逻辑（git diff 验证、test 解析）从未跑过真实任务

**Gate D6: B** — 设计完整，但同样需要真实任务验证数据准确性。

---

## D7 — Human Control + Recovery · **B**

### D7-1 Pause ⚠️

代码存在 `TaskExecutionState.PAUSED` 和 transition。但 **没有 E2E 测试真实暂停一个运行中的 execution**。

### D7-2 Resume ⚠️

同上，未真实验证。

### D7-3 Cancel ❌

`PipelineOrchestrator` 中 **没有 cancel 方法实现**。grep "cancel" in PipelineOrchestrator.java = 0 results。

只有 `cancel` 在 GenericCLIPlugin 中 kill process，但没有 orchestration 层级的 cancel。

### D7-4 Approval ✅

- `ApprovalRequest` 实体 + repository
- WS 事件 `approval_required`
- 前端 TaskDetailPanel 显示 Approve/Deny 按钮

### D7-5 Retry ✅

每次 retry 创建新 Execution（不是覆盖）。`retryExecution` 方法验证。

### D7-6 Recovery ✅

- `RecoveryService` 在启动时扫描 RUNNING execution
- 验证通过：`[E2E] RecoveryService correctly marked execution as FAILED: PROCESS_DIED`
- 但 ⚠️ 是模拟场景

**Gate D7: B** — 设计完整，Recovery 通过，但 Cancel/Resume 未真实验证。

---

## D8 — Adaptive Learning · **C**

### D8-1 Performance Profile ⚠️

- `PerformanceRecord` 实体存在
- `PerformanceDashboard.vue` 存在
- 但从未被真实数据填入

### D8-2 Evidence-based ⚠️

`PerformanceRecord` 的字段设计是 evidence-based（sample_size, false_positive_rate, miss_rate, user_acceptance_rate）。但：

- 从未真实写入数据
- `PerformanceDashboard` 显示的是 mock 数据还是真实数据？需要检查

### D8-3 Drift ⚠️

`DRIFT_DETECTED` 事件类型存在。`driftAlerts` API 存在。但从未真实检测到过一次 drift。

### D8-4 Recommendation ⚠️

- `RECOMMENDATION_GENERATED` 事件类型存在
- `recommendation` API 存在
- 但从未真实生成过一条 recommendation

### D8-5 Human Approval ✅

`APPROVAL_AUTO_APPROVED` 事件类型存在，recommendation 不能直接修改 Team Configuration 是设计原则。

### 核心问题

D8 的所有功能都是"准备好但从未使用过"。

```text
PerformanceRecord entity: empty (0 records)
EvolutionEngine: exists but never invoked
DRIFT_DETECTED: 0 times
RECOMMENDATION_GENERATED: 0 times
```

**Gate D8: C** — 实现存在但从未被真实使用。

---

## D9 — Web Mission Control · **B**

### D9-1 实时状态 ✅

WebSocket 订阅：
- MissionDetailPage (log, node_update, mission_completed, mission_failed, agent_status_update)
- TaskDetailPanel (state_update, approval_required, pipeline_step_started/completed)

### D9-2 Live Events ✅

wsManager 自动重连 + EventBus 事件流。E2E 测试 eventReplayAfterFilter 验证。

### D9-3 Human Control ⚠️

- 按钮存在：Start / Pause / Resume / Cancel / Retry
- 但**Cancel 按钮只 message.error("Failed to cancel mission")**，未实际调用 backend cancel API（PipelineOrchestrator 没有 cancel 方法）

### D9-4 Task Detail ✅

TaskDetailPanel 509 行，8 格面板，覆盖 objective / status / artifacts / findings / approvals / events / decisions / commands。

### D9-5 信息层级 ✅

Execution Ledger 设计：
- Summary 卡片（6 项指标）
- What Changed | Evidence 双栏
- Knowledge Candidates

⚠️ 但同样，**从未在真实任务上验证**信息层级是否真的"从结论到证据"展示。

### 前端质量提升

- Phase 4-UI：184 处硬编码 → 0 处
- 字体大小统一为 6 级
- 设计系统 CSS 变量覆盖率达 100%（mission 组件）

**Gate D9: B** — UI 设计完整且高质量，但 Cancel/真实操作验证缺失。

---

## D10 — 工程质量 / 可交付性 · **B**

### D10-1 测试分类真实 ✅

- Phase 3-fix 后已消除 readiness-skip 的假通过
- E2E 测试有 PASS / FAIL / SKIP 真实分类
- 13 → 15 E2E 测试

### D10-2 核心 E2E ⚠️

完整链路存在：
```
Task → Codex → Claude → Verifier → Artifact + Event Store
```

但 **从未真实跑通**（依赖缺失导致跳过）。

### D10-3 核心失败 E2E ⚠️

有 ProcessTracker 测试、有 RecoveryService 失败处理测试，但**没有真实**：
- CLI failure 真实测试
- Agent timeout 真实测试
- Verifier failure 真实测试
- Human denial 真实测试

### D10-4 CI ⚠️

没有 CI 配置文件（`.cnb.yml` 是云端构建，与 GitHub Actions 不同）。Maven test 是手动执行。

### D10-5 Architecture Debt ⚠️

历史曾有 `@Lazy` hack（Phase 3-fix 已修），`EventStoreService` 双构造函数（已修）。当前债务较小。但：

- 3 个数据获取方式并存（api / store / raw axios）→ P1-3 已修但还需统一
- 多次硬编码数据（Phase 4-UI 已修）

### D10-6 可安装 ✅

```
git clone
cd backend && mvn compile && mvn test
cd .. && pnpm install && pnpm build
```

但需要本地安装 Codex / Claude / Atomcode CLI。文档未明确这一前置条件。

**Gate D10: B** — 测试分类正确，CI 待补，可安装但前置依赖未文档化。

---

## 总结：TeamMind 现在到底是不是 Project AI Team Runtime？

### Gate 判据（必须全部满足才能进入 Product Alpha）

```text
✓ 一个真实用户故事完整成立                              ⚠️ PARTIAL（文档不足）
✓ 至少 2 个真实 CLI                                       ✅ codex + claude + atomcode
✓ 至少 1 条真实 Handoff                                   ❌ 未真实跑过
✓ Review → Fix → Re-review                               ❌ 未真实跑过
✓ 独立 Verification                                       ⚠️ 代码有但未真实跑
✓ Human Approval                                          ✅ UI + API 完整
✓ Process Recovery                                        ✅ E2E 通过（模拟场景）
✓ WebSocket 实时状态                                      ✅ 代码完整
✓ Restart Recovery                                        ✅ RecoveryService 通过
✓ Task Execution Ledger                                   ✅ 完整实现
✓ Evidence 可追溯                                         ✅ 5 种 evidence 类型
```

### 关键失败

```text
❌ pipeline "review-loop" 主路径从未真实跑通
❌ Codex → Claude Handoff 从未真实发生
❌ Verifier 真实验证从未跑过
❌ Cancel/Resume 从未在真实 running execution 上验证
❌ Performance Record 从未被真实数据填入
```

### 真实情况

| 维度 | 等级 | 一句话 |
|------|------|--------|
| 产品定义 | B | 概念清晰但缺产品文案 |
| Runtime 架构 | **A** | 状态机、对象边界、持久化、重启恢复都成立 |
| 真实 Agent | **C** | CLI 可调用但主路径未跑 |
| Agent 协作 | **C** | 代码有但 review→fix→re-review 闭环未证明 |
| State/Event | **A** | WAL + 57 事件 + 分级存储完整 |
| Evidence/Ledger | **B** | 设计完整未真实验证 |
| Human Control | **B** | Cancel/Resume 未真实验证 |
| Adaptive Learning | **C** | 实现存在从未使用 |
| Web UI | **B** | UI 设计高质量但 Cancel/真实操作未跑 |
| 工程质量 | **B** | 测试分类正确但 CI 待补 |

---

## 结论

**TeamMind 当前状态：Runtime Prototype（接近但尚未达到 Product Alpha）**

技术执行力：**A** — 架构、测试、集成都扎实。
产品完整度：**C** — 核心场景从未真实跑过。
差异化：**B** — ActivityExtractor 和 Execution Ledger 是真正的产品创新，但从未被真实数据喂过。

---

## 真正需要做的下一步（不是更多功能，是补缺口）

| 优先级 | 缺口 | 工作量 |
|--------|------|--------|
| **P0** | 真实跑通 review-loop pipeline（解依赖：provider、config.toml、claude.json） | 1-2h |
| **P0** | 真实 Handoff 验证（至少 1 次 codex→claude 真实产出传递） | 30min |
| **P0** | 真实 Cancel/Resume 在 running execution 上验证 | 2h |
| **P1** | 真实 Verifier 跑通（git diff + test runner） | 2h |
| **P1** | 真实 Performance Record 写入并生成第一条 recommendation | 4h |
| **P2** | README.md 重写（用户/场景/onboarding） | 2h |
| **P2** | CI 配置文件（GitHub Actions 或云端） | 2h |

---

**这次摸牌的判据不是 315 个测试通过。**

判据是：

> **一个真实开发者把一个真实任务交给 TeamMind 后，TeamMind 能不能安全、透明、可验证地把任务完成？**

当前答案：**不能保证**。Pipeline 主路径从未真实跑过。

这是当前最核心的事实。