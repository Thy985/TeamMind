# TeamMind 修复实施计划与产品交付验收标准

> 文档定位：将上一轮《项目现状分析报告》中识别的 P0/P1/P2 风险转化为可执行、可验收、可追踪的工程交付物。
> 适用范围：当前 `TeamMind` 全栈（前端 Vue 3 + 后端 Spring Boot 3.2 + SQLite）。
> 文档原则：**任何一项"完成"必须能在 5 分钟内被独立验证**（含复现步骤、断言点、日志关键字、SQL 查询）。

---

## 目录

1. [总体策略与原则](#一-总体策略与原则)
2. [工作分解（WBS）总览](#二-工作分解wbs总览)
3. [P0 — 必须立即修复](#三-p0--必须立即修复)
4. [P1 — 建议尽快修复](#四-p1--建议尽快修复)
5. [P2 — 持续打磨](#五-p2--持续打磨)
6. [产品交付验收标准（DoD）](#六-产品交付验收标准dod)
7. [回归测试矩阵](#七-回归测试矩阵)
8. [风险与回滚预案](#八-风险与回滚预案)
9. [附录 A：相关文档索引](#附录-a相关文档索引新建或更新)
10. [附录 B：交付里程碑](#附录-b交付里程碑)
11. [附录 C：依赖版本策略](#附录-c依赖版本策略)

---

## 一、总体策略与原则

### 1.1 工程原则

| 原则 | 说明 |
|---|---|
| **后端先行，前端跟进** | P0 中 6/8 项是后端接口语义/契约修复，前端只在契约稳定后才能对齐。 |
| **小步提交，可回滚** | 每个 Task 拆为 1~3 个原子提交；任何 PR 不破坏 `mvn test` + `vitest run`。 |
| **契约优先** | 引入 `docs/openapi.yaml` 与 `docs/ws-events.md` 作为单一真相源，前端不再"看代码猜接口"。 |
| **事件命名空间收敛** | 后端 STOMP 事件统一为 `snake_case`（`mission_completed`），前端 `WSEventType` 删去冒号变体。 |
| **测试即文档** | 每条验收标准对应至少 1 个自动化测试（JUnit/Vitest/E2E）。 |

### 1.2 分支策略

- `main`：已可演示的稳定基线（当前 HEAD）。
- `remediate/p0-*` / `remediate/p1-*` / `remediate/p2-*`：按优先级开分支，每个 Task 一个 PR。
- `release/vX.Y`：每两周一个集成窗口，跑通全量回归矩阵后合并。

### 1.3 角色

| 角色 | 责任 |
|---|---|
| Tech Lead | 维护本文件、合并窗口 |
| Backend Engineer | 负责 executor / evolution / auth 修复 |
| Frontend Engineer | 负责登录守卫 / 画布 / WS 对齐 |
| QA | 维护 §7 回归矩阵，每 PR 跑一遍 |

---

## 二、工作分解（WBS）总览

| 编号 | 模块 | 标题 | 优先级 | 工作量（人天） | 验收段落 |
|---|---|---|---|---|---|
| P0-1 | Auth | 接入前端登录守卫与登录页 | P0 | 1.0 | §3.1 / §6.1 |
| P0-2 | Executor | 拆分 `AgentExecutionEngine` 单文件 | P0 | 2.0 | §3.2 / §6.2 |
| P0-3 | WS | 统一事件命名空间，固化 `payload` 字段 | P0 | 1.0 | §3.3 / §6.3 |
| P0-4 | Evolution | 修复 `metricsAfter` 时机错误 | P0 | 0.5 | §3.4 / §6.4 |
| P0-5 | Mission | 修复 `retryNode` / `skipNode` 不重新执行的缺陷 | P0 | 1.0 | §3.5 / §6.5 |
| P0-6 | Bootstrap | 写入 5 个默认 Agent 的 Prompt 与工具 | P0 | 0.5 | §3.6 / §6.6 |
| P0-7 | Evolution | 工具生成 JSON 抽取鲁棒化（结构化解析） | P0 | 1.0 | §3.7 / §6.7 |
| P0-8 | Metrics | `triggerEvolution` 进化前后指标可观测性 | P0 | 0.5 | §3.4 复述 / §6.4 |
| P1-1 | Mission | 删除未使用的 `executeWithTopology` 顺序版 | P1 | 0.2 | §4.1 |
| P1-2 | Runtime | `MissionRuntimeManager` 防止同一 mission 重复启动 | P1 | 0.5 | §4.2 |
| P1-3 | Auth | `triggerEvolution` 中 `force=true` 路径禁止生产角色使用 | P1 | 0.3 | §4.3 |
| P1-4 | Mission | `createMission` 修正事件名（`mission_created`） | P1 | 0.2 | §4.4 |
| P1-5 | Bootstrap | `LLMTrackingService` 估算 `estimated_cost` 真实实现 | P1 | 0.5 | §4.5 |
| P1-6 | Frontend | 移除/对齐 `usePWA` / `usePluginSystem` Mock | P1 | 1.0 | §4.6 |
| P1-7 | CI | 接入 `mvn test` + `vitest run` 到流水线 | P1 | 0.5 | §4.7 |
| P1-8 | Mission | `AgentService.installAgent` 加载配置后回写 DB | P1 | 0.3 | §4.8 |
| **P1-9** | **Dependency Upgrade** | **Spring Boot 3.2 → 3.3 升级（Java 保持 17）** | **P1** | **1.0** | **§4.9 / §6.10** |
| **P1-10** | **Dependency Upgrade** | **Spring Boot 升级专项回归矩阵** | **P1** | **1.0** | **§4.9 / §7.3** |
| P2-1 | Schema | Mission nodes/edges 引入 JSON Schema 校验 | P2 | 1.5 | §5.1 |
| P2-2 | DB | SQLite 写锁收敛（事务化 + 重试） | P2 | 2.0 | §5.2 |
| P2-3 | WS | 多人协作 SessionRegistry 后端实现 | P2 | 3.0 | §5.3 |
| P2-4 | i18n | 接入 `vue-i18n` 替代 `useI18n` | P2 | 1.0 | §5.4 |
| P2-5 | Coverage | 测试覆盖率门禁 ≥ 60% | P2 | 1.5 | §5.5 |
| P2-6 | LLM | 引入流式 Agent 执行端到端 SSE 展示 | P2 | 2.0 | §5.6 |

合计：**P0 估 7.5 人天；P1 估 6.5 人天；P2 估 11.0 人天；总计 ≈ 25 人天**（不含集成回归与文档维护）。

---

## 三、P0 — 必须立即修复

### 3.1 P0-1 前端登录守卫与登录页

#### 背景
- 后端 `JwtAuthFilter` 强制保护 `/api/**`；`JwtConfig.validateSecret` 已 fail-closed（`JwtConfig.java:26-43`）。
- 前端 `useAuth` 完整但 `main.ts` **未调用 `setupRouteGuards()`**；`router/index.ts` 没有 `login` / `forbidden` 路由。
- 结果：演示打开即被 401 风暴击穿。

#### 改造范围
| 文件 | 行为 |
|---|---|
| `src/main.ts` | 在 `app.use(router)` 之后调用 `setupRouteGuards()` |
| `src/router/index.ts` | 新增 `login` / `forbidden` 路由；为 `/missions/*`、`/history`、`/market`、`/templates`、`/settings` 加 `meta.requiresAuth = true` |
| `src/pages/LoginPage.vue`（新建） | Naive UI 表单：`username`、`password`；成功后 `useAuth().login()` 后跳 `redirect` |
| `src/pages/ForbiddenPage.vue`（新建） | 403 占位 |
| `src/composables/useAuth.ts` | 把 `email/password` 拆为 `username/password`；`login` 调用 `authApi.login({username, password})` |
| `src/api/axios.ts` | 响应拦截器对 401 触发 `clearAuth` + `router.push({name:'login'})` |

#### 验收（§6.1）
- 启动后端 `mvn spring-boot:run`（无 token 也会因 JWT 配置存在而启动；若本地开发需设 `TEAMMIND_JWT_SECRET=<32+ char>`）。
- 启动前端 `npm run dev`，浏览器直接访问 `http://localhost:3000/missions`。
- **期望**：重定向到 `/login?redirect=/missions`，URL 携带 `redirect`。
- 输入 `admin / admin123`（种子用户）登录后回跳 `/missions`，可继续访问任意 `requiresAuth` 路由。
- Vitest 新增 `useAuth.route-guards.test.ts`：mock `useRouter().push`，断言未登录访问受保护路由时被 push 到 `login`。

#### 风险
- `localStorage.getItem('token')` 模式下 SSO 失效，但本项目单租户可接受。

---

### 3.2 P0-2 拆分 `AgentExecutionEngine`

#### 背景
- `AgentExecutionEngine.java` 接近 1000 行（L955），同时承担：执行循环、消息构建、工具解析、权限校验、4 个内置工具、动态工具调度、文件 I/O、网络搜索、LLM 重试、状态更新。

#### 目标结构

```
executor/
├── AgentExecutionEngine.java        仅保留编排循环（≤ 200 行）
├── context/
│   ├── AgentExecutionContext.java   已存在
│   └── AgentExecutionResult.java    已存在
├── tool/
│   ├── AgentTool.java               工具抽象接口
│   ├── ToolRegistry.java            按 toolType 解析与调度
│   ├── PermissionService.java       Agent 权限与 tool 映射
│   ├── CodeAnalyzerTool.java        抽出 analyzeCode
│   ├── TextProcessorTool.java       抽出 processText
│   ├── FileReaderTool.java          抽出 readFile（沙箱校验内嵌）
│   ├── WebSearchTool.java           抽出 searchWeb
│   └── DynamicToolDispatcher.java   委托到内置真实能力
├── retry/
│   └── LlmRetryPolicy.java          指数退避 + 可重试错误判定
└── messaging/
    └── MessageBuilder.java          buildMessages / buildSystemPrompt
```

#### 约束
- 公共方法签名零变化（`execute(AgentExecutionContext)` 与结果 DTO 不变）。
- 单元测试从 `AgentExecutionEngineTest.java` 拆出，新增：
  - `CodeAnalyzerToolTest.java`（行数/TODO/危险调用/嵌套/重复行/质量分）
  - `FileReaderToolTest.java`（路径穿越、目录列举、二进制忽略）
  - `WebSearchToolTest.java`（未配置端点、HTTP 200、HTTP 500）
  - `LlmRetryPolicyTest.java`（429/503/timeout 重试判定）

#### 验收（§6.2）
- `wc -l backend/src/main/java/com/teammind/executor/AgentExecutionEngine.java` 输出 **≤ 220 行**。
- `mvn test -Dtest=AgentExecutionEngineTest` 全绿；新工具类测试均绿。
- `analyzeCode("if(x){if(y){if(z){}}}", "java")` 返回 `max_nesting_depth >= 3`。
- `readFile("../../etc/passwd")` 返回 `{"error":"Path is outside the sandbox root and was blocked"}` 且 `blocked=true`。

---

### 3.3 P0-3 统一 WS 事件命名空间

#### 背景
- 后端 `WSEventPublisher` 统一为 `snake_case`：`mission_started`、`mission_completed`、`mission_failed`、`agent_spawned`、`agent_status_update`、`node_update`、`log`、`evolution_triggered`、`evolution_completed`、`resolution_required`、`resolution_resolved`。
- 前端 `WSEventType` 同时存在 **`snake_case`** 与 **`namespace:snake_case`**（`collaboration:join`、`mission:completed` 等）。
- 后端 `WebSocketController` 处理的是 `/app/resolution/vote`、`/app/subscribe` 等，**没有** `mission:updated` 这类冒号路径；前端 `useCollaboration` 发的冒号消息必然 404。

#### 改造
| 文件 | 行为 |
|---|---|
| `src/types/index.ts` | `WSEventType` 删除全部带 `:` 的变体；`WSEvent.data` 字段标注 `@deprecated`，前端代码逐步移除 |
| `src/api/websocket.ts` | 删除 `event.data` 兼容读取；只读 `event.payload`；send 路径 `/app/` + `type` 拼接逻辑保持 |
| `src/composables/useCollaboration.ts` | 重写为**纯前端 UI 状态机**（与 WS 解耦），不再发送冒号事件；若要真协作见 §5.3 |
| `src/components/canvas/CollaborationCanvas.vue` | 删除 `event.data` fallback 分支 |
| `docs/ws-events.md`（新建） | 列出后端所有发出的事件、payload schema、订阅频道 |

#### 验收（§6.3）
- `grep -rn "mission:completed\|mission:updated\|collaboration:join" src/` 输出 0 行。
- `grep -rn "event.data" src/` 仅出现在 `WSEvent` 接口注释与一处向下兼容的 `payload || event.data` 兜底（限定为 `MissionDetailPage.vue`，后续 PR 删除）。
- 浏览器 Network → WS 帧：`MESSAGE` 订阅 `/topic/missions/{id}` 时，订阅成功后任意节点状态变化在 200ms 内收到一条 `MESSAGE` 帧，body JSON 形如 `{"type":"node_update","missionId":"...","payload":{"nodeId":"...","data":{"status":"running"}}}`。

---

### 3.4 P0-4 修复 `metricsAfter` 时机错误（含 P0-8 可观测性）

#### 背景
- `AgentService.triggerEvolution` 在 `evolutionEngine.evolve()` 内部已写 `agent`（`evolutionVersion`、`evolutionScore`），但**真实业务指标（`totalMissions` / `successfulMissions` / `totalTokensUsed` / `userRating`）只在 `MissionRuntimeManager.recordAgentMetrics` 任务结束后才回写**。
- 因此当前 `metricsAfter` 永远与 `metricsBefore` 一致，"前后对比验收"对真实业务指标无效。

#### 方案
1. **方法 A（最小）**：在 `AgentService.triggerEvolution` 中新增字段 `taskSignals`，将"Agent 当前已有指标"作为 before；显式注明"after 仅反映进化后即时属性，任务级指标须在下一次任务结束回写后通过 `GET /api/agents/{id}/metrics` 观察"。
2. **方法 B（推荐）**：引入 `EvolutionEvaluationService`，在 `EvolutionEngine` 提交后**重新调度**该 Agent 的最近 N 条任务（沙箱回放或合成 metric），计算 `metricsAfter`。复杂度高，本期不实施。
3. **P0-8 可观测性补充**：
   - `EvolutionResultDTO` 新增字段 `taskSignalAfter` 与 `evaluationWindow`（窗口大小）。
   - 前端 `agentMarket` store 新增 `getAgentMetrics` 后立刻调用以填充前后指标卡片。

#### 验收（§6.4）
- 新增 `AgentServiceEvolutionTest.shouldReturnTaskSignalAfterWithCurrentValue`：执行 `PROMPT_OPTIMIZATION`，断言 `metricsAfter.totalMissions == metricsBefore.totalMissions`（即时未变化），但 `toVersion` + 1。
- `GET /api/agents/agent-1/metrics` 任务执行后字段变化，前端演化历史卡片展示"执行任务 X 后刷新看实际变化"。

---

### 3.5 P0-5 `retryNode` / `skipNode` 真正重新执行

#### 背景
- `MissionService.retryNode` 仅修改 `nodes[*].data.status = "running"` 后保存，**没有把节点重新推入 `MissionRuntimeManager` 的就绪队列**。
- 前端 `MissionDetailPage.handleRetry` 遍历 `error` 节点调用接口，节点永远不会重启。

#### 方案
- `MissionRuntimeManager` 新增方法 `requeueNode(missionId, nodeId)`：
  - 把节点 `data.status = "running"`；
  - 如果节点没有依赖或在重试时强制忽略依赖（提供 `force=true` 参数），把它加入就绪队列；
  - 如果 `skipNode`，直接把节点结果标记为 `success` 并触发后继依赖节点的入度 -1。
- `MissionService.retryNode` / `skipNode` 调用 `runtimeManager.requeueNode`；并校验 mission 当前必须为 `RUNNING` 或 `PAUSED`，否则 400。

#### 验收（§6.5）
- 启动 mission → 注入一个 Agent 抛错 → 节点进入 `error` → 调用 `POST /api/missions/{id}/nodes/{nodeId}/retry` → 500ms 内节点状态变为 `running` → LLM 重新调用 → 最终 `success` 或 `error`。
- `mvn test -Dtest=MissionServiceTest#retryNodeReenqueuesNode` 绿。

---

### 3.6 P0-6 写入默认 Agent 的 Prompt 与工具

#### 背景
- `DataInitializer.initDefaultAgents` 创建 5 个默认 Agent（`agent-1 ~ agent-5`），但**没有写入 `currentPrompt` 与 `tools`**。
- 用户从市场安装默认 Agent 并启动 mission 时，`executeSimpleMission` 选中第一个已安装且 enabled 的 Agent，prompt 为空会直接走 LLM，但工具列表为空导致能力缺失。

#### 方案
- 把 `resources/agents/{code-reviewer,task-planner,data-analyst,test-engineer,documentation-writer}.md` 补齐（缺 2 个，新建）。
- `DataInitializer` 读取 `agents/*.md` 并写入每个 Agent 的 `currentPrompt`、`tools`。
- 优先级：内置硬编码的 5 段 Prompt（不依赖外部文件，可控）。

#### 验收（§6.6）
- 启动后端 → `SELECT id, name, current_prompt FROM agents WHERE current_prompt IS NULL OR current_prompt = ''` 返回 **0 行**。
- 新增 `DataInitializerTest` 验证：本地 `@TempFolder` 内放 5 个 MD → 启动后 agent-1..agent-5 均有非空 `currentPrompt`。

---

### 3.7 P0-7 工具生成 JSON 抽取鲁棒化

#### 背景
- `EvolutionEngine.parseToolFromResponse` 用 `content.indexOf('{')` / `lastIndexOf('}')`，对 LLM 输出（嵌套代码块、Markdown 围栏、说明性 JSON 示例）极易截错。
- 当前 fallback 把 `code` 字段填整个 LLM 响应，`executeDynamicTool` 不识别，**永远走不到真实能力**。

#### 方案
- 抽出 `JsonExtractor` 工具类：
  1. 先按 ``` ``` ```json ... ``` ``` ``` 块抽取；
  2. 找不到时按 ``` ``` ``` ... ``` ``` ``` 块；
  3. 仍找不到则扫描最外层匹配花括号（带括号深度计数，避免被注释/字符串吃掉）。
- 解析失败时 fallback **不再保存为工具**，而是返回 `success=false, description="LLM 输出无法解析为工具定义"`。
- 引入 `jackson` 流式解析 `JsonParser` 而非 `readValue` 全文，方便定位失败位置。

#### 验收（§6.7）
- `JsonExtractorTest`：
  - 输入 ``` ``` ```json\n{"name":"foo","description":"x"}\n``` ``` ``` → 抽取成功。
  - 输入包含嵌套 `{}`（说明性 JSON 示例）→ 抽取外层 JSON 而非说明示例。
  - 输入纯 Markdown 无 JSON → 返回 `Optional.empty()`。
- `EvolutionEngineTest.generateToolFailsCleanlyOnUnparseableResponse` 验证失败路径返回 `success=false` 而非把全文当作 `code` 写入。

---

## 四、P1 — 建议尽快修复

### 4.1 P1-1 删除 `executeWithTopology` 顺序版
- `MissionRuntimeManager` 中 `executeMission` 已默认调用 `executeWithTopologyParallel`；顺序版本是历史代码，保留易导致误用。
- 操作：删除 `executeWithTopology` 方法及其构建依赖/拓扑排序辅助（保留并行实现中的同款方法）。
- 验收：`grep -n "executeWithTopology\b" backend/src/main/java/com/teammind/executor/MissionRuntimeManager.java` 唯一命中 `executeWithTopologyParallel`。

### 4.2 P1-2 防同一 mission 重复启动
- `MissionRuntimeManager.startMission(missionId)` 当前没有"已在 activeMissions 中" 的拒绝逻辑。
- 在 `startMission` 入口增加：
  ```java
  if (activeMissions.containsKey(missionId)) {
      throw new IllegalStateException("Mission already running: " + missionId);
  }
  ```
- 增加 `RuntimeManagerConcurrencyTest`：并发启动同 mission 10 次，断言 `MissionRuntime` 数量 == 1。

### 4.3 P1-3 强制进化 RBAC
- `EvolutionRequest.context.force=true` 当前任何人可调用，跳过门禁。
- 方案：
  - 校验 `force=true` 时要求用户具备 `ROLE_ADMIN`（来自 JWT 中的 `roles`）；
  - 无权限返回 403，并打 WARN 日志。
- 验收：单测 + curl 验证 `admin` token 可 force，普通用户 token 收到 403。

### 4.4 P1-4 `createMission` 事件名修正
- 当前 `MissionService.createMission` 立刻发 `mission_started`，但状态为 PENDING。
- 改为发 `mission_created`（新增 `WSEvent.MISSION_CREATED` 常量）。
- 前端 `useMissionStore.fetchMission` 在响应中本地设置状态，不依赖事件。
- 验收：grep `publishMissionStarted` 在 `createMission` 中消失；浏览器 Network WS 帧订阅后可见 `mission_created` 帧。

### 4.5 P1-5 LLM 调用成本估算
- `schema.sql` 中 `llm_calls.estimated_cost REAL` 字段存在。
- `LLMTrackingService.recordCall` 写入时按模型定价表估算：
  ```java
  double costPer1k = pricing.getOrDefault(model, 0.0);
  long costMicros = (long) ((tokens / 1000.0) * costPer1k * 1_000_000);
  ```
- 定价表来自 `application.yml` 新增 `teammind.llm.pricing` 节点。
- 验收：`getUsageStats()` 返回非 0 的 `totalCost`，对应测试中模拟 1000 tokens、定价 0.001 → 成本 0.001。

### 4.6 P1-6 前端 Mock 能力对齐
- `usePWA`：删除 `registerServiceWorker` 中对 `/sw.js` 的引用，或新增一个最小可用的 `public/sw.js`（cache-first）。
- `usePluginSystem`：标记为 `@deprecated`，不在路由/页面导入；保留供未来真实插件架构使用。
- `useCollaboration`：`@deprecated`，迁移至 `useNotifications`（仅保留通知能力）。
- 验收：`grep -rn "usePluginSystem\|useCollaboration\|usePWA" src/pages src/components src/router` 仅出现在 `__tests__/`（如有）。

### 4.7 P1-7 CI 接入
- `.github/workflows/ci.yml`（或 `.cnb.yml` 增强）：
  - 后端：`mvn -B verify`
  - 前端：`npm ci && npm run lint:ci && npm run test -- --run && npm run build`
- 验收：每个 PR 触发流水线，全部绿色方可合并。

### 4.8 P1-8 `installAgent` 加载配置后回写
- `AgentService.installAgent` 在 `loadAgentConfig` 后**没有 `agentRepository.save`**。
- 改为：`loadAgentConfig` 后强制 `writeLockService.executeWithLock(() -> agentRepository.save(agent))`。
- 验收：`AgentServiceTest.installAgentPersistsLoadedPrompt` 验证：MD 中 prompt 为 `X`，安装后 `findById(agent).getCurrentPrompt().equals("X")`。

---

### 4.9 P1-9 Spring Boot 3.2 → 3.3 升级（Java 保持 17）

#### 4.9.1 背景与决策

- **Spring Boot 3.2.x OSS 免费维护已于 2025 年停止**（与项目维护方的政策窗口一致）。
- 继续停留在 3.2.x 等同于**不再接收安全补丁与依赖 CVE 修复**，对长期部署不可接受。
- 经代码审查，本项目使用的 Spring API（`OncePerRequestFilter`、`@Scheduled`、`@Async`、`@JdbcTypeCode(SqlTypes.JSON)`、`WebClient`、`STOMP over SockJS`、`BCryptPasswordEncoder`、`RestControllerAdvice` 等）**均为 Spring 6.x 通用子集**，无任何 3.3 才引入 / 3.2 即将弃用的特性，迁移摩擦面小。
- **决策**：升级至 **Spring Boot 3.3.x**（同大版本最小跳跃），**Java 维持 17**（Java 21 升级推迟到 v1.0 GA 后单独评估）。
- **不做的事**：不跨级升级至 3.4.x/3.5.x（风险收益不对等）；不引入 Spring Boot Actuator；不替换 HikariCP/SQLite/WAL 方案。

#### 4.9.2 前置核实（30 分钟，开工前必做）

> 由于网络搜索受限，下表给出**应现场人工核实**的官方信息源。完成后在 PR 描述中贴入截图或链接：

| 核实项 | 来源 | 通过条件 |
|---|---|---|
| 3.3.x 当前是否仍在 OSS 窗口 | https://spring.io/projects/spring-boot/support | "OSS Support" 列含目标版本 |
| 目标 3.3.x 对应 Spring Framework 版本 | 同上页 + https://spring.io/projects/spring-framework/support | 确认无 5.x/6.x 错位 |
| 目标 3.3.x 对应 Spring Security 版本 | Spring Security release notes | 与现有 `JwtAuthFilter` API 兼容 |
| 是否有 3.2 → 3.3 已知破坏性变更影响本项目 | Spring Boot 3.3 Release Notes | 命中下文 §4.9.5 兼容表 |

**若核实失败（3.3 已 EoL）** → 改升 3.4.x，并按官方迁移指南补充修正。

#### 4.9.3 依赖改动（最小化 diff）

仅修改 `backend/pom.xml` 中 `spring-boot-starter-parent` 版本号：

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <!-- 由 3.2.x → 3.3.x （具体小版本以核实结果为准） -->
    <version>3.3.x</version>
    <relativePath/>
</parent>
```

**禁止同时改动**：
- 不升级 Java 版本（保持 `<java.version>17</java.version>`）
- 不替换 Lombok / Jackson / Hibernate 版本（由 Boot Parent BOM 管理）
- 不新增依赖

#### 4.9.4 实施步骤（按顺序执行，每步必须独立 green）

1. **依赖分析**
   ```bash
   cd backend
   mvn -U clean
   mvn dependency:tree -Dincludes="org.springframework*" > ../spring-deps-before.txt
   ```

2. **改 pom.xml 版本号**

3. **编译**
   ```bash
   mvn -U clean compile
   ```
   - 期望：零编译错误；可有 deprecation warning（不阻塞）。
   - **若出现编译错误**：记录具体类与方法，先 revert 版本号，分析是 3.3 API 移除还是 JDK 编译目标问题，再决定补丁策略。

4. **测试**
   ```bash
   mvn -U test
   ```
   - **必须 100% 通过**。若失败：
     - 大概率是 `Mockito` 行为微调（Spring Boot 3.3 升级 Mockito 5.x）→ 优先适配测试侧，不动生产代码
     - 次概率是反射/Spring 内部 API 变更 → 单独评估

5. **集成构建**
   ```bash
   mvn -U verify
   ```
   - 期望：打包成功，零 FAIL。

6. **本地启动冒烟**
   ```bash
   export TEAMMIND_JWT_SECRET=$(openssl rand -base64 48)   # 32+ 字符
   mvn spring-boot:run
   ```
   - 启动日志必须出现的关键字：
     - `Started TeamMindApplication in X.XXX seconds`
     - `JWT secret validated (length=N chars)`
     - `WebSocket endpoint registered: /ws`
     - `HikariPool-1 - Start completed.`
   - **必须不出现**：
     - `ClassNotFoundException`
     - `NoSuchMethodError`
     - `NoClassDefFoundError`
     - `ClassFormatError`
     - `BeanCreationException`
   - 健康探活（如未来启用）：`GET /actuator/health` → 200（本版本未启用，跳过）

7. **端到端冒烟**（按 §7.2 13 步清单）

8. **依赖差异报告**
   ```bash
   mvn dependency:tree -Dincludes="org.springframework*" > ../spring-deps-after.txt
   diff ../spring-deps-before.txt ../spring-deps-after.txt
   ```
   - 期望：所有 `spring-*` 制品小版本或次版本变化，无意外的 `spring-` 包消失或新增。

#### 4.9.5 兼容破坏点预判与本项目对照

| 风险点 | 风险描述 | 本项目是否涉及 | 验证位置 |
|---|---|---|---|
| `WebClient` 5.x → 6.x 行为微调 | 编解码器与超时默认值变化 | **是**（`BaiduQianfanClient`、`AgentExecutionEngine.searchWeb`） | 冒烟步骤 §7.2 第 6 步 |
| `OncePerRequestFilter` Jakarta Servlet 6.1 | 新默认字符编码 | 是（`JwtAuthFilter`） | 单测 `JwtUtilTest` + 401/403 触发 |
| Spring Data JPA 3.3 → Hibernate 6.5 | `@JdbcTypeCode(SqlTypes.JSON)` 序列化 | 是（`Agent.java`） | 单测 + 重启后 `agents.permissions` 字段能正确读写 |
| `BCryptPasswordEncoder` 包路径 | 不变 | 是（`DataInitializer`） | `admin / admin123` 登录成功 |
| `@Scheduled` 时区与 cron 解析 | 默认时区从 UTC 调整 | 是（`AutomaticEvolutionScheduler`） | 单测 `AutomaticEvolutionSchedulerTest` |
| `@Async` 任务执行器绑定 | 与 `ThreadPoolConfig` 已有 `@Bean` 同名 | **是**（`MissionRuntimeManager.startMission` 有 `@Async`） | 冒烟：mission 启动非阻塞 |
| HikariCP 默认值 | 连接池默认参数微调 | 是（`application.yml` 显式覆盖，理论上不受影响） | 启动日志无 WARN |
| Spring Boot Actuator 默认暴露路径 | 路径变化 | **未启用 Actuator**（无影响） | 跳过 |
| `@ConfigurationProperties` 绑定 | Boot 3.3 收紧 | **本项目显式用 `@Value` 注入**，未命中 | 单测启动通过即可 |
| Spring Security `MethodSecurity` 默认 | 行为变化 | **未用 `@EnableMethodSecurity`** | 跳过 |
| Spring Boot 3.3 Jackson 时间类型 | `JavaTimeModule` 默认注册 | 是（`MissionDTO` 含 `LocalDateTime`） | `GET /api/missions/{id}` 返回时间字段无 NPE |

#### 4.9.6 验收（§6.10）

详见 §6.10 完整验收清单。每条验收项对应至少 1 个自动化测试或 grep 断言。

#### 4.9.7 回滚预案

- **代码回滚**：单 PR 改 `pom.xml` 一个数字即可，`git revert <commit>` 即可恢复 3.2.x。
- **数据库回滚**：不涉及 schema 变更（Hibernate `ddl-auto: update` 仅加字段，不删），回滚版本后 SQLite 文件保持兼容。
- **缓存/外部依赖**：无。
- **若发现运行时回归**（单测通过但启动失败）：
  1. 立即 revert PR；
  2. 创建 issue 标记 "Boot 3.3 阻塞项"；
  3. 临时方案：在 3.2.x 上线最小化 CVE 缓解（如升级 jackson-databind / spring-core 的 patch 版本，**仅限安全补丁**，不跨小版本）。

#### 4.9.8 PR 描述模板（提交时必填）

```markdown
## Spring Boot 3.2.x → 3.3.x 升级

### 核实依据
- https://spring.io/projects/spring-boot/support （截图见 #XXX）
- 升级前 Spring Framework: X.Y
- 升级后 Spring Framework: X.Y

### 变更范围
- [x] pom.xml: spring-boot-starter-parent 3.2.x → 3.3.x
- [x] Java 版本：保持 17
- [ ] 其它代码改动：（空 / 列出）

### 回归证据
- [x] `mvn verify` 全绿
- [x] 启动日志关键字：Started / Hikari / WebSocket / JWT
- [x] §7.2 冒烟清单 13 步全部通过
- [x] `dependency:tree` diff 见 #XXX

### 兼容性破坏点
- 已对照 §4.9.5 表逐项验证，本项目无命中

### 回滚
- `git revert <commit>` 即可，无 schema 变更
```

---

## 五、P2 — 持续打磨

### 5.1 P2-1 Mission nodes/edges JSON Schema 校验
- 引入 `networknt/json-schema-validator`；
- `schema/mission-node.schema.json`：定义 `id`、`type ∈ {agent,input,output,decision}`、`position.x/y`、`data.label`、`data.status ∈ {idle,running,success,error,waiting}` 等。
- `MissionService.updateMission` 与 `MissionRuntimeManager.executeMission` 入口做 schema 校验。

### 5.2 P2-2 SQLite 写锁收敛
- 移除 `SQLiteWriteLockService` 的全局锁；
- 在 `MissionRuntimeManager` 写节点状态处改用 `@Transactional` + 短事务 + `RetryTemplate`（针对 `SQLITE_BUSY`）；
- 保留 Hikari `maximum-pool-size: 3` 不变。

### 5.3 P2-3 多人协作 SessionRegistry
- 后端：
  - `CollaborationSessionService`（ConcurrentHashMap 内存）跟踪 `sessionId → userId → missionId`；
  - 暴露 WS 端点 `/app/collaboration/{join,leave,cursor}`；
  - 广播 `user_joined` / `user_left` / `cursor_move` 事件。
- 前端：`useCollaboration` 重新接回真实 WS 协议。

### 5.4 P2-4 i18n
- 引入 `vue-i18n`；
- 默认 `zh-CN` + `en-US` 两套；
- `useI18n` composable 替换为 `useI18n()` from `vue-i18n`。

### 5.5 P2-5 测试覆盖率门禁
- 后端：`mvn verify -Pcoverage`，要求 `instruction-covered ratio >= 60%`。
- 前端：`vitest run --coverage`，要求 `lines >= 60%`。

### 5.6 P2-6 流式 Agent 执行端到端
- `ExecutionController.streamExecuteAgent` 已有 SSE 端点，但当前只在 `result` 事件发出最终结果。
- 改为基于 `StreamingLLMClient.streamChatFlux` 把每个 token 推为 `chunk` 事件；前端 `MissionDetailPage` 增加"实时输出"面板。

---

## 六、产品交付验收标准（DoD）

> 本节是**面向产品/PM**的清单：每条都可通过"点击/curl/日志"在 5 分钟内验证。所有 P0 完成后方视为"可演示版本 v1.0"。

### 6.1 登录与会话
| # | 验收项 | 步骤 | 期望 |
|---|---|---|---|
| 6.1.1 | 未登录访问受保护路由 | 浏览器清 localStorage，访问 `/missions` | 重定向 `/login?redirect=/missions` |
| 6.1.2 | 默认管理员登录 | `admin / admin123` | 跳转原页面，token 写入 localStorage |
| 6.1.3 | Token 失效 | 后端重启或手动 `TEAMMIND_JWT_SECRET` 换新后刷新前端 | 自动跳 `/login` 并清 token |
| 6.1.4 | JWT 缺失启动失败 | 取消 `TEAMMIND_JWT_SECRET` 启动后端 | 应用启动报错 `IllegalStateException: TEAMMIND_JWT_SECRET is not set` |

### 6.2 Agent 执行
| # | 验收项 | 步骤 | 期望 |
|---|---|---|---|
| 6.2.1 | 内置工具触发 | mission prompt：`"请用 code_analyzer 工具分析下面 Java 代码 ..."` | Agent 在返回中包含 `quality_score`、`issues` |
| 6.2.2 | 文件读取沙箱 | mission 提示调用 `file_reader` 读取 `../../etc/passwd` | 工具结果 `blocked=true`，任务继续执行（不是失败） |
| 6.2.3 | 工具权限校验 | Agent 无 `read:code` 权限但 mission 要求 `code_analyzer` | 工具返回 `error: Permission denied` |
| 6.2.4 | LLM 重试 | mock LLM 客户端第一次返回 `429`，第二次返回正常 | `LLMTrackingService` 记录两条调用；最终 success |
| 6.2.5 | 最大迭代保护 | mission 强迫 Agent 持续调工具 | 达 `maxIterations` 后返回 `finishReason=max_iterations` |

### 6.3 WebSocket 实时
| # | 验收项 | 步骤 | 期望 |
|---|---|---|---|
| 6.3.1 | 全局事件订阅 | DevTools Network → WS → Frames，订阅 `/topic/events` | 任意 mission 启停可看到对应帧 |
| 6.3.2 | 任务专属订阅 | `/topic/missions/{id}` | 仅收到该 mission 事件 |
| 6.3.3 | 节点状态推送延迟 | mission 内 Agent 状态变化 | 画布在 ≤ 500ms 内更新节点颜色 |
| 6.3.4 | 事件命名统一 | grep `src/` 中 WS 事件订阅键 | 仅含 snake_case（不含冒号） |

### 6.4 进化与指标
| # | 验收项 | 步骤 | 期望 |
|---|---|---|---|
| 6.4.1 | 手动进化 | `POST /api/agents/agent-1/evolve` `{type:"PROMPT_OPTIMIZATION", reason:"test"}` | 200；`evolutionVersion` + 1；记录落库 |
| 6.4.2 | 门禁拒绝 | sample < 5 | 返回 `success=false`，`description` 解释原因 |
| 6.4.3 | 强制进化（管理员） | admin token + `context.force=true` | 跳过门禁 |
| 6.4.4 | 强制进化（普通用户） | 非 admin token + `context.force=true` | 403 |
| 6.4.5 | 自动调度 | 配置 `auto-scheduler.enabled=true` 且 sample >= 5 且成功率 < 0.6 | 定时任务触发进化；可在日志中看到 `Auto evolution ... success=true` |
| 6.4.6 | 回滚 | `POST /api/agents/agent-1/evolution/{recordId}/rollback` | `evolutionVersion` 回退；`isRolledBack=true` |
| 6.4.7 | 工具生成鲁棒 | mock LLM 返回 `...这是说明性 JSON {"a":1} ... {"name":"myTool","toolType":"text_processor"} ...` | 解析到第二个 JSON；成功添加工具 |
| 6.4.8 | 工具生成失败路径 | mock LLM 返回纯文本无 JSON | `success=false` 且**未**写入脏数据 |

### 6.5 任务生命周期
| # | 验收项 | 步骤 | 期望 |
|---|---|---|---|
| 6.5.1 | 创建 | `POST /api/missions` | WS 收到 `mission_created` 帧（非 `mission_started`） |
| 6.5.2 | 启动 → 暂停 → 恢复 | 连点按钮 | 状态分别进入 `running / paused / running` |
| 6.5.3 | 取消 | mission 进行中取消 | 所有 in-flight future `cancel(true)`；最终状态 `failed`，发布 `mission_failed` |
| 6.5.4 | 节点重试 | 制造一个节点 error | 调用 `retryNode` 后 ≤ 1s 节点重新 `running`，最终再次执行结果 |
| 6.5.5 | 节点跳过 | 调用 `skipNode` | 节点 `success` 且后继依赖节点入度 -1 |
| 6.5.6 | 防重入 | 并发 10 次 `startMission` | 仅 1 个 `MissionRuntime` 实例创建，其他 9 个收到 4xx |

### 6.6 默认数据
| # | 验收项 | 步骤 | 期望 |
|---|---|---|---|
| 6.6.1 | 默认 5 Agent | 全新数据库启动 | `SELECT count(*) FROM agents` = 5 且每个 `currentPrompt` 非空 |
| 6.6.2 | 默认管理员 | 全新数据库 | `SELECT count(*) FROM users` = 1，用户名 `admin` |

### 6.7 工具生成
（参见 §6.4.7 / 6.4.8）

### 6.8 前端 UX
| # | 验收项 | 步骤 | 期望 |
|---|---|---|---|
| 6.8.1 | 主题切换 | 顶部按钮 | localStorage `theme` 切换；Naive UI 主题同步 |
| 6.8.2 | 任务导出 | MissionDetailPage "Export" | 下载 JSON 包含 nodes/edges/logs/result |
| 6.8.3 | 协作画布拖拽 | Vue Flow 画布 | 节点位置可拖；刷新后通过 `updateMission` 持久化 |

### 6.9 性能与稳定性
| # | 验收项 | 阈值 |
|---|---|---|
| 6.9.1 | LLM 调用 P95 延迟 | ≤ 30s（默认模型） |
| 6.9.2 | 任务控制台页面 TTFB | ≤ 500ms |
| 6.9.3 | 100 次 mission 创建 | SQLite WAL 不锁死（最大等待 ≤ 5s） |
| 6.9.4 | 测试覆盖率 | 后端 line ≥ 60%；前端 line ≥ 60% |

### 6.10 Spring Boot 3.2 → 3.3 升级验收

> 本节是 P1-9 / P1-10 的专门验收清单。每条必须在 5 分钟内可独立验证。

#### 6.10.1 依赖与构建

| # | 验收项 | 命令 / 步骤 | 期望 |
|---|---|---|---|
| 6.10.1.1 | 父 POM 版本号 | `grep -A1 "spring-boot-starter-parent" backend/pom.xml \| head -3` | 显示 `3.3.x`，非 `3.2.x` |
| 6.10.1.2 | Java 版本号 | `grep "<java.version>" backend/pom.xml` | 显示 `17`，未升级至 21 |
| 6.10.1.3 | 依赖树差异 | `mvn dependency:tree -Dincludes="org.springframework*"` 与升级前对比 | 仅 spring-* 制品小/次版本变化，无新增/消失 |
| 6.10.1.4 | 编译 | `mvn -U clean compile` | 零 ERROR；可有 deprecation WARNING |
| 6.10.1.5 | 单元测试 | `mvn -U test` | 12 个测试类全绿，0 FAIL |
| 6.10.1.6 | 集成构建 | `mvn -U verify` | 打包成功 |

#### 6.10.2 应用启动

| # | 验收项 | 步骤 | 期望 |
|---|---|---|---|
| 6.10.2.1 | 启动时间 | 设置 `TEAMMIND_JWT_SECRET=<32+ char>` 后 `mvn spring-boot:run` | `Started TeamMindApplication in X.XXX seconds`，X 不应显著高于 3.2.x 基线（±20%） |
| 6.10.2.2 | 关键日志关键字 | 同上启动日志 | 全部出现：`JWT secret validated`、`WebSocket endpoint registered: /ws`、`HikariPool-1 - Start completed.`、`Tomcat started on port 8080` |
| 6.10.2.3 | 失败关键字 | 同上 | **不出现** `ClassNotFoundException` / `NoSuchMethodError` / `NoClassDefFoundError` / `BeanCreationException` |
| 6.10.2.4 | 端口监听 | `curl -I http://localhost:8080/api/auth/login -X POST -H 'Content-Type: application/json' -d '{}'` | 返回 400/401（不是 5xx / 连接拒绝） |
| 6.10.2.5 | JWT 失败拦截 | 同上请求不带 `Authorization` 头访问 `/api/missions` | 401 `{"status":401,"code":"UNAUTHORIZED",...}` |

#### 6.10.3 核心子系统回归

| # | 验收项 | 步骤 | 期望 |
|---|---|---|---|
| 6.10.3.1 | JWT 登录 | `curl -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}'` | 200，返回 `token`、`userId`、`roles:["ADMIN"]` |
| 6.10.3.2 | BCrypt 兼容 | 登录成功后，`/api/auth/me` 携带 token | 200，返回 token payload（说明 BCrypt 验证通过） |
| 6.10.3.3 | SQLite 连接 | 启动日志 + `sqlite3 ~/.teammind/teammind.db ".tables"` | `agents/missions/users/templates/evolution_records/llm_calls` 表存在 |
| 6.10.3.4 | JPA 序列化 JSON | `GET /api/agents/agent-1` 携带 token | `permissions`、`tools` 字段正确反序列化（非 `null` 或空字符串） |
| 6.10.3.5 | WebSocket 握手 | 浏览器 DevTools Network → WS 帧 | 101 Switching Protocols；后续 STOMP CONNECT 携带 token 通过鉴权 |
| 6.10.3.6 | WebSocket 鉴权失败 | 移除 token 后刷新 | STOMP CONNECT 帧被拒，断连 |
| 6.10.3.7 | `@Scheduled` 自动调度器 | 启动日志 | `Scheduled task ... AutomaticEvolutionScheduler.autoEvolveTick` 注册成功 |
| 6.10.3.8 | `@Async` 任务启动 | `POST /api/missions` 创建 mission → `POST /api/missions/{id}/start` | HTTP 200 在 ≤ 200ms 内返回（异步执行不阻塞 controller） |
| 6.10.3.9 | LLM 客户端构造 | 启动日志 | `BaiduQianfanClient` Bean 创建无错（即使 API key 为空也不抛异常） |
| 6.10.3.10 | 时间字段序列化 | `GET /api/missions/{id}` | `createdAt` / `updatedAt` 为 ISO 字符串，非数组 |

#### 6.10.4 端到端冒烟

完整执行 §7.2 全部 13 步清单，**任何一步失败必须 revert PR**。

#### 6.10.5 安全合规

| # | 验收项 | 命令 | 期望 |
|---|---|---|---|
| 6.10.5.1 | 仍依赖 3.2.x | `mvn dependency:tree \| grep "spring-boot-starter:[^:]*:3\\.2"` | 0 行 |
| 6.10.5.2 | CVE 扫描（OWASP / Trivy 任选其一） | `mvn org.owasp:dependency-check-maven:check` 或 docker run trivy | 无 CRITICAL/HIGH 级 CVE 直接命中本项目传递依赖 |

---

## 七、回归测试矩阵

### 7.1 自动化测试

| 层 | 命令 | 必须通过的用例 |
|---|---|---|
| 后端单测 | `mvn test` | JwtUtilTest、AuthServiceTest、AuthControllerTest、MissionServiceTest、MissionControllerTest、AgentServiceTest、EvolutionEngineTest、EvolutionGateServiceTest、AutomaticEvolutionSchedulerTest、AgentExecutionEngineTest、MissionRuntimeManagerTest、ResolutionServiceTest |
| 后端集成 | `mvn -DskipTests=false verify` | 同上 + DataInitializerTest（新）、WebSocketE2ETest（新） |
| 前端单测 | `npm run test -- --run` | useAuth.test.ts、mission.test.ts、template.test.ts、agentMarket.test.ts、ui.test.ts、common.test.ts、validation.test.ts、websocket.test.ts、useAuth.route-guards.test.ts（新） |
| 前端构建 | `npm run build` | 类型检查零错误 |
| 前端 E2E（Playwright，建议二期新增） | `npx playwright test` | 登录 → 创建 mission → 节点 error → 重试 → 成功；evolution 全流程 |

### 7.2 手动冒烟清单（每次发版前由 QA 走一遍）

```
□ 启动后端，确认无 JWT 密钥时报错退出
□ 启动前端，未登录访问 / 重定向到 /login
□ 登录 admin/admin123
□ Dashboard 显示 4 个默认模板、Recent Missions 空
□ Market 显示 5 个默认 Agent
□ 安装 agent-1 / agent-2 / agent-3
□ 创建 Mission：标题 + 描述（带模板 "Code Review Workflow"）
□ MissionDetailPage：画布空白（因为未拖节点）→ 直接 Start 走简单执行路径
□ 实时日志面板滚动刷新
□ 制造一个 Agent 抛错（mock）→ 节点 error → Retry → 再次执行
□ Pause / Resume / Cancel 控制
□ Agent 市场：选中 agent-1 → Evolution → Prompt Optimization → 成功 → 历史中可见
□ Settings：检查主题切换
□ 浏览器 DevTools Network → 看到 WS 帧命名均为 snake_case
□ 清 localStorage 再次访问 → 重定向 /login
```

### 7.3 Spring Boot 升级专项回归（P1-9 / P1-10）

> 本节是 P1-9 升级 PR 必须独立通过的回归矩阵，**与 §7.1 / §7.2 并行**。升级 PR 在合入前需由 QA 在 CI 环境按本清单全部打勾。

#### 7.3.1 升级前快照（升级 PR 启动时执行）

```bash
# 1. 依赖树快照
cd backend
mvn dependency:tree -Dincludes="org.springframework*" > ../spring-deps-before.txt
mvn dependency:tree > ../all-deps-before.txt

# 2. 启动时间基线（3 次取中位数）
export TEAMMIND_JWT_SECRET=$(openssl rand -base64 48)
for i in 1 2 3; do
  /usr/bin/time -f "%e" mvn spring-boot:run > /tmp/boot-$i.log 2>&1 &
  PID=$!
  sleep 30
  kill $PID 2>/dev/null
done
# 取三次启动时间中位数记录到 PR

# 3. 测试基线
mvn test 2>&1 | tee /tmp/test-before.log
```

#### 7.3.2 升级 PR 中执行

```bash
# 改 pom.xml
git diff backend/pom.xml  # 应只显示版本号一行变化

# 编译 + 测试
mvn -U clean verify 2>&1 | tee /tmp/test-after.log

# 启动时间对比
# （同上 7.3.1 步骤 2）

# 依赖差异
mvn dependency:tree -Dincludes="org.springframework*" > ../spring-deps-after.txt
diff ../spring-deps-before.txt ../spring-deps-after.txt
```

#### 7.3.3 升级后必查清单

| # | 项 | 命令 / 步骤 | 通过条件 |
|---|---|---|---|
| 1 | 编译零错 | `mvn -U clean compile 2>&1 \| grep -E "ERROR\|BUILD FAILURE"` | 无输出 |
| 2 | 单测全绿 | `mvn -U test 2>&1 \| grep -E "Tests run:.*Failures\|Tests run:.*Errors"` | 无输出 |
| 3 | 集成构建 | `mvn -U verify` | BUILD SUCCESS |
| 4 | 启动时间漂移 | 三次启动时间中位数 vs 升级前 | ±20% 以内 |
| 5 | 关键日志 | grep `Started TeamMind` `HikariPool-1 - Start completed.` `WebSocket endpoint` `JWT secret validated` | 全部命中 |
| 6 | 失败日志 | grep `ClassNotFoundException\|NoSuchMethodError\|NoClassDefFoundError\|BeanCreationException` | 全部 0 命中 |
| 7 | JWT 强制 | `curl -X POST http://localhost:8080/api/missions` 无 token | 401 |
| 8 | JWT 通过 | 登录 admin/admin123 → 带 token 调 `/api/missions` | 200 |
| 9 | WS 握手 | 浏览器登录后 `/topic/events` 订阅 | 收到 STOMP 帧 |
| 10 | WS 鉴权 | 删除 localStorage token 后刷新 | STOMP CONNECT 被拒 |
| 11 | 自动调度 | 启动日志中 `Scheduled task pool` 行 | 出现 |
| 12 | 异步执行 | mission 创建 → 启动 | HTTP 200 ≤ 200ms 返回 |
| 13 | SQLite | `sqlite3 ~/.teammind/teammind.db ".tables"` | 表存在 |
| 14 | JSON 字段 | `GET /api/agents/agent-1` | `permissions/tools` 非空 |
| 15 | 时间字段 | `GET /api/missions/{id}` | `createdAt` 为字符串 |
| 16 | CVE 扫描 | OWASP dependency-check 或 Trivy | 无 CRITICAL/HIGH |
| 17 | 残留 3.2 | `mvn dependency:tree \| grep "spring-boot:[^:]*:3\\.2"` | 0 行 |
| 18 | 残留 spring-* 旧版 | `mvn dependency:tree \| grep "spring-.*:5\\.[0-9]\\."` | 0 行 |
| 19 | 端到端冒烟 | §7.2 全 13 步 | 全部通过 |
| 20 | 前端联调 | `npm run build` + `npm run test -- --run` | 全绿 |

#### 7.3.4 升级后 24 小时观察期

升级 PR merge 到 main 后，QA 在 staging 环境连续观察 24h：

| 监控项 | 阈值 | 数据源 |
|---|---|---|
| 启动成功率 | ≥ 99% | 应用日志 `Started TeamMindApplication` 行数 / 重启次数 |
| 单元测试 flaky | ≤ 1% | CI 多次重试 |
| `/api/missions` 错误率 | ≤ 0.5% | nginx/access log 5xx |
| WS 握手失败率 | ≤ 1% | 客户端日志 |
| SQLite `database is locked` 异常 | 0 | 应用日志 `SQLITE_BUSY` 关键字 |
| LLM 调用成功率 | 与升级前持平 ±5% | `llm_calls.success=true` 占比 |

观察期内任意指标连续 2 次超阈值 → 自动 revert，回退到 3.2.x。

---

## 八、风险与回滚预案

### 8.1 风险登记表

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| JWT secret 部署疏漏导致生产拒绝启动 | 中 | 高 | README 强调；提供 `docker-compose` 默认值；K8s 启动探针提示 |
| 重构 `AgentExecutionEngine` 引入回归 | 中 | 高 | 拆 PR 保持每步可独立回退；新增工具单测 |
| SQLite WAL + Hikari 写锁并发瓶颈 | 低（本期不解决） | 中 | §5.2 P2-2 |
| 千帆模型名 `minimax-m2.5` 误植 | 中 | 低 | 修正为真实名称；CI 加配置校验 |
| WS 事件命名收敛破坏前端联动 | 低 | 高 | §7.1 全量回归 |
| 多用户协作 P2-3 占用大量工时 | 中 | 中 | 二期做；本期不阻塞 |
| **Spring Boot 3.2.x EoL 后未及时升级暴露 CVE** | **中** | **高** | **P1-9 升级到 3.3.x；§7.3 专项回归矩阵；24h 灰度观察** |
| Spring Boot 3.3 升级引入运行时回归（单测通过但生产报错） | 中 | 高 | §4.9.4 步骤 6 启动关键字 + 失败关键字双校验；§7.3.3 19 项清单；24h 灰度；自动 revert |
| 升级到 3.3 后 `@Scheduled` 时区变化导致进化调度异常 | 低 | 中 | 单测 `AutomaticEvolutionSchedulerTest` + 显式 `zone = "UTC"` 配置（若需要） |
| 升级后 `WebClient` 默认超时变化导致长 prompt 超时 | 低 | 中 | `BaiduQianfanClient` 已显式 `timeoutMs`，不受默认值影响 |
| 升级后 Hibernate 6.5 `@JdbcTypeCode(SqlTypes.JSON)` 行为变化 | 低 | 中 | 单测覆盖 + `agents.permissions/tools` 字段读写验证（§6.10.3.4） |
| Java 17 编译目标与 Lombok/annotation processor 兼容问题 | 低 | 低 | 显式保留 `<java.version>17</java.version>`；编译验证 |

### 8.2 回滚预案

- 每个 P0 Task 一个独立 PR；merge squash；保留 commit hash 列表（写入 `docs/remediation-changelog.md`）。
- 关键开关（如 `auto-scheduler.enabled`、`evolution.gate.enabled`）通过配置回退，无需代码 revert。
- 若 §3.3 WS 重命名引发雪崩，保留一个 `feature/ws-deprecation` 分支做 1 周过渡，前端 `WSEventType` 类型 union 临时保留旧字面量做 shim。
- **Spring Boot 升级回滚（专项）**：
  1. 单 PR 改 `pom.xml` 一个数字，`git revert <commit>` 即可恢复 3.2.x。
  2. **回滚触发条件**（任一）：
     - §7.3.3 第 1-6 项任一失败
     - §6.10 验收清单任一项失败
     - §7.3.4 24h 观察期任一指标连续 2 次超阈值
  3. **回滚脚本**（紧急）：
     ```bash
     cd backend
     git revert <spring-boot-upgrade-commit>
     mvn -U clean verify
     mvn spring-boot:run  # 回到 3.2.x
     ```
  4. **回滚后处理**：创建 incident report，标记"3.3 升级阻塞项"，在 §4.9 表格追加新的"已发现阻塞项"行。

---

## 附录 A：相关文档索引（新建或更新）

| 文件 | 用途 |
|---|---|
| `docs/openapi.yaml` | REST API 单一真相源（OpenAPI 3.1） |
| `docs/ws-events.md` | STOMP 事件契约（event/payload schema） |
| `docs/mission-node.schema.json` | mission node 强类型契约 |
| `docs/remediation-changelog.md` | 每条 PR 的 commit hash 与验证记录 |
| `docs/runbooks/dev-startup.md` | 开发者本地启动（含 JWT secret 生成命令） |
| `docs/runbooks/prod-deploy.md` | 生产部署 checklist |
| `docs/architecture/overview.md` | 模块依赖图（PlantUML/Mermaid） |
| `docs/runbooks/spring-boot-upgrade.md` | **Spring Boot 版本升级 SOP（从 P1-9 沉淀）** |
| `docs/dependency-policy.md` | **依赖版本策略：Spring Boot OSS 窗口跟踪 + 升级节奏** |

---

## 附录 B：交付里程碑

| 里程碑 | 完成条件 | 预计时间 |
|---|---|---|
| M1 (P0 全绿) | §3 全部完成 + §6.1-6.9 验收清单 P0 条目 100% 通过 + `mvn test`/`vitest run` 全绿 + 手动冒烟通过 | T+1 周 |
| **M1.5（Spring Boot 升级）** | **§4.9 P1-9 升级完成 + §6.10 / §7.3 全 20 项通过 + 24h 灰度观察无异常** | **T+2 周（与 P0 部分并行）** |
| M2 (P1 全绿) | §4 全部完成（含 P1-9/10）+ CI 流水线接入 + 覆盖率门禁打开（不阻塞） | T+3 周 |
| M3 (v1.0 GA) | §5 中 P2-1/P2-5 完成；覆盖率 ≥ 60%；E2E Playwright 套件上线；发版说明齐备 | T+5 周 |

---

## 附录 C：依赖版本策略

### C.1 Spring Boot 升级节奏

- **季度审查**（每 3 个月一次）：Tech Lead 访问 https://spring.io/projects/spring-boot/support，核对当前所使用版本是否仍在 OSS 免费支持窗口。
- **升级窗口决策**：
  - 当前版本 ≤ 3 个月后将 EoL → 排期下一里程碑升级
  - 当前版本已 EoL 但无新 CVE → 仍排期升级，仅不阻塞 P0
  - 当前版本有未修复 CRITICAL CVE → 紧急升级，72 小时内出 PR
- **升级最小跳跃**：每次只跨次版本（如 3.2 → 3.3），不跨大版本（3.x → 4.x 需独立技术调研）。
- **Java 版本跟随**：Java LTS（17/21/25）切换与 Spring Boot 升级**解耦**，单独评估。

### C.2 当前推荐基线（截至本文件发布）

| 组件 | 当前版本 | 推荐版本 | 节奏 |
|---|---|---|---|
| Spring Boot | 3.2.x | **3.3.x**（P1-9 升级目标） | 季度审查 |
| Java | 17 | 17（保持） | Spring Boot 3.3 EoL 前不升级 |
| Spring Framework | 跟随 Boot | 跟随 Boot | 与 Boot 同步 |
| Spring Security | 跟随 Boot | 跟随 Boot | 与 Boot 同步 |
| Hibernate | 跟随 Boot | 跟随 Boot | 与 Boot 同步 |
| SQLite JDBC | xerial 跟随 | 跟随 | 无主动升级 |

### C.3 升级熔断条件

满足任一条件必须立即停手并升级：

1. Spring Boot 当前版本显示 **"End of OSS Support"**
2. CVE 数据库出现 **CRITICAL** 级别漏洞且影响本项目传递依赖
3. JDK 当前版本进入 **"End of Public Updates"**（Java 17 时间表：Premier Support 持续至 2026-10；之后进入 Extended Support 需付费）

---

**结束**。本文档在每完成一个 Task 后，由 Tech Lead 在 §6 对应条目下追加"已验证 @<commit-hash> <date>"，并在 §7.2 冒烟清单打勾，作为审计与回滚的依据。