# Capability Routing + Agent Philosophy (v0.2 Draft)

> Runtime 不问"我要调用谁"，而问"我现在需要什么能力"。
> 路由结果受 **Policy Engine** 约束，最终得分来自 **8 因素加权评分**。

---

## 一、能力路由的核心算法

### 1.1 完整路由流程

```
Task arrives
    ↓
[1] Infer required capability from task
    ↓
[2] Find all candidate Plugins claiming this capability
    ↓
[3] Filter: isAvailable() = true
    ↓
[4] Filter: match philosophy preference (if any)
    ↓
[5] Policy Check: filter out plugins violating project rules
    ↓
[6] Score each candidate (8 weighted factors)
    ↓
[7] Return highest-scoring Plugin (or emit approval.required)
    ↓
[8] (Optional) Emit role.routed event
```

### 1.2 8 因素评分函数

```typescript
function calculateRoutingScore(
  plugin: AgentPlugin,
  task: AgentTask,
  sharedState: SharedState,
  projectPolicy: ProjectPolicy
): number {
  let score = 0;
  
  // ─── 权重 1（30 分）：项目级历史表现 ───
  score += projectPerformanceScore(plugin, task, sharedState);
  
  // ─── 权重 2（20 分）：任务类型级表现（新增 v0.2）───
  score += taskTypePerformanceScore(plugin, task, sharedState);
  
  // ─── 权重 3（15 分）：哲学匹配 ───
  score += philosophyScore(plugin, task);
  
  // ─── 权重 4（12 分）：能力声明质量 ───
  score += capabilityQualityScore(plugin, task);
  
  // ─── 权重 5（8 分）：用户显式偏好 ───
  score += userPreferenceScore(plugin, task);
  
  // ─── 权重 6（5 分）：当前可用性 ───
  score += availabilityScore(plugin);
  
  // ─── 扣分项 A（-15 分）：成本与延迟 ───
  score -= costLatencyPenalty(plugin);
  
  // ─── Policy 过滤（硬约束，不打分）───
  if (!policyCheck(plugin, task, projectPolicy)) {
    return -Infinity;  // 直接排除
  }
  
  return score;
}

/**
 * 任务类型级表现：同一个 Agent 在不同任务类型上的表现可能差异很大。
 * 例：Codex 在 "parser refactor" 上 96%，在 "architecture redesign" 上 84%。
 */
function taskTypePerformanceScore(
  plugin: AgentPlugin,
  task: AgentTask,
  sharedState: SharedState
): number {
  const taskType = inferTaskType(task.objective);  // "parser_refactor" / "auth_change" / ...
  const taskTypePerf = sharedState.agentProfile
    .performanceByTaskType?.[plugin.metadata.id]?.[taskType];
  
  if (taskTypePerf && taskTypePerf.sampleSize >= 3) {
    return taskTypePerf.successRate * 20;
  }
  return 10;  // 中性默认值
}

/**
 * 推断任务类型（从 objective 文本提取关键词）
 */
function inferTaskType(objective: string): string {
  const lower = objective.toLowerCase();
  if (/parser|grammar|syntax/.test(lower)) return 'parser_refactor';
  if (/auth|jwt|oauth|permission/.test(lower)) return 'auth_change';
  if (/database|migration|schema/.test(lower)) return 'db_migration';
  if (/refactor|restructure/.test(lower)) return 'large_refactor';
  if (/security|vulnerab|safety/.test(lower)) return 'security_review';
  if (/test|e2e|unit/.test(lower)) return 'test_generation';
  if (/api|endpoint|route/.test(lower)) return 'api_design';
  if (/doc|readme/.test(lower)) return 'documentation';
  return 'general_purpose';
}
```

### 1.3 Policy 过滤

```typescript
/**
 * Policy 检查：硬约束，不符合直接排除，不打分。
 */
function policyCheck(
  plugin: AgentPlugin,
  task: AgentTask,
  projectPolicy: ProjectPolicy
): boolean {
  const capability = task.requiredCapability;
  const pluginId = plugin.metadata.id;
  
  // 1. 检查 capabilityPolicy 的 allowedPlugins
  const capPolicy = projectPolicy.capabilityPolicies.find(p => p.capability === capability);
  if (capPolicy?.allowedPlugins && !capPolicy.allowedPlugins.includes(pluginId)) {
    return false;
  }
  
  // 2. 检查 prohibitionRules
  for (const rule of projectPolicy.prohibitionRules) {
    if (matchesRule(rule.target, task)) {
      if (rule.severity === 'HARD') return false;
      // SOFT: 打警告，但不排除
    }
  }
  
  // 3. 检查 taskPolicy 覆盖
  for (const tp of projectPolicy.taskPolicies) {
    if (new RegExp(tp.pattern).test(task.objective)) {
      if (tp.override.allowedPlugins) {
        if (!tp.override.allowedPlugins.includes(pluginId)) return false;
      }
    }
  }
  
  return true;
}
```

---

## 二、8 因素评分（v0.2 升级）

> **v0.1 是 5 因素，v0.2 升级为 8 因素 + Team Policy 硬约束。**

```typescript
function calculateRoutingScore(
  plugin: AgentPlugin,
  task: AgentTask,
  sharedState: SharedState,
  projectPolicy: ProjectPolicy,
  taskTypeId: TaskTypeId  // 从 objective 推断的任务类型
): number {
  let score = 0;
  
  // ─── 因素 1（30 分）：项目级历史表现 ───
  score += projectRoleScore(plugin, task, sharedState);
  
  // ─── 因素 2（20 分）：任务类型级表现（v0.2 新增）───
  score += taskTypeScore(plugin, taskTypeId, sharedState);
  
  // ─── 因素 3（15 分）：哲学匹配 ───
  score += philosophyScore(plugin, task);
  
  // ─── 因素 4（12 分）：能力声明质量 ───
  score += capabilityQualityScore(plugin, task);
  
  // ─── 因素 5（8 分）：用户显式偏好 ───
  score += userPreferenceScore(plugin, task);
  
  // ─── 因素 6（5 分）：当前可用性 ───
  score += availabilityScore(plugin);
  
  // ─── 扣分项 A：成本与延迟 ───
  score -= costLatencyPenalty(plugin);
  
  // ─── Policy 过滤（硬约束，不打分）───
  if (!policyCheck(plugin, task, projectPolicy)) {
    return -Infinity;
  }
  
  // ─── Routing Lesson 加成 ───
  score += routingLessonBonus(plugin, task, sharedState);
  
  return score;
}
```

### 1.3 推理 Required Capability

```typescript
function inferRequiredCapability(task: AgentTask): string {
  // 显式指定优先
  if (task.requiredCapability) {
    return task.requiredCapability;
  }
  
  // 根据 objective 文本推理
  const objective = task.objective.toLowerCase();
  
  if (objective.includes('审查') || objective.includes('review')) return 'code_review';
  if (objective.includes('实现') || objective.includes('implement')) return 'implementation';
  if (objective.includes('测试') || objective.includes('test')) return 'test_generation';
  if (objective.includes('架构') || objective.includes('architect')) return 'architecture_design';
  if (objective.includes('重构') || objective.includes('refactor')) return 'refactoring';
  if (objective.includes('文档') || objective.includes('document')) return 'documentation';
  
  // 默认
  return 'general_purpose';
}
```

### 各因素函数定义

```typescript
/** 因素 1：项目级历史表现（30 分） */
function projectRoleScore(
  plugin: AgentPlugin, task: AgentTask, sharedState: SharedState
): number {
  const record = sharedState.agentProfile.performanceByRole[task.role]?.[plugin.metadata.id];
  if (record && record.sampleSize >= 5) return record.successRate * 30;
  if (record && record.sampleSize >= 3) return (record.successRate * 0.7 + 0.5 * 0.3) * 30;
  const global = sharedState.agentProfile.globalPerformance[plugin.metadata.id];
  return (global?.successRate ?? 0.5) * 30;
}

/** 因素 2：任务类型级表现（20 分，v0.2 新增） */
function taskTypeScore(
  plugin: AgentPlugin, taskTypeId: TaskTypeId, sharedState: SharedState
): number {
  const record = sharedState.agentProfile.performanceByTaskType[plugin.metadata.id]?.[taskTypeId];
  if (record && record.sampleSize >= 3) return record.successRate * 20;
  return 10; // 中性默认值
}

/** 因素 3：哲学匹配（15 分） */
function philosophyScore(plugin: AgentPlugin, task: AgentTask): number {
  if (!task.philosophyHint?.length || !plugin.metadata.philosophy) return 7.5;
  const matches = task.philosophyHint.filter(p =>
    plugin.metadata.philosophy!.primary.includes(p)
  ).length;
  return (matches / task.philosophyHint.length) * 15;
}

/** 因素 4：能力声明质量（12 分） */
function capabilityQualityScore(plugin: AgentPlugin, task: AgentTask): number {
  const cap = plugin.capabilities.find(c => c.name === task.requiredCapability);
  if (!cap) return 0;
  const map = { EXCELLENT: 12, GOOD: 8, FAIR: 4, POOR: 0 };
  return map[cap.quality] ?? 0;
}

/** 因素 5：用户显式偏好（8 分） */
function userPreferenceScore(plugin: AgentPlugin, task: AgentTask): number {
  return task.preferredPluginId === plugin.metadata.id ? 8 : 0;
}

/** 因素 6：当前可用性（5 分） */
function availabilityScore(plugin: AgentPlugin): number {
  return plugin.isAvailable() ? 5 : 0;
}

/** 扣分项：成本与延迟 */
function costLatencyPenalty(plugin: AgentPlugin): number {
  let penalty = 0;
  penalty += (plugin.profile.avgLatencyMs ?? 30000) / 1000;
  if (plugin.profile.costPerInvocation) {
    penalty += plugin.profile.costPerInvocation * 5;
  }
  return Math.min(penalty, 15);
}

/** Routing Lesson 加成（15 分封顶） */
function routingLessonBonus(
  plugin: AgentPlugin, task: AgentTask, sharedState: SharedState
): number {
  const lesson = sharedState.routingLessons.find(
    l => l.pluginId === plugin.metadata.id && l.confidence > 0.8
  );
  return lesson ? lesson.confidence * 15 : 0;
}
```

---

## 二、Agent Philosophy Matrix

### 2.1 5 个 CLI 的设计哲学拆解

> 数据来源：各 CLI 的官方文档、设计目标、默认行为。
> 注：这些不是主观打分，而是从官方信息提取。

#### Claude Code

```yaml
id: claude-code
vendor: Anthropic
homepage: https://github.com/anthropics/claude-code
stars: 14.1万

design_philosophy:
  primary:
    - safety               # 安全第一
    - controlled_action    # 受控操作
    - explicit_permission  # 显式权限
    - cautious_execution   # 谨慎执行
  design_goals:
    - 可控的权限边界
    - 对每个操作要求显式审批
    - sandbox 隔离执行
    - 用户主导决策
  preferred_roles:
    - security_review      # 强
    - architecture_review  # 强
    - risk_analysis        # 强
  weak_roles:
    - bulk_refactor        # 不擅长
    - bulk_formatting      # 不擅长

capabilities:
  code_review:            EXCELLENT  # 强项
  implementation:        GOOD
  architecture_design:    EXCELLENT
  risk_analysis:          EXCELLENT
  test_generation:        FAIR
  refactoring:            FAIR
  documentation:          GOOD

profile:
  avg_latency_ms: 45000
  reliability_score: 0.92
  cost: high              # Claude API 较贵
```

#### Codex CLI

```yaml
id: codex
vendor: OpenAI
homepage: https://github.com/openai/codex
stars: 10.5万

design_philosophy:
  primary:
    - execution              # 执行导向
    - repository_understanding  # 仓库理解
    - iterative_build        # 迭代构建
    - test_and_review        # 测试与审查
  design_goals:
    - 理解整个代码库
    - 修改代码并测试
    - 审查 diff 后提交
    - build features and fix bugs
    - 沙箱执行
  preferred_roles:
    - implementation        # 强项
    - debugging             # 强项
    - testing               # 强项
    - diff_iteration        # 强项
  weak_roles:
    - abstract_architecture # 不擅长
    - pure_research         # 不擅长

capabilities:
  code_review:            GOOD
  implementation:        EXCELLENT
  architecture_design:    FAIR
  risk_analysis:          FAIR
  test_generation:        EXCELLENT
  refactoring:            EXCELLENT
  documentation:          FAIR

profile:
  avg_latency_ms: 35000
  reliability_score: 0.94
  cost: high
```

#### Aider

```yaml
id: aider
vendor: 开源社区
homepage: https://github.com/Aider-AI/aider
stars: 4.8万

design_philosophy:
  primary:
    - targeted_edit         # 定向编辑
    - git_native            # Git 原生
    - rapid_patch           # 快速补丁
  design_goals:
    - 与 Git 深度集成
    - 自动 commit
    - 大代码库定向修改
    - 配对编程风格
  preferred_roles:
    - refactoring           # 强项
    - targeted_edit         # 强项
    - rapid_patch           # 强项
  weak_roles:
    - full_architecture     # 不擅长
    - large_review          # 不擅长

capabilities:
  code_review:            FAIR
  implementation:        GOOD
  architecture_design:    POOR
  test_generation:        FAIR
  refactoring:            EXCELLENT  # 强项
  documentation:          FAIR

profile:
  avg_latency_ms: 25000
  reliability_score: 0.88
  cost: low               # 可用本地模型
```

#### Gemini CLI

```yaml
id: gemini-cli
vendor: Google
homepage: https://github.com/google-gemini/gemini-cli
stars: 10.5万

design_philosophy:
  primary:
    - research              # 研究导向
    - multimodal            # 多模态
    - large_context         # 大上下文
    - free_tier             # 免费额度大
  design_goals:
    - 大上下文窗口
    - 多模态理解
    - 免费额度
    - 工具集成（搜索、文件）
  preferred_roles:
    - research              # 强项
    - documentation_search  # 强项
    - large_codebase_analysis  # 强项
  weak_roles:
    - complex_refactor      # 不擅长

capabilities:
  code_review:            GOOD
  implementation:        GOOD
  architecture_design:    GOOD
  research:               EXCELLENT  # 强项
  test_generation:        GOOD
  refactoring:            FAIR
  documentation:          EXCELLENT  # 强项

profile:
  avg_latency_ms: 30000
  reliability_score: 0.85
  cost: low               # 免费额度大
```

#### OpenCode

```yaml
id: opencode
vendor: 开源社区
homepage: https://github.com/opencode-ai/opencode
stars: 19.5万

design_philosophy:
  primary:
    - flexibility           # 灵活
    - open_runtime          # 开放运行时
    - multi_model           # 多模型
    - privacy               # 隐私本地化
  design_goals:
    - 多模型支持
    - 本地化优先
    - 隐私保护
    - 高度可配置
  preferred_roles:
    - general_purpose       # 通用
    - privacy_sensitive     # 隐私敏感
  weak_roles:
    - none_specific         # 无特定强项

capabilities:
  code_review:            GOOD
  implementation:        GOOD
  architecture_design:    GOOD
  research:               GOOD
  test_generation:        GOOD
  refactoring:            GOOD
  documentation:          GOOD

profile:
  avg_latency_ms: 20000
  reliability_score: 0.87
  cost: variable          # 取决于模型
```

---

## 三、能力 → Plugin 映射矩阵

| 能力 \ Plugin | Claude Code | Codex | Aider | Gemini CLI | OpenCode |
|---|---|---|---|---|---|
| **code_review** | EXCELLENT | GOOD | FAIR | GOOD | GOOD |
| **implementation** | GOOD | EXCELLENT | GOOD | GOOD | GOOD |
| **architecture_design** | EXCELLENT | FAIR | POOR | GOOD | GOOD |
| **risk_analysis** | EXCELLENT | FAIR | POOR | FAIR | FAIR |
| **test_generation** | FAIR | EXCELLENT | FAIR | GOOD | GOOD |
| **refactoring** | FAIR | EXCELLENT | EXCELLENT | FAIR | GOOD |
| **documentation** | GOOD | FAIR | FAIR | EXCELLENT | GOOD |
| **research** | GOOD | POOR | POOR | EXCELLENT | GOOD |
| **large_codebase_analysis** | GOOD | GOOD | FAIR | EXCELLENT | GOOD |
| **privacy_sensitive** | POOR | POOR | GOOD | POOR | EXCELLENT |

---

## 四、能力路由的实际例子

### 4.1 标准开发任务

```
User: "把 auth 从 session 改成 JWT"

CapabilityRouter.inferRequiredCapability("把 auth 从 session 改成 JWT")
→ "implementation"

Candidates:
  Claude Code: implementation=GOOD, philosophy_match=N/A
  Codex:       implementation=EXCELLENT, philosophy_match=N/A
  Aider:       implementation=GOOD, philosophy_match=N/A
  Gemini CLI:  implementation=GOOD, philosophy_match=N/A
  OpenCode:    implementation=GOOD, philosophy_match=N/A

→ Default winner: Codex (highest base score)

But if project has history:
  Codex on FormulaFix (implementation role): 0.95 (37 samples)
  Claude on FormulaFix (implementation role): 0.82 (23 samples)
  
→ Same winner: Codex (history confirms)
```

### 4.2 安全审查任务

```
User: "审查一下 PR 中的权限变更"

CapabilityRouter.inferRequiredCapability("审查权限变更")
→ "code_review"
    with philosophyHint: ["safety", "controlled_action"]

Candidates:
  Claude Code: code_review=EXCELLENT, philosophy_match=1.0  (safety + controlled_action 全匹配)
  Codex:       code_review=GOOD, philosophy_match=0.0
  Aider:       code_review=FAIR, philosophy_match=0.0
  Gemini CLI:  code_review=GOOD, philosophy_match=0.0

→ Winner: Claude Code (philosophy match 决定胜负)
```

### 4.3 大型重构任务

```
User: "重构这 200 个文件的 monorepo"

CapabilityRouter.inferRequiredCapability("重构")
→ "refactoring"
    with philosophyHint: ["large_scale_change"]

Candidates:
  Claude Code: refactoring=FAIR,  weak_role=bulk_refactor
  Codex:       refactoring=EXCELLENT
  Aider:       refactoring=EXCELLENT, philosophy_match=1.0 (targeted_edit + rapid_patch)
  Gemini CLI:  refactoring=FAIR, weak_role=complex_refactor

→ Winner: Aider (philosophy match + capability)
```

---

## 五、与项目历史结合（自适应）

### 5.1 Profile 双层

```typescript
interface ProjectAgentProfile {
  projectId: string;
  
  // 第一层：项目级（最重要）
  performanceByRole: Record<string, Record<string, PerformanceRecord>>;
  // 例：
  //   performanceByRole['LEAD']['codex'] = { successRate: 0.95, ... }
  //   performanceByRole['SECURITY_GATE']['claude-code'] = { validFindingRate: 0.91, ... }
  
  // 第二层：全局（冷启动默认值）
  globalPerformance: Record<string, PerformanceRecord>;
  
  // 全局 + 项目 = 最终适用性
  finalSuitability(pluginId: string, role: string): number {
    const projectScore = this.performanceByRole[role]?.[pluginId]?.successRate;
    if (projectScore !== undefined && this.performanceByRole[role][pluginId].sampleSize >= 5) {
      return projectScore;
    }
    return this.globalPerformance[pluginId]?.successRate || 0.5;  // 中性默认值
  }
}
```

### 5.2 Routing Lesson 自动提炼

```typescript
// 当一个任务完成后：
async function extractRoutingLesson(taskExecution: TaskExecution): Promise<void> {
  const { task, steps, verification } = taskExecution;
  
  // 1. 识别关键 pattern
  const patterns = extractPatterns(task.objective);  // "auth", "JWT", "review"...
  
  // 2. 关联到 routing lesson
  for (const pattern of patterns) {
    const lessonKey = `${pattern.category}-${pattern.action}`;
    const currentLesson = sharedState.routingLessons.find(l => l.key === lessonKey);
    
    if (currentLesson) {
      // 更新现有 lesson
      currentLesson.evidenceCount++;
      currentLesson.confidence = updateConfidence(currentLesson, verification.score);
    } else {
      // 新建 lesson
      sharedState.routingLessons.push({
        key: lessonKey,
        condition: `Task involves ${pattern.category}`,
        recommendedTeam: extractTeamFromSteps(steps),
        evidenceCount: 1,
        confidence: 0.6,
        learnedAt: new Date().toISOString()
      });
    }
  }
}
```

### 5.3 项目级 Routing Lessons 示例

```yaml
routingLessons:
  - key: "auth-change"
    condition: "Task involves auth changes"
    recommendedTeam:
      LEAD: codex              # 执行优先
      SECURITY_GATE: claude-code  # 安全审查
      TESTER: codex
    evidenceCount: 12
    confidence: 0.92
    learnedAt: 2026-08-01
    
  - key: "large-refactor"
    condition: "Task involves >50 files refactor"
    recommendedTeam:
      LEAD: aider             # 快速定向编辑
      REVIEWER: claude-code   # 安全边界
    evidenceCount: 4
    confidence: 0.78
    learnedAt: 2026-08-10
```

### 5.4 应用 Routing Lessons

```
Task: "修改 OAuth 权限"

CapabilityRouter:
  1. 任务分类 → "auth"
  2. 查询 routingLessons["auth-change"] → 推荐 LEAD=codex, SECURITY=claude
  3. 直接使用推荐的 team 配置（不重算）
```

---

## 六、Team Profile 命名（用户友好层）

### 6.1 内置 Profile

```typescript
const BUILTIN_PROFILES = [
  {
    name: 'High Assurance',
    description: '适合金融 / 医疗 / 安全敏感项目',
    defaultRoles: [
      { roleId: 'LEAD', philosophyPreference: ['execution', 'iterative_build'], assignedPluginId: 'codex' },
      { roleId: 'SECURITY_GATE', philosophyPreference: ['safety', 'controlled_action'], assignedPluginId: 'claude-code' },
      { roleId: 'TESTER', assignedPluginId: 'codex' },
      { roleId: 'REFACTORER', philosophyPreference: ['rapid_edit'], assignedPluginId: 'aider' }
    ]
  },
  {
    name: 'Rapid Iteration',
    description: '适合原型 / Hackathon / 快速验证',
    defaultRoles: [
      { roleId: 'LEAD', philosophyPreference: ['flexible_runtime'], assignedPluginId: 'opencode' },
      { roleId: 'REVIEWER', philosophyPreference: ['safety'], assignedPluginId: 'claude-code' },
      { roleId: 'TESTER', assignedPluginId: 'aider' }
    ]
  },
  {
    name: 'Research Heavy',
    description: '适合代码理解 / 文档 / 学习',
    defaultRoles: [
      { roleId: 'RESEARCHER', philosophyPreference: ['research', 'multimodal'], assignedPluginId: 'gemini-cli' },
      { roleId: 'IMPLEMENTER', assignedPluginId: 'codex' },
      { roleId: 'REVIEWER', assignedPluginId: 'claude-code' }
    ]
  },
  {
    name: 'Privacy First',
    description: '适合代码不能出本地的敏感场景',
    defaultRoles: [
      { roleId: 'LEAD', philosophyPreference: ['privacy', 'flexible_runtime'], assignedPluginId: 'opencode' },
      { roleId: 'REVIEWER', assignedPluginId: 'claude-code' }
    ]
  }
];
```

### 6.2 用户看到的"组建 AI 团队" UI

```
Build your AI Team

Team Profile:  [High Assurance ▼]

Lead Engineer
[ Codex ▼ ]                    执行导向
  Why: execution + iterative_build

Security Reviewer
[ Claude Code ▼ ]              安全导向
  Why: safety + controlled_action

Test Engineer
[ Codex ▼ ]

Refactor Specialist
[ Aider ▼ ]                    快速编辑
  Why: rapid_edit

[保存]
```

---

## 七、推荐与角色漂移

### 7.1 推荐生成

```typescript
async function generateTeamRecommendation(projectId: string): Promise<Recommendation | null> {
  const profile = await loadProjectProfile(projectId);
  const tasks = profile.totalTasks;
  
  if (tasks < 30) return null;  // 样本不足
  
  // 找出明显不匹配的配置
  const issues = [];
  for (const [role, pluginScores] of Object.entries(profile.performanceByRole)) {
    for (const [pluginId, record] of Object.entries(pluginScores)) {
      if (record.successRate < 0.7 && record.sampleSize >= 10) {
        issues.push({
          role,
          currentPlugin: pluginId,
          currentScore: record.successRate,
          suggestedPlugin: findBetterAlternative(role, profile),
          suggestedScore: ...
        });
      }
    }
  }
  
  if (issues.length === 0) return null;
  
  return {
    type: 'TEAM_RECOMMENDATION',
    basedOn: { sampleSize: tasks, periodDays: 30 },
    currentTeam: profile.currentTeam,
    recommendedTeam: buildRecommendedTeam(issues),
    issues,
    actions: ['APPLY', 'IGNORE', 'DETAILS']
  };
}
```

### 7.2 漂移检测

```typescript
async function detectRoleDrift(projectId: string): Promise<DriftAlert[]> {
  const profile = await loadProjectProfile(projectId);
  const alerts: DriftAlert[] = [];
  
  for (const [role, pluginScores] of Object.entries(profile.performanceByRole)) {
    for (const [pluginId, record] of Object.entries(pluginScores)) {
      // 检测最近 30 天趋势
      const recent = await getRecentPerformance(projectId, role, pluginId, 30);
      const baseline = await getBaselinePerformance(projectId, role, pluginId, 90);
      
      const trend = recent.successRate - baseline.successRate;
      
      if (Math.abs(trend) > 0.1 && recent.sampleSize >= 10) {
        alerts.push({
          role,
          pluginId,
          metric: 'success_rate',
          trend: trend > 0 ? 'IMPROVING' : 'DECLINING',
          change: trend,
          sinceDate: recent.sinceDate,
          recommendation: trend < 0 
            ? `考虑让 ${pluginId} 在 ${role} 角色上退场`
            : `考虑让 ${pluginId} 在 ${role} 角色上承担更多`
        });
      }
    }
  }
  
  return alerts;
}
```

---

## 八、用户控制权

```typescript
// 用户始终保留最终决定权
interface TeamConfigOverride {
  // 用户可以锁定某个 role 给特定 Plugin
  lockedRoles: Array<{
    roleId: string;
    pluginId: string;
    reason?: string;
  }>;
  
  // 用户可以排除某些 Plugin
  excludedPlugins: string[];
  
  // 用户可以强制使用某个 Profile
  forceProfile?: string;
}
```

例如：

```typescript
// 用户说"Claude Code 我必须当 Lead"
{
  lockedRoles: [
    { roleId: 'LEAD', pluginId: 'claude-code', reason: '用户偏好' }
  ],
  excludedPlugins: [],
  forceProfile: null
}
```

Runtime 即使推荐 Codex 也会尊重这个 lock。

---

## 九、与其他文档的关系

| 文档 | 关系 |
|---|---|
| [core-model.md](core-model.md) | 数据结构基础 |
| [plugin-system.md](plugin-system.md) | Plugin Runtime 实现 |
| [role-evolution.md](role-evolution.md) | 自适应闭环（待写） |
| [agent-philosophy-matrix.md](agent-philosophy-matrix.md) | 各 CLI 详细拆解（待写） |

---

**版本**：v0.1 Draft
**最后更新**：2026-08-14