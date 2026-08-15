# TeamMind Runtime Core Model (v0.3 Draft)

> 本文档定义 TeamMind "Project AI Team Runtime" 的核心数据模型。
>
> **四句定位**：
> 1. **Project-first** —— 项目是系统的一等公民，不是 worktree 的集合
> 2. **Team-first** —— 用户配置的是团队和职责，不是每次选择 CLI
> 3. **Philosophy-aware** —— 不同 Agent 不只是不同模型，而是不同工程范式
> 4. **Learning-driven** —— 团队会根据真实执行证据逐渐调整角色和路由
>
> **设计哲学三层**：
> 1. **Everything is a Plugin** —— 核心系统保持极小，能力外置
> 2. **Everything is a Capability** —— Runtime 问"需要什么能力"而非"调用谁"
> 3. **Every Agent brings its own Philosophy** —— 不是工具，是设计方法论的互补
> 4. **Project Policy Governs All** —— 治理规则约束 Agent 的行为边界

---

## TL;DR

```
TeamMind = Project + Plugin Runtime + Capability Routing + Team Policy + Shared State + Adaptive Evolution

Project                  → 项目级隔离单位 + 持续累积的状态（一等公民）
Plugin Runtime           → Cordis-like 插件管理 + 生命周期
Capability Routing       → 按能力 + 哲学 + 政策 + 历史表现 + 任务类型综合评分调度
Team Policy              → 项目治理规则约束 Agent 行为边界
Shared State             → Context + Artifacts + Findings + Decisions + Evidence
Adaptive Evolution       → 多层 Performance Profile（Global + Project + Task-Type + Role History）
```

**四句定位**：
1. **Project-first**：项目是系统的一等公民，不是 worktree 的集合
2. **Team-first**：用户配置的是团队和职责，不是每次选择 CLI
3. **Philosophy-aware**：不同 Agent 不只是不同模型，而是不同工程范式
4. **Learning-driven**：团队会根据真实执行证据逐渐调整角色和路由

**一句话**：
> **不是选择最强的 Agent，而是让不同 Agent 在最适合自己的位置协同工作，并让用户随时观察、干预、接管。**

---

## 一、设计哲学三层（Why）

### 1.1 Everything is a Plugin

```
TeamMind Core
    │
Cordis-like Plugin Runtime
    │
    ├─ Agent Plugins    (Claude / Codex / Aider / OpenCode / Gemini / 自定义)
    ├─ Context Plugins  (Git / Obsidian / Project KB / 文件系统)
    ├─ Tool Plugins     (Terminal / Browser / Docker / CI)
    ├─ Verifier Plugins (Unit Test / E2E / Lint / Static Analysis)
    ├─ Memory Plugins   (Project Memory / Task Memory)
    └─ Integration Plugins (GitHub / GitLab / Jira / Linear)
```

**核心系统根本不需要知道** Claude 是什么、Codex 是什么。它只知道：

```typescript
interface Plugin {
  capabilities: CapabilityDescriptor[];
  invoke(context: PluginContext): Promise<PluginResult>;
  stream?(context: PluginContext, onChunk: (chunk: any) => void): Promise<PluginResult>;
  cancel?(): Promise<void>;
  inspect?(): PluginHealth;
  lifecycle: { onLoad?(): Promise<void>; onUnload?(): Promise<void> };
}
```

**关键反模式**（必须避免）：

```typescript
// ❌ 错误：把 Agent 类型硬编码到 Core
if (agent === 'claude') {
  // ...
} else if (agent === 'codex') {
  // ...
}

// ✅ 正确：Core 只与 Plugin 接口交互
const result = await pluginManager.invoke({
  capability: 'code_review',
  context: ...
});
```

### 1.2 Everything is a Capability

```
Lead Agent 不需要想：
  ❌ "我要调用 Codex。"
  
Lead Agent 想：
  ✅ "我现在需要代码审查能力。"
  ✅ "Runtime，帮我找一个最适合这个项目的 code_review 能力的 Agent。"
```

Runtime 自动解析：

```
Need: code_review

Candidates:
  Codex Plugin
    quality: high
    latency: medium
    projectHistory: 0.94  (FormulaFix, security_review 角色)
    
  Claude Plugin
    quality: high
    latency: high
    projectHistory: 0.91  (FormulaFix, architecture_review 角色)

→ select Codex  (基于项目历史)
```

### 1.3 Every Agent brings its own Philosophy

不是把多个 Agent 拼起来，而是让不同 Agent 的**设计哲学**形成互补。

```
Claude Code  = 安全 / 权限 / 显式审批的工程师
Codex        = 执行 / 构建 / 测试闭环的工程师
Aider        = 快速定向编辑的工程师
Gemini CLI   = 研究 / 多模态的工程师
OpenCode     = 灵活 / 开源运行时的工程师
```

**关键洞察**：不同 CLI 是不同**工程方法论**，不是同质工具。

这会让 TeamMind 出现"**异质性冗余**"——这是传统 Agent 系统缺乏的：

```
传统：强模型 → 强模型 → 强模型
       （同一种偏见）

TeamMind：Codex（执行哲学）→ Claude（安全哲学）
         （不同方法论的交叉验证）
```

---

## 二、核心数据模型

### 2.1 Project（项目）

```typescript
interface Project {
  id: string;
  name: string;                  // "FormulaFix"
  description?: string;
  rootPath: string;              // 工作目录
  
  team: TeamConfig;              // 项目级团队配置
  
  // 持续累积的状态（这是真正积累价值的部分）
  sharedState: SharedState;
  
  // 项目级 Agent 表现档案（自适应核心）
  agentProfile: ProjectAgentProfile;
  
  createdAt: string;
  updatedAt: string;
  lastRunAt?: string;
}
```

### 2.2 Team（团队）

```typescript
interface TeamConfig {
  // 不再硬编码 "Lead = Claude"，而是"主负责人 role 的 Agent"
  roles: TeamRole[];
  
  // Team Profile 命名（可选，给团队起名）
  profileName?: string;          // "High Assurance Team" / "Rapid Iteration Team"
}

interface TeamRole {
  roleId: string;                // "LEAD" | "REVIEWER" | "TESTER" | "ARCHITECT" | ...
  
  // 这个 role 的"哲学倾向"（用于能力路由匹配）
  philosophyPreference?: AgentPhilosophy[];
  
  // 这个 role 当前绑定的 Agent Plugin
  assignedPluginId?: string;     // 可选，未绑定时由 Runtime 动态选择
  
  // 触发条件
  triggers?: Array<{
    when: 'AFTER_IMPLEMENTATION' | 'AFTER_REVIEW' | 'ON_FAILURE' | 'MANUAL';
  }>;
}
```

**示例**：

```json
{
  "profileName": "High Assurance Team",
  "roles": [
    {
      "roleId": "LEAD",
      "philosophyPreference": ["execution", "iterative_build"],
      "assignedPluginId": "codex"
    },
    {
      "roleId": "SECURITY_GATE",
      "philosophyPreference": ["safety", "controlled_action"],
      "assignedPluginId": "claude-code"
    },
    {
      "roleId": "TESTER",
      "assignedPluginId": "codex"
    },
    {
      "roleId": "REFACTORER",
      "philosophyPreference": ["rapid_edit"],
      "assignedPluginId": "aider"
    }
  ]
}
```

---

### 2.3 Team Policy（项目治理规则）

> **这是 TeamMind 区别于所有其他工具的最后一层：不是"哪个 Agent 最强就让谁做"，而是"根据项目治理规则，在允许范围内选择最合适的 Agent"。**

```typescript
interface ProjectPolicy {
  // 能力级别策略：某个能力必须由谁做、可以由谁做
  capabilityPolicies: CapabilityPolicy[];
  
  // 任务级别策略：特定类型任务的特殊规则
  taskPolicies: TaskPolicy[];
  
  // 审批规则：哪些操作必须人工确认
  approvalRules: ApprovalRule[];
  
  // 禁止规则：哪些操作绝对不能做
  prohibitionRules: ProhibitionRule[];
}

interface CapabilityPolicy {
  capability: string;              // "implementation" / "code_review" / ...
  
  // 允许使用哪些 Agent（null = 全部）
  allowedPlugins?: string[];
  
  // 首选 Agent（评分相同时的 tiebreaker）
  preferredPlugin?: string;
  
  // 必须经过审查后才能执行
  requiresReview: boolean;
  
  // 审查方（如果 requiresReview=true）
  reviewBy?: string[];
}

interface TaskPolicy {
  pattern: string;                 // 正则匹配任务描述
  override: Partial<CapabilityPolicy>;
}

interface ApprovalRule {
  condition: string;               // 触发条件
  action: 'REQUIRED' | 'SUGGESTED' | 'SKIP';
  fallback: 'APPROVE' | 'DENY' | 'PAUSE';
}

interface ProhibitionRule {
  target: string;                  // 操作类型
  reason: string;
  severity: 'HARD' | 'SOFT';       // HARD = 直接拒绝，SOFT = 警告
}
```

**示例**：

```yaml
project_policy:
  capability_policies:
    - capability: implementation
      preferred_plugin: codex
      requires_review: false
  
    - capability: code_review
      allowed_plugins: [claude-code, codex]
      preferred_plugin: claude-code
      requires_review: false
  
    - capability: security_review
      required_review: true
      review_by: [claude-code]

  task_policies:
    - pattern: ".*auth.*|.*security.*"
      override:
        required_review: true
        review_by: [claude-code]
    
    - pattern: ".*database.*|.*migration.*"
      override:
        required_review: true
        review_by: [claude-code, codex]
        approval_required: true
    
    - pattern: ".*production.*config.*"
      override:
        approval_required: true
        approval_fallback: pause

  approval_rules:
    - condition: "file_path matches '*config*production*'"
      action: REQUIRED
      fallback: pause
    - condition: "finding.severity == CRITICAL"
      action: REQUIRED
      fallback: pause
    - condition: "artifact_type == DATABASE_MIGRATION"
      action: REQUIRED
      fallback: deny

  prohibition_rules:
    - target: "bulk_refactor_production"
      reason: "生产环境不允许批量重构"
      severity: HARD
    - target: "skip_tests"
      reason: "测试不可跳过"
      severity: HARD
```

**路由时的 Policy 检查流程**：

```
1. Capability Router 给出候选 Agent 列表
   ↓
2. Policy Engine 检查：
   - 这些 Agent 是否在 allowed_plugins 中？
   - 这个 capability 是否 requires_review？
   - 这个任务是否匹配 task_policy？
   - 是否触发 approval_rules？
   - 是否违反 prohibition_rules？
   ↓
3. 过滤掉不符合 Policy 的 Agent
   ↓
4. 在剩余候选中重新评分
   ↓
5. 如果需要审批，发出 approval.required 事件
   ↓
6. 返回最终决策
```

---

### 2.4 Agent Plugin（统一接口）

```typescript
interface AgentPlugin extends Plugin {
  pluginType: 'AGENT';
  id: string;                    // "claude-code" / "codex" / ...
  
  // 设计哲学（这是 Agent 自带的）
  philosophy: AgentPhilosophy;
  
  // 能力声明（不只是"我能做什么"，还包括"我擅长做什么"）
  capabilities: CapabilityDeclaration[];
  
  // 成本与性能特征
  profile: {
    costPerInvocation?: number;
    avgLatencyMs?: number;
    reliabilityScore?: number;   // 基于官方信息 + 历史表现
  };
  
  // 健康检查
  isAvailable(): Promise<boolean>;
  
  // 执行
  executeTask(task: AgentTask): Promise<AgentResult>;
  streamTask(task: AgentTask, onChunk: (chunk: any) => void): Promise<AgentResult>;
  
  cancel?(): Promise<void>;
}

interface AgentPhilosophy {
  primary: string[];             // ['safety', 'controlled_action', 'explicit_permission']
  designGoals?: string[];        // 厂商官方文档的设计目标
  preferredRoles?: string[];     // ['security_review', 'architecture_review']
  weakRoles?: string[];          // ['bulk_refactor']
  // 注：这些信息从官方文档提取，非主观打分
}

interface CapabilityDeclaration {
  capability: string;            // "code_review" / "implementation" / "refactor"
  quality: 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR';  // 来自官方文档或实测
  description?: string;
}
```

**示例**（Claude Code）：

```yaml
id: claude-code
philosophy:
  primary:
    - safety
    - controlled_action
    - explicit_permission
    - cautious_execution
  designGoals:
    - "可控的权限边界"
    - "对每个操作要求显式审批"
  preferredRoles:
    - security_review
    - architecture_review
    - risk_analysis
  weakRoles:
    - bulk_refactor
    - bulk_formatting

capabilities:
  - capability: code_review
    quality: EXCELLENT
  - capability: implementation
    quality: GOOD
  - capability: architecture_design
    quality: EXCELLENT

profile:
  avgLatencyMs: 45000
  reliabilityScore: 0.92
```

### 2.5 Shared State（共享工作记忆）

**核心思想**：Agent 之间不是"传递对话"，而是"共同维护项目状态"。

```typescript
interface SharedState {
  // 项目上下文（持续累积）
  context: ProjectContext;
  
  // 历史决策（团队积累的智慧）
  decisions: Decision[];
  
  // 当前任务状态
  currentTask?: TaskExecution;
  
  // 项目产出
  artifacts: Artifact[];
  
  // Routing Lessons（自适应闭环的关键）
  routingLessons: RoutingLesson[];
}

interface ProjectContext {
  architecture?: string;
  adrs?: Array<{
    decision: string;
    rationale: string;
    date: string;
  }>;
  codingRules?: string[];
  testStrategy?: string;
  projectHistory?: string;       // 自然语言描述的项目历程
}

interface RoutingLesson {
  // 从执行历史中提炼的路由经验
  condition: string;             // "auth changes"
  recommendedTeam: TeamConfigRef;
  evidenceCount: number;         // 基于多少条历史
  confidence: number;            // 0-1
  learnedAt: string;
}
```

### 2.6 Task & Agent Result

```typescript
interface AgentTask {
  taskId: string;
  role: string;                  // "LEAD" | "REVIEWER" | ...
  
  // 不是"原始 prompt"，而是结构化目标
  objective: string;
  constraints?: string[];
  
  // 输入来自 Shared State，不是 stdout 文本
  context: {
    projectSummary: string;
    relevantContext: string[];
    previousArtifacts: Artifact[];
    agentPhilosophyHint?: string; // 告诉 Agent 它当前是什么 role
  };
}

interface AgentResult {
  taskId: string;
  agentId: string;
  status: 'SUCCESS' | 'FAILURE' | 'NEEDS_REVIEW';
  
  summary: string;
  artifacts: Artifact[];
  findings: Finding[];
  questions: Question[];
  
  // 关键：可独立验证的证据
  evidence: Evidence[];
  
  // 自报家门（仅用于辅助决策）
  selfReport?: {
    confidence: number;
    quality: 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR';
  };
  
  // Lead 用：建议下一步
  nextAction?: {
    suggestedRole?: string;       // 不指定 Agent，只指定 role
    suggestedCapability?: string;
    reason: string;
  };
}
```

### 2.7 Artifact（产物）

Agent 不是返回文本，而是返回**结构化产物**：

```typescript
type Artifact = 
  | CodeDiffArtifact
  | TestReportArtifact
  | ReviewFindingsArtifact
  | ResearchArtifact;

interface CodeDiffArtifact {
  type: 'CODE_DIFF';
  files: Array<{
    path: string;
    changeType: 'ADD' | 'MODIFY' | 'DELETE';
    diff: string;
    linesAdded: number;
    linesRemoved: number;
  }>;
}

interface TestReportArtifact {
  type: 'TEST_REPORT';
  framework: string;
  total: number;
  passed: number;
  failed: number;
  failures: Array<{
    testName: string;
    error: string;
  }>;
}

interface ReviewFindingsArtifact {
  type: 'REVIEW_FINDINGS';
  issues: Array<{
    severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
    file: string;
    line?: number;
    description: string;
    suggestion?: string;
  }>;
  approved: boolean;
}
```

### 2.7 Evidence（证据）

**核心原则**：`Agent says success` ≠ `Task success`。TeamMind 必须独立验证。

```typescript
type Evidence = 
  | GitDiffEvidence
  | TestExecutionEvidence
  | FileExistenceEvidence
  | CommandExitCodeEvidence;

interface GitDiffEvidence {
  type: 'GIT_DIFF';
  baseCommit: string;
  headCommit: string;
  filesChanged: number;
  totalAdditions: number;
  totalDeletions: number;
  verified: boolean;             // git log 能查到吗？
}

interface TestExecutionEvidence {
  type: 'TEST_EXECUTION';
  command: string;
  exitCode: number;
  stdout: string;
  stderr: string;
  durationMs: number;
  verified: boolean;
}

interface FileExistenceEvidence {
  type: 'FILE_EXISTENCE';
  path: string;
  exists: boolean;
  sha256?: string;
}

interface CommandExitCodeEvidence {
  type: 'COMMAND_EXIT';
  command: string;
  exitCode: number;
}
```

---

## 三、Plugin Runtime（Cordis-like）

### 3.1 架构

```
┌─────────────────────────────────────────────────────┐
│              Plugin Runtime                          │
│                                                     │
│  PluginManager         - 注册 / 注销 / 发现          │
│  PluginLoader          - 加载 / 卸载 / 隔离           │
│  CapabilityRegistry    - 注册 Agent 能力            │
│  EventBus              - 事件分发（任务 / 状态）     │
│  Scheduler             - 任务调度（依赖 / 重试）     │
│  HealthMonitor         - 探活 / 熔断                │
└─────────────────────────────────────────────────────┘
        ↑                ↑                ↑
   Claude Plugin    Codex Plugin    Aider Plugin
```

### 3.2 Plugin 生命周期

```
1. Discovery
   Plugin Manager 扫描可用插件（CLI 自检 / 文件系统扫描 / 配置）
   ↓
2. Loading
   Plugin Loader 加载 plugin metadata + capabilities
   ↓
3. Registration
   注册到 CapabilityRegistry（按能力索引）
   注册事件监听器到 EventBus
   ↓
4. Health Check
   周期性探活；失败则标记 unavailable
   ↓
5. Execution
   Scheduler 根据依赖关系调用
   失败 / 超时自动重试或熔断
   ↓
6. Cleanup
   优雅停机 / 强制终止 / 状态清理
```

### 3.3 插件依赖与编排

```
// 示例：登录态 → 拉取代码 → 执行任务 → 验证
scheduler.register({
  name: 'execute-task',
  steps: [
    { plugin: 'github', method: 'fetchRepo' },
    { plugin: 'codex', method: 'executeTask', dependsOn: ['github.fetchRepo'] },
    { plugin: 'verifier:test-runner', method: 'run', dependsOn: ['codex.executeTask'] },
    { plugin: 'verifier:diff-checker', method: 'verify', dependsOn: ['codex.executeTask'] },
  ],
  onFailure: 'rollback'
});
```

### 3.4 插件通信：Event Bus

```typescript
// 事件类型
type EventType =
  | 'plugin.loaded' | 'plugin.unloaded'
  | 'plugin.healthy' | 'plugin.failed'
  | 'task.started' | 'task.completed' | 'task.failed'
  | 'evidence.verified' | 'evidence.failed'
  | 'role.drift_detected';

// 订阅示例
eventBus.on('task.completed', async (event) => {
  await performanceTracker.record(event);
});
```

---

## 四、Capability Routing（能力路由）

### 4.1 核心算法

```typescript
async function routeTask(
  task: AgentTask,
  sharedState: SharedState
): Promise<AgentPlugin> {
  const requiredCapability = inferCapability(task);
  
  // 1. 找到所有声称能做这个能力的 Agent
  const candidates = capabilityRegistry.findByCapability(requiredCapability);
  
  // 2. 过滤不可用的（健康检查失败 / 超出预算）
  const available = candidates.filter(p => p.isAvailable());
  
  if (available.length === 0) {
    throw new NoCapableAgentError(requiredCapability);
  }
  
  // 3. 按权重评分
  const scored = available.map(plugin => ({
    plugin,
    score: calculateRoutingScore(plugin, task, sharedState)
  }));
  
  // 4. 返回最佳匹配
  return scored.sort((a, b) => b.score - a.score)[0].plugin;
}

function calculateRoutingScore(
  plugin: AgentPlugin,
  task: AgentTask,
  sharedState: SharedState
): number {
  let score = 0;
  
  // 权重 1：项目级历史表现（最高）
  const projectPerf = sharedState.agentProfile.performanceByRole[task.role];
  if (projectPerf[plugin.id]) {
    score += projectPerf[plugin.id].successRate * 40;
  }
  
  // 权重 2：哲学匹配度
  if (plugin.philosophy.primary.some(p => 
    task.philosophyRequirement?.includes(p))) {
    score += 20;
  }
  
  // 权重 3：能力声明质量
  const cap = plugin.capabilities.find(c => c.capability === inferCapability(task));
  if (cap?.quality === 'EXCELLENT') score += 15;
  else if (cap?.quality === 'GOOD') score += 10;
  else if (cap?.quality === 'FAIR') score += 5;
  
  // 权重 4：成本与延迟
  score -= (plugin.profile.avgLatencyMs / 1000);  // 1 秒 = -1 分
  score -= (plugin.profile.costPerInvocation || 0) * 5;
  
  // 权重 5：用户显式偏好（如 Team Config 中指定）
  if (sharedState.currentTask?.roleBinding === plugin.id) {
    score += 10;
  }
  
  return score;
}
```

### 4.2 用户看到的不是"选 CLI"，而是"组团队"

```typescript
// ❌ 传统
Choose CLI:
☑ Claude
☑ Codex
☑ Aider

// ✅ TeamMind
Build your AI Team:

Lead Engineer
[ Codex ▼ ]                    // 执行导向

Security Reviewer
[ Claude Code ▼ ]              // 安全导向

Test Engineer
[ Codex ▼ ]

Refactor Specialist
[ Aider ▼ ]                    // 快速编辑
```

### 4.3 Project Profile 命名

```typescript
interface TeamProfile {
  name: string;                  // "High Assurance Team" / "Rapid Iteration Team"
  description: string;
  defaultRoles: TeamRole[];
}

// 内置 Profile
const BUILTIN_PROFILES = [
  {
    name: 'High Assurance',
    description: '适合金融 / 医疗 / 安全敏感项目',
    defaultRoles: [
      { roleId: 'LEAD', philosophyPreference: ['execution'], assignedPluginId: 'codex' },
      { roleId: 'SECURITY_GATE', philosophyPreference: ['safety'], assignedPluginId: 'claude-code' },
      { roleId: 'TESTER', assignedPluginId: 'codex' },
      { roleId: 'REFACTORER', assignedPluginId: 'aider' }
    ]
  },
  {
    name: 'Rapid Iteration',
    description: '适合原型 / Hackathon / 快速验证',
    defaultRoles: [
      { roleId: 'LEAD', philosophyPreference: ['flexible_runtime'], assignedPluginId: 'opencode' },
      { roleId: 'REVIEWER', assignedPluginId: 'claude-code' },
      { roleId: 'TESTER', assignedPluginId: 'aider' }
    ]
  }
];
```

---

## 五、Adaptive Role Evolution（自适应角色进化）

### 5.1 闭环

```
更多任务
   ↓
更多执行日志
   ↓
更多 Evidence
   ↓
更准确的 Agent Profile
   ↓
更好的 Role Routing
   ↓
更高的任务成功率
   ↓
更多可靠数据
   ↓
更准确的 Team Recommendation
```

### 5.2 数据采集：不是"Log"，是"经过评估的 Signal"

```typescript
// 原始 Log
{
  agent: 'codex',
  task: 'JWT migration',
  outcome: 'success'  // Agent 自报
}

// ❌ 不能直接当 Signal
// ✅ 必须经过
{
  evidenceVerification: [
    { type: 'GIT_DIFF', verified: true, filesChanged: 8 },
    { type: 'TEST_EXECUTION', exitCode: 0, passed: 42 },
    { type: 'USER_FEEDBACK', accepted: true }
  ],
  
  reviewFindings: { approved: true, issuesCount: 0 },
  
  followUpIssues: 0,            // 后续没引入 bug
  
  performanceScore: 0.95       // 0-1 综合评估
}
```

### 5.3 Profile 双层结构

```
Global Agent Profile
        +
Project Agent Profile
        ↓
Final Suitability
```

```typescript
interface ProjectAgentProfile {
  projectId: string;
  
  // 按 role 拆分的项目级表现
  performanceByRole: Record<string, Record<string, PerformanceRecord>>;
  
  // 全局表现（作为冷启动默认值）
  globalPerformance: Record<string, PerformanceRecord>;
  
  // 检测到的 role drift
  driftAlerts: Array<{
    role: string;
    pluginId: string;
    metric: string;
    trend: 'DECLINING' | 'IMPROVING';
    sinceDate: string;
  }>;
}

interface PerformanceRecord {
  successRate: number;           // 0-1
  avgIterations: number;         // 平均返工次数
  avgDurationMs: number;
  falsePositiveRate?: number;    // 对 review 类任务有意义
  missRate?: number;
  sampleSize: number;            // 任务样本数
  lastUpdated: string;
}
```

### 5.4 推荐策略：Human-in-the-loop

```typescript
// 一个月后：37 次任务
{
  type: 'TEAM_RECOMMENDATION',
  basedOn: {
    sampleSize: 37,
    periodDays: 30
  },
  currentTeam: {
    LEAD: 'claude-code',
    REVIEWER: 'codex'
  },
  recommendedTeam: {
    LEAD: 'codex',
    ARCHITECTURE_REVIEWER: 'claude-code'
  },
  evidence: [
    {
      metric: 'Codex as Implementation Lead',
      value: 'success_rate=0.94, avg_iterations=0.7',
      trend: 'IMPROVING'
    },
    {
      metric: 'Claude as Security Reviewer',
      value: 'valid_finding_rate=0.91',
      trend: 'IMPROVING'
    }
  ],
  actions: ['APPLY', 'IGNORE', 'DETAILS']
}
```

### 5.5 Role Drift Detection

```
Project 初始配置：
  Claude = Lead
  Codex  = Reviewer

3 个月后数据：
  Codex implementation success ↑ 9%
  Claude implementation success ↓ 12%
  Claude review quality ↑ 4%

检测到：Role Drift
  Claude:
    Implementation suitability ↓ 12% (样本 23)
  Codex:
    Implementation suitability ↑ 9% (样本 31)
  
建议：重新分配
  LEAD → Codex
  REVIEWER → Claude
```

---

## 六、用户体验形态

### 6.1 项目视图

```
Project: FormulaFix
  Root: /Users/me/work/formulafix

  Team Profile: High Assurance
  
  AI Team
  ─────────────────────────────────
  🔹 Codex          LEAD        执行 / 构建 / 测试闭环
  🔸 Claude Code    SECURITY    安全 / 权限 / 显式审批
  🔸 Codex          TESTER
  🔸 Aider          REFACTORER  快速编辑

  Shared Context
  ─────────────────────────────────
  Architecture:  React 18 + MathJax 3 + Express
  ADRs:          [3 entries]
  Coding rules:  [禁止 any, 必走 Result<T>]

  Recent Activity
  ─────────────────────────────────
  ✓ T-101  修复 LaTeX 渲染错位
  ✓ T-100  添加公式预览功能
  ⚠ T-99   重构 auth 模块  需要人类决策

  Recommendation
  ─────────────────────────────────
  基于过去 37 次任务：
  [查看完整建议]
```

### 6.2 任务执行视图

```
Task: T-101  修复 LaTeX 渲染错位
  Status: COMPLETED ✓

  Steps
  ─────────────────────────────────
  [14:23] Codex          LEAD          分析问题   ✓ VERIFIED
  [14:24] Codex          LEAD          修改代码   ✓ VERIFIED (git diff)
  [14:25] Claude Code    SECURITY       审查       ✓ VERIFIED
  [14:26] Claude Code    SECURITY       发现 2 处问题  ✓ VERIFIED
  [14:27] Codex          LEAD          修复       ✓ VERIFIED
  [14:28] Aider          REFACTORER     补充测试   ✓ VERIFIED (42 pass)

  Result
  ─────────────────────────────────
  Changes:   7 files, +183 / -79
  Review:    Claude: PASS (2 issues found, 2 resolved)
  Tests:     42 passed, 0 failed
  Evidence:  5 verified items

  Performance Update
  ─────────────────────────────────
  Codex (LEAD):    implementation +0.02 (now 0.95)
  Claude (SECURITY): review_quality +0.01 (now 0.94)
```

### 6.3 简化的初次体验

新用户零配置即可用：

```typescript
// 默认 Profile
const DEFAULT_PROFILE = {
  name: 'Balanced Team',
  roles: [
    { roleId: 'LEAD', assignedPluginId: 'claude-code' },
    { roleId: 'REVIEWER', assignedPluginId: 'codex' },
    { roleId: 'TESTER', assignedPluginId: 'aider' }
  ]
};

// 第一个项目
project = {
  team: DEFAULT_PROFILE
};
```

---

## 七、与"通用 CLI 编排"的边界（最终版）

| 维度 | 通用 CLI 编排 | TeamMind |
|---|---|---|
| 思考模型 | "把几个 CLI 串起来" | "组建一支有哲学互补的 AI 团队" |
| 抽象层次 | 工具箱 | Runtime |
| Agent 之间传什么 | stdout 文本 | **Task Artifact + Evidence** |
| 调度依据 | CLI 名字 | **Capability + Philosophy + 历史表现** |
| 状态保存 | 会话历史 | **Project State**（永久累积） |
| 验证机制 | 信任 Agent 自报 | **独立 Evidence 验证** |
| 自适应性 | 静态 | **自适应 Role Evolution** |
| 长期价值 | CLI 配置 | **项目级 AI 工程知识库** |

---

## 八、实施路线（修正后）

### W1 ✅ 已完成
- 预研报告 + Adapter spec + README + RFC + CLIDiscovery（实测发现 Claude + Codex）

### W2：核心 Runtime 骨架（修正后）

| # | 任务 | 工作量 |
|---|---|---|
| W2.1 | Plugin Runtime 框架（Cordis-like） | 2 天 |
| W2.2 | Capability Registry + 路由算法 | 1.5 天 |
| W2.3 | Agent Plugin 接口（让现有 5 个 CLI 适配） | 1.5 天 |
| W2.4 | Shared State + Evidence Verifier | 1 天 |

### W3：首个完整 Pipeline

| # | 任务 | 工作量 |
|---|---|---|
| W3.1 | Claude Code Agent Plugin（完整） | 2 天 |
| W3.2 | Codex Agent Plugin（完整） | 1.5 天 |
| W3.3 | Git / Test Verifier Plugins | 1 天 |
| W3.4 | 端到端流程：用户 → Lead → Reviewer → Tester → 完成 | 2 天 |

### W4：自适应闭环

| # | 任务 | 工作量 |
|---|---|---|
| W4.1 | Project Agent Profile 数据采集 | 1.5 天 |
| W4.2 | Role Drift Detection | 1 天 |
| W4.3 | Team Recommendation UI | 1 天 |

### W5：发布

| # | 任务 | 工作量 |
|---|---|---|
| W5.1 | 5 个 CLI 的设计哲学 Matrix | 0.5 天 |
| W5.2 | 录视频 + 写 README | 1 天 |
| W5.3 | GitHub Release v0.1 | 0.5 天 |

**总计：W2-W5 = 18.5 天**

---

## 八、统一事件协议（Event Protocol）

> **所有 CLI Adapter 把自己的行为映射成 TeamMind 标准事件，前端只认识这套协议。**
> 详见 [event-protocol.md](event-protocol.md)。

核心原则：
- Adapter 在入口处转换 CLI 原始输出 → 标准 TeamMind 事件
- 前端不需要知道任何 CLI 格式
- 事件协议版本管理（breaking change 需协商）
- 关键事件：`agent.chunk` / `tool.called` / `file.changed` / `evidence.verified` / `approval.required` / `routing.decided`

**事件总数：40+ 种，覆盖完整任务生命周期。**

---

## 九、三级控制模式（Control Modes）

> **用户不只是"看"，而是可以根据信任程度在三个层级之间切换。**
> 详见 [control-modes.md](control-modes.md)。

| 模式 | 理念 | 审批触发 | 适用场景 |
|---|---|---|---|
| **Autopilot** 🤖 | 信任系统 | 仅高风险 | 熟悉项目、低风险、时间紧 |
| **Supervised** 👁 | 信任系统 + 安全网 | 每个 Agent 切换 + 高风险 | 大多数场景（默认） |
| **Manual** 🎮 | 自己主导 | 所有动作 | 新项目、调试、敏感操作 |

**用户干预层次**：
1. Observe（观察）→ 随时查看进度
2. Interrupt（中断）→ 暂停 / 取消
3. Redirect（重定向）→ 切换 Agent / 修改任务
4. Take Over（接管）→ 直接控制某个 Agent

---

## 十、Mission Control Web UI

> **不是聊天机器人，是 Mission Control。**
> 详见 [web-ui-architecture.md](web-ui-architecture.md)。

**核心设计原则**：
- **对话 ≠ 状态**：用户对话和 Agent 工作状态完全分离
- **三层面板**：Team Status（实时）+ Live Events（时间线）+ Artifacts（产物）
- **Agent 卡片**：动态网格显示每个 Agent 的实时状态
- **审批面板**：只在需要时出现，支持批量操作
- **画布用于观察，不用于编排**：不同于 n8n/LangFlow

---

## 十一、需要您拍板的关键决策

| # | 决策 | 选项 |
|---|---|---|
| 1 | 是否将"Capability"作为第一类抽象？ | 是 / 否 |
| 2 | 是否将"Agent Philosophy"作为 Plugin 元数据？ | 是 / 否 |
| 3 | 默认 Profile 是 Claude Lead 还是 Codex Lead？ | Claude / Codex / 用户选 |
| 4 | 是否在 v0.1 启用自适应推荐？ | 是（数据门槛 30 次任务）/ 否 |
| 5 | Plugin 系统是否暴露用户接口（自写 Plugin）？ | v0.1 隐藏 / v0.2 暴露 |

---

**Spec 版本**：v0.3（新增 Team Policy + 8 因素路由 + 四层 Profile）
**最后更新**：2026-08-14
**反馈渠道**：GitHub Issue `RFC-001`

---

## 附录 A：关键文档索引（v0.3）

| 文档 | 内容 | 阶段 |
|---|---|---|
| [plugin-system.md](plugin-system.md) | Cordis-like Plugin Runtime | W2 |
| [capability-routing.md](capability-routing.md) | 能力路由 + 8 因素评分 + Team Policy | W2 |
| [role-evolution.md](role-evolution.md) | **四层** Adaptive Role Evolution（Global + Project + Task-Type + Role History） | W4 |
| [agent-philosophy-matrix.md](agent-philosophy-matrix.md) | 5 个 CLI 设计哲学拆解 | — |
| [event-protocol.md](event-protocol.md) | 统一事件协议（40+ 事件类型） | W2-W3 |
| [task-state-machine.md](task-state-machine.md) | Task 状态机 + Policy Engine | W2-W3 |
| [web-ui-architecture.md](web-ui-architecture.md) | Mission Control 信息架构 | W3-W4 |
| [control-modes.md](control-modes.md) | 三级控制模式（Autopilot/Supervised/Manual） | W3 |

| v0.1（CLI 编排） | v0.2（Project AI Team Runtime） | v0.3（+Team Policy） |
|---|---|---|
| 5 个固定 CLI | **一切皆插件**（任意 Agent / Tool / Verifier / Memory / Integration） | 同 v0.2 |
| Lead = CLI 名 | **Lead = role + 能力匹配** | **Lead = role + 能力 + Policy** |
| Agent 之间传 stdout | **Agent 之间传 Task Artifact + Evidence** | 同 v0.2 |
| Agent 自报 success | **独立 Evidence Verifier** | 同 v0.2 |
| 静态配置 | **自适应 Role Evolution** | **四层 Profile（Global/Project/Task-Type/History）** |
| 选 CLI | **组建有哲学互补的团队** | 同 v0.2 |
| 价值 = 支持几个 CLI | **价值 = 项目级 AI 工程知识库** | **+ 项目治理规则（Policy）约束行为边界** |