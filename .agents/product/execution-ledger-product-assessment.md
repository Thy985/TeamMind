# TeamMind 产品评估与路线图

> 生成时间：2025-07-16  
> 来源：产品经理两轮评估（批判 + Execution Ledger 方案评价）  
> 目标读者：Agnes / Codex / Claude Code（所有开发 Agent）

---

## 第一部分：第一轮批判 — Phase 3 交付问题诊断

### 一、工作流彻底失控

**问题：** 三个 worktree，只有 main 在动。

```
main                最新提交（Phase 3 E2E）
w7-codex-runtime    停留在 Phase 1B，落后 4+ 个提交
w7-claude-review    停留在 Phase 1B，什么都没做
```

AGENTS.md 中定义的"Codex 实现 → Claude 审查 → Agnes 修复 → 合并"协作流程**从未执行过**。Phase 3 全部提交由 Agnes 单人闭环，没有交叉审查。三 Agent 协作系统是空壳。

### 二、315 个测试通过了，但核心用户路径没有一个真实走完

E2E 测试标题声称"Codex CLI 真实调用"和"Claude Code 真实调用"——**实际只跑了 readiness check，没有真正 invoke。**

一旦 readiness 返回 UNAVAILABLE，整段测试直接 `return` 跳过。13 个测试里真正的 live 路径覆盖是 **0**。

PipelineOrchestrator 里的 `simulateAgentExecution()` 还在返回 **硬编码 mock 字符串**：
```java
// 生产环境应该是 plugin.invoke()，现在是：
if ("review".equals(stepDef.getName())) {
    return "[REVIEW] No critical findings. Implementation looks good.\n" +
           "  - Code style: OK\n  - Security: No issues";
}
```

315 这个数字是给 commit message 看的，不是给用户的。

### 三、技术债堆到了"启动崩溃"级别

- `PluginManager ↔ ReadinessManager` 循环依赖：靠 `@Lazy` 勉强绕过
- `EventStoreService` 双构造函数歧义：靠 `@Autowired` 修复
- 这些问题在生产环境里会导致 **Spring Context 启动失败**

不是"可运行"，是"能启动"。两者之间有质的区别。

### 四、E2E 基础设施依赖不可控

测试报告写着"Codex CLI ✓、Claude Code CLI ✓、Codex++ provider ✓"——但：
- Codex++ 运行在哪个端口？谁保证 57321 永远开着？
- claude CLI 需要 API key，测试机上有，其他开发者呢？
- 一个标榜"多 Agent 协作"的系统，测试本身只能跑单 Agent

### 五、最核心的产品问题：没有用户故事

所有提交信息全是技术语言：
> "CLI Platform + Recovery Hardening + ProjectPage"
> "真实 CLI 调用 + Pipeline 完整链路 + Recovery 验证"

没有一行在回答：**用户是谁？用户在什么场景下用这个？解决了什么痛点？**

ProjectPage.vue 有"CLI 健康检查"按钮——但这个功能的最终用户是谁？是开发者调试系统，还是运营团队看服务状态？没有区分，就没有产品。

### 六、第一轮评分

| 维度 | 评分 | 说明 |
|------|------|------|
| 技术完成度 | ⭐⭐⭐⭐ | 功能实现了，但靠补丁维持 |
| 质量保障 | ⭐⭐ | 315 个测试里真正覆盖核心路径的 ≈ 0 |
| 协作流程 | ⭐ | 三 Agent 分工从未执行 |
| 产品思维 | ⭐ | 无用户视角，全是工程师视角 |
| 可交付性 | ⭐⭐ | 本地跑通 ≠ 产品可用 |

---

## 第二部分：第二轮评价 — Execution Ledger 方案分析

### 一、方向判断

> **TeamMind 已经有了"账本的原材料"，只是还没有"账本"本身。**

这不是一个新的产品模块，而是把现有数据资产用正确的产品抽象重新组织。已有基础设施约 **60% 就位**，差距主要在 Extractor 和 UI 层。

### 二、核心产品洞察

**"记录尽量来自系统事实，而不是 Agent 自述。"**

这句话是整个方案的分水岭。

| 产品定位 | 数据来源 | 可信度 |
|---------|---------|--------|
| Aider（故事讲述者） | Agent 自己说"我安装了 jsonwebtoken" | 可质疑 |
| TeamMind（事实记录器） | `package.json diff + npm install 命令 + exit code 0` | 不可反驳 |

**这不是"加一个新功能"，是把 TeamMind 从"Agent 协作运行时"升级为"任务执行审计系统"。**

### 三、三层记录模型

```
Raw Trace (机器世界，永久保留)
  ↓ 自动聚合
Task Activity (人类可读，系统生成)
  ↓ 用户决策
Knowledge / ADR / Memory (长期价值，人工筛选)
```

| 层级 | 内容示例 | 用户是否天天看 |
|------|---------|--------------|
| **Raw Trace** | command, stdout/stderr, tool call, file change, process, exit code | 否，出问题才追溯 |
| **Task Activity** | 本次任务期间新增依赖、修改文件、遇到问题、解决方式 | **是，核心产品界面** |
| **Project Knowledge** | ADR, Decision, Lesson, Project Memory | 选择性查看 |

### 四、关键产品原则

> **Ledger 的价值不是"展示更多数据"，而是"把数据翻译成事实"。**

Aider 的 `/run`、`/git`、`/diff` 是让用户自己去分析原始数据。  
Ledger 是替用户分析完毕，只呈现结论和可验证的证据。

### 五、变更分类体系

```
Project Changes      → 文件改动统计
Environment Changes  → 环境变量、PATH 修改
Dependency Changes   → package.json / pom.xml 变更
Tool Changes         → 新安装的 CLI 工具
Command Activity     → 执行的命令列表（折叠式）
Agent Decisions      → 架构选择、路由决策
Problems / Incidents → 遇到的问题及解决方式
Verification         → 测试通过、证据验证结果
Outputs              → 产物摘要
```

### 六、Incident / Resolution 模式

不要叫"Bug Log"，叫 **Incident / Resolution**：

```
Problem #1
  Type:     Compilation Error
  Observed: JWT payload 类型不匹配
  Detected by: TestRunner
  Resolution: 调整 JwtPayload 类型定义
  Resolved by: Codex
  Verification: 42/42 tests passed
```

用户关心的不是"Agent 第 37 轮说了什么"，而是 **"发生了什么、为什么发生、最后怎么解决的"**。

### 七、晋升机制（Knowledge Promotion）

不是所有记录都进 ADR。设置过滤门：

```
TeamMind detected:
  "Windows 上 Playwright E2E 需要额外字体安装步骤（重试 3 次后通过）"

Would you like to save this as a project lesson?
  [Save]  [Dismiss]
```

保护用户的 ADR/Memory 不被 Agent 噪音污染。

### 八、Roadmap

#### Sprint 1：Activity Extractor Engine（核心）

**不做新 Entity，只做 Event Type → Activity Category 映射。**

```java
// 不需要新 table，只需要一个新的 Service
public class ActivityExtractor {
    List<ActivityCategory> extract(String taskId);
}

enum ActivityCategory {
    COMMANDS_EXECUTED,    // COMMAND_RUNNING + TOOL_CALLED
    FILES_CHANGED,        // FILE_CHANGED
    DEPENDENCIES_CHANGED, // 从 package.json diff 推导（需补充事件类型）
    INCIDENTS,            // ERROR_CRITICAL + ERROR_RECOVERABLE
    VERIFICATIONS,        // EVIDENCE_VERIFIED + TEST_PASSED
    AGENT_DECISIONS,      // DECISION_MADE + APPROVAL_GRANTED
}
```

**前置条件：** 扩展 `EventType` 枚举，补充依赖变更相关事件。

#### Sprint 2：Evidence 扩展

将现有 Evidence 从 5 种扩展到能支撑 Ledger 的规模：

```
PACKAGE_INSTALLED     ← npm/yarn/pip install 命令 + 版本输出
COMMAND_EXITED        ← 现有 COMMAND_EXIT 细化（exit code + duration）
ENV_VAR_MODIFIED      ← 环境变量变更记录
PROCESS_STARTED       ← 进程启动事件
```

**前置条件：** 在 `CodexPlugin` / `ClaudeCodePlugin` 的 invoke 流程中补充这些事件发射。

#### Sprint 3：Ledger UI

在 MissionDetail 增加 **Execution Ledger** Tab：

```
┌─────────────────────────────────────────────────────┐
│  Execution Ledger                                   │
├──────────────────────┬──────────────────────────────┤
│  What Changed        │  Evidence                   │
│  ─────────────       │  ─────────────────          │
│  7 files changed     │  ✓ package.json diff        │
│  +1 dependency       │  ✓ npm install jsonwebtoken │
│                      │    exit 0 @ 14:21:07        │
│  18 commands         │                              │
│  3 important         │  ✓ git diff verified        │
│                      │  ✓ 42 tests passed          │
│                      │                              │
│  1 incident          │                              │
│  resolved            │                              │
├──────────────────────┴──────────────────────────────┤
│  Knowledge Candidates                               │
│  [Create ADR]  [Save Lesson]  [Ignore]              │
└─────────────────────────────────────────────────────┘
```

命令折叠式展示："18 commands executed · 3 important" → 点击展开完整列表。

#### Sprint 4：Knowledge Promotion

用户决策界面：
- ADR 创建向导
- Lesson 保存到 `RoutingLesson` 实体
- 防噪过滤（低价值事件不触发提示）

---

## 第三部分：当前状态

### 已修复（Round 1 批判后的行动）

| 问题 | 修复 | 提交 |
|------|------|------|
| Pipeline 调用 mock 而非真实插件 | `simulateAgentExecution()` → `pluginManager.findById()` → `plugin.invoke(ctx)` | `25e4a373` |
| PluginManager ↔ ReadinessManager 循环依赖 | `@Lazy` on constructor parameter | `933cad4c` |
| EventStoreService 双构造函数歧义 | `@Autowired` on primary constructor | `933cad4c` |
| CLIConfig 类型 cast 错误 | `(String) ((String) map.getOrDefault(...)).toUpperCase()` | `e161c5b2` |
| GenericCLIPlugin expandEnvVars lambda | `Matcher.appendReplacement()` 替代 `replaceAll` | `e161c5b2` |
| CLIDiscoveryService Files.list 类型 | `File[]` + for-loop 替代 Stream | `e161c5b2` |
| 13 E2E 测试全部通过 | Spring Context 正常加载，Pipeline 真实执行 | `933cad4c` |

### 当前测试基线

```
315 tests pass, 0 failures
Frontend: ✓ built (4357 modules, 3.31s)
```

### 仍待处理（手动操作）

**Worktree 同步：**
- `w7-codex-runtime` 停留在 `7db54c0f`（Phase 1B），落后 main 7 个提交
- `w7-claude-review` 停留在 `7db54c0f`，同样落后
- 正确同步方式：`git reset --hard main`（拉取最新代码后开始新 work）

**E2E 测试改进：**
- Codex/Claude 真实调用仍有 `if (readiness.isUnavailable()) return;` 跳过逻辑
- 应改为：只有 CLI 工具根本不在 PATH 时才 skip，readiness DEGRADED 也应继续执行

### Git 提交历史（Phase 3 相关）

```
25e4a373  W7: Fix critical product gap — replace simulateAgentExecution with real plugin.invoke()
12497d03  W7: Update test baseline with E2E results (315 pass)
933cad4c  W7: Phase 3 E2E — 真实 CLI 调用 + Pipeline 完整链路 + Recovery 验证
f35b7f58  W7: Update AGENTS.md Phase 3 status
e161c5b2  W7 Phase 3: CLI Platform + Recovery Hardening + ProjectPage
c2a90662  W7: Phase 3 plan — CLI platform + Recovery hardening (3B first, 3A validates)
```

---

## 第四部分：开发纪律

### 三条铁律

1. **不写 mock 当功能**  
   `simulateAgentExecution()` 这类代码在 commit 前必须删除或替换为真实实现。

2. **测试必须有可验证的断言**  
   `if (readiness.isUnavailable()) return;` 是跳过测试，不是通过测试。

3. **提交信息说"用户得到什么"，不说"代码写了什么"**  
   ❌ "添加 CLIAdapter 接口"  
   ✅ "支持多 CLI 动态注册，新适配器只需 YAML 配置"

### 协作流程（重申）

```
1. Codex 在 worktree 实现 → 提交到 w7-codex-runtime
2. Claude Code 审查 diff → 输出 review 到 .agents/handoffs/
3. Agnes 修复问题 → 合并到 main
4. 全量测试 315+ pass → 才算完成
```

---

## 附录：现有系统 vs Ledger 所需差距分析

| 现有能力 | Ledger 需求 | 差距 | 优先级 |
|---------|------------|------|--------|
| `RuntimeEvent` 40+ 事件类型 | 事件分类引擎 | 需建立 Event → Activity Category 映射 | P0 |
| `Evidence` 5 种类型 | 扩展为 10+ 种 | 缺 PACKAGE_INSTALLED / ENV_VAR_MODIFIED | P1 |
| `EventStoreService` tiered storage | 原始数据永久保留 | 已有 | ✅ |
| `AgentInvocation` pid + command | 环境变更记录 | 部分已有，需补充 | P1 |
| `Artifact` | 输出物记录 | 已有 | ✅ |
| **无** | 环境快照对比服务 | 完全缺失 | P1 |
| **无** | Activity Extractor 引擎 | 完全缺失 | P0 |
| **无** | Knowledge Promotion UI | 完全缺失 | P2 |
| **无** | Ledger 折叠式命令展示 | 完全缺失 | P2 |

---

*本文档由 Agnes 整理，供后续开发参考。*
