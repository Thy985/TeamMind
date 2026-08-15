# Adaptive Role Evolution (v0.2 Draft)

> TeamMind 不只是让多个 Agent 协作，而是让团队配置随项目运行逐渐进化。
>
> **v0.2 更新**：Profile 从双层升级为**四层**（Global + Project + Task-Type + Role History），
> 让路由算法能精确到"这个 Agent 在这个任务类型上表现如何"。

---

## 一、为什么需要 Role Evolution

### 1.1 静态配置的局限

```
项目初始配置（开发者手动选）：
  Claude = Lead
  Codex  = Reviewer
  
3 个月后实际数据：
  Codex implementation success ↑ 9%
  Claude implementation success ↓ 12%
  Claude review quality ↑ 4%
  
→ 初始配置已不是最优
→ 用户不知道，需要系统提醒
```

### 1.2 自适应闭环价值

```
使用越多 → 数据越多 → 路由越准 → 成功率越高 → 用户越多
→ 飞轮效应
```

这是 **TeamMind 真正的护城河**：
- CLI 适配可以被复制
- **项目级 Performance Profile + Role History + Routing Lessons** 无法被复制

---

## 二、核心数据模型

### 2.1 Project Agent Profile

```typescript
interface ProjectAgentProfile {
  projectId: string;
  
  // ─── 第一层：Global Profile（所有项目的默认基准）────
  globalPerformance: Record<string, PerformanceRecord>;
  // 例：
  //   globalPerformance['codex'] = {
  //     implementation: { successRate: 0.91, sampleSize: 847 },
  //     security_review: { successRate: 0.88, sampleSize: 312 },
  //     code_review: { successRate: 0.85, sampleSize: 198 }
  //   }
  
  // ─── 第二层：Project Profile（按 role 拆分）────
  performanceByRole: Record<string, Record<string, PerformanceRecord>>;
  // 例：
  //   performanceByRole['LEAD']['codex'] = {
  //     successRate: 0.95,
  //     avgIterations: 0.7,
  //     avgDurationMs: 672000,
  //     sampleSize: 37,
  //     lastUpdated: '2026-08-14'
  //   }
  
  // ─── 第三层：Task-Type Profile（按任务类型拆分）────
  performanceByTaskType: Record<string, Record<string, PerformanceRecord>>;
  // 例：
  //   performanceByTaskType['codex'] = {
  //     'parser_refactor': { successRate: 0.96, sampleSize: 12 },
  //     'auth_change': { successRate: 0.94, sampleSize: 8 },
  //     'db_migration': { successRate: 0.72, sampleSize: 5 },
  //     'architecture_redesign': { successRate: 0.84, sampleSize: 7 }
  //   }
  
  // ─── 第四层：Role History（完整的任务执行历史）────
  roleHistory: RoleExecutionHistory[];
  
  // ─── 衍生层 ───
  driftAlerts: DriftAlert[];
  routingLessons: RoutingLesson[];
}

/** 任务类型 ID（从 objective 文本推断） */
type TaskTypeId =
  | 'parser_refactor'
  | 'auth_change'
  | 'db_migration'
  | 'large_refactor'
  | 'security_review'
  | 'test_generation'
  | 'api_design'
  | 'documentation'
  | 'bug_fix'
  | 'feature_addition'
  | 'performance_optimization'
  | 'general_purpose';

interface RoleExecutionHistory {
  taskId: string;
  role: string;
  pluginId: string;
  taskTypeId: TaskTypeId;
  objective: string;
  outcome: 'SUCCESS' | 'FAILED' | 'PARTIAL';
  evidenceVerified: boolean;
  iterations: number;
  durationMs: number;
  timestamp: string;
}

interface PerformanceRecord {
  // 通用
  successRate: number;           // 0-1
  avgIterations: number;         // 平均返工次数
  avgDurationMs: number;
  sampleSize: number;
  lastUpdated: string;
  
  // 任务特定指标
  falsePositiveRate?: number;    // 对 review 类任务有意义
  missRate?: number;              // 对 review 类任务有意义
  userAcceptanceRate?: number;    // 用户接受率
}

interface DriftAlert {
  role: string;
  pluginId: string;
  metric: string;                // 'success_rate' | 'avg_iterations' | ...
  trend: 'DECLINING' | 'IMPROVING';
  change: number;                // 变化幅度
  windowDays: number;            // 检测窗口
  sinceDate: string;
  recommendation: string;
}

interface RoutingLesson {
  key: string;                   // "auth-change" / "large-refactor"
  condition: string;             // 自然语言描述
  recommendedTeam: TeamConfigRef;
  evidenceCount: number;
  confidence: number;            // 0-1
  learnedAt: string;
}
```

---

## 三、Evidence → Performance Signal 流程

### 3.1 三层过滤原则

```
原始 Log
   ↓ [过滤 1：完成度]
只有真正"完成"的任务进入统计
   
   ↓ [过滤 2：Evidence 验证]
只有通过独立 Evidence 验证的任务计入 success
   
   ↓ [过滤 3：Outcome 评估]
结合用户接受度、后续影响综合评分
```

### 3.2 完整流程

```typescript
async function recordTaskExecution(taskExecution: TaskExecution): Promise<void> {
  // 1. Evidence 验证
  const allEvidenceVerified = await verifyAllEvidence(taskExecution.steps);
  
  // 2. Outcome 评估
  const outcome = await evaluateOutcome(taskExecution);
  
  // 3. Performance Signal 计算
  const signal = {
    successRate: outcome.accepted ? 1.0 : 0.0,
    iterations: outcome.userEdits + outcome.rollbackCount,
    duration: taskExecution.totalDurationMs,
    evidenceVerified: allEvidenceVerified,
    userAccepted: outcome.accepted,
    followUpIssues: outcome.bugCount
  };
  
  // 4. 写入 Profile
  for (const step of taskExecution.steps) {
    if (step.role && step.pluginId) {
      await updateProjectProfile(taskExecution.projectId, step.role, step.pluginId, signal);
    }
  }
  
  // 5. Routing Lesson 提炼
  await extractRoutingLesson(taskExecution);
  
  // 6. Drift 检测
  await checkRoleDrift(taskExecution.projectId);
}
```

---

## 四、Profile 四层结构（v0.2 升级）

### 4.1 为什么需要四层

```
Global Profile
  → 冷启动默认值，所有项目共享
  
Project Profile（按 role）
  → "这个项目的 LEAD 角色，Codex 成功率 95%"
  
Task-Type Profile（按任务类型）
  → "这个项目的 parser_refactor，Codex 成功率 96%，但 db_migration 只有 72%"
  
Role History
  → 完整任务执行日志，用于分析 drift、提炼 lessons
```

**关键洞察**：同一个 Agent 在不同任务类型上的表现差异可能很大。

```
FormulaFix 项目：

Codex 全局（所有项目）：implementation 成功率 91%
  ↓
FormulaFix 项目级：LEAD 角色 成功率 95%
  ↓
FormulaFix 任务类型级：
  parser_refactor    → 96%  (12 次)
  auth_change        → 94%  (8 次)
  db_migration       → 72%  (5 次)   ← 数据库不如人类擅长
  architecture_redesign → 84% (7 次)
  test_generation    → 97%  (15 次)
```

路由时如果任务是"parser refactor"，用 Task-Type Profile 会给出更高置信度；
如果是"database migration"，会降低 Codex 评分，提示用户考虑人工介入。

### 4.2 最终 Suitability 计算（加权融合）

```typescript
function finalSuitability(
  pluginId: string,
  role: string,
  taskTypeId: TaskTypeId,
  profile: ProjectAgentProfile
): number {
  // ── 1. Task-Type 分数（权重最高，因为最精确）──
  const taskTypeRecord = profile.performanceByTaskType[pluginId]?.[taskTypeId];
  const taskTypeScore = weightedScore(taskTypeRecord, w: 0.35);
  
  // ── 2. Project-Role 分数（次高）──
  const projectRoleRecord = profile.performanceByRole[role]?.[pluginId];
  const projectRoleScore = weightedScore(projectRoleRecord, w: 0.30);
  
  // ── 3. Global 分数（兜底）──
  const globalRecord = profile.globalPerformance[pluginId];
  const globalScore = weightedScore(globalRecord, w: 0.20);
  
  // ── 4. Routing Lesson 加成（如果命中）──
  const lessonBonus = routingLessonBonus(pluginId, role, taskTypeId, profile);
  
  return taskTypeScore * 0.35
       + projectRoleScore * 0.30
       + globalScore * 0.20
       + lessonBonus;
}

/** 样本不足时平滑降级，避免噪声 */
function weightedScore(record: PerformanceRecord | undefined, w: number): number {
  if (!record || record.sampleSize === 0) return 0.5;  // 中性默认
  if (record.sampleSize < 3) return 0.5 + (record.successRate - 0.5) * 0.3;  // 低置信
  if (record.sampleSize < 5) return 0.5 + (record.successRate - 0.5) * 0.6;  // 中置信
  return record.successRate;  // 高置信
}

/** Routing Lesson 加成 */
function routingLessonBonus(
  pluginId: string,
  role: string,
  taskTypeId: TaskTypeId,
  profile: ProjectAgentProfile
): number {
  const matchingLesson = profile.routingLessons.find(
    l => l.pluginId === pluginId 
      && l.role === role 
      && l.taskTypeId === taskTypeId
      && l.confidence > 0.8
  );
  return matchingLesson ? matchingLesson.confidence * 0.15 : 0;
}
```

### 4.3 权重分配逻辑

| 数据层 | 权重 | 理由 |
|---|---|---|
| **Task-Type** | 35% | 最精确：同任务类型的历史最直接相关 |
| **Project-Role** | 30% | 重要：这个 Agent 在这个项目的这个角色上 |
| **Global** | 20% | 兜底：全局平均，样本多时可靠 |
| **Routing Lesson** | 15% | 加成：已验证的 Pattern 给额外加分 |

**总权重 = 100%**

### 4.4 样本不足的降级策略

```
sampleSize >= 10:  完全信任（full weight）
sampleSize 5-9:    70% 信任（0.7 × score + 0.3 × prior）
sampleSize 3-4:    50% 信任（0.5 × score + 0.5 × prior）
sampleSize 1-2:    20% 信任（0.2 × score + 0.8 × prior）
sampleSize = 0:    不使用此层（neutral 0.5）
```

---

## 五、Routing Lesson 自动提炼

### 5.1 触发时机

```
Task 完成 + Verification 通过
   ↓
分析任务的"模式指纹"
   ↓
关联到现有 lesson 或创建新 lesson
   ↓
更新 confidence
```

### 5.2 模式识别

```typescript
function extractPatterns(objective: string): Pattern[] {
  const lower = objective.toLowerCase();
  const patterns: Pattern[] = [];
  
  // 关键词分类
  if (lower.match(/auth|权限|登录|认证/)) {
    patterns.push({ category: 'auth', action: 'change' });
  }
  if (lower.match(/测试|test|e2e|unit/)) {
    patterns.push({ category: 'test', action: 'generate' });
  }
  if (lower.match(/重构|refactor/)) {
    patterns.push({ category: 'refactor', action: 'large_scale' });
  }
  if (lower.match(/安全|security|漏洞|vulnerab/)) {
    patterns.push({ category: 'security', action: 'review' });
  }
  if (lower.match(/api|接口|endpoint/)) {
    patterns.push({ category: 'api', action: 'design' });
  }
  if (lower.match(/文档|doc|readme/)) {
    patterns.push({ category: 'doc', action: 'generate' });
  }
  
  // 文件数特征（如果能从上下文获取）
  // if (input.fileCount > 50) patterns.push({ category: 'refactor', action: 'large_scale' });
  
  return patterns;
}
```

### 5.3 更新 Logic

```typescript
async function updateRoutingLesson(
  pattern: Pattern,
  taskExecution: TaskExecution
): Promise<void> {
  const lessonKey = `${pattern.category}-${pattern.action}`;
  let lesson = await loadLesson(lessonKey);
  
  if (lesson) {
    // 已有 lesson：更新
    const wasSuccessful = taskExecution.verification.score >= 0.8;
    lesson.evidenceCount++;
    lesson.confidence = bayesianUpdate(
      lesson.confidence,
      wasSuccessful,
      lesson.evidenceCount
    );
    lesson.recommendedTeam = extractTeamFromExecution(taskExecution);
    lesson.lastUpdated = new Date().toISOString();
  } else {
    // 新 lesson：初始化
    lesson = {
      key: lessonKey,
      condition: `Task involving ${pattern.category} ${pattern.action}`,
      recommendedTeam: extractTeamFromExecution(taskExecution),
      evidenceCount: 1,
      confidence: 0.5,
      learnedAt: new Date().toISOString()
    };
  }
  
  await saveLesson(lesson);
  
  // 通知用户
  if (lesson.evidenceCount === 1) {
    eventBus.emit('lesson.created', { lesson });
  } else if (lesson.evidenceCount === 5 || lesson.evidenceCount === 20) {
    eventBus.emit('lesson.matured', { lesson });
  }
}
```

### 5.4 Bayesian Confidence 更新

```typescript
function bayesianUpdate(
  prior: number,
  success: boolean,
  totalSamples: number
): number {
  // Beta-Binomial 模型简化版
  const alpha = prior * 10;
  const beta = (1 - prior) * 10;
  
  const newAlpha = alpha + (success ? 1 : 0);
  const newBeta = beta + (success ? 0 : 1);
  
  return newAlpha / (newAlpha + newBeta);
}
```

---

## 六、Team Recommendation

### 6.1 触发条件

```typescript
// 满足以下条件才生成推荐
shouldGenerateRecommendation(project: ProjectAgentProfile): boolean {
  return project.totalTasks >= 30         // 至少 30 次任务
      && project.lastRecommendationAt < Date.now() - 7 * 24 * 3600 * 1000;  // 7 天内未推荐
}
```

### 6.2 推荐生成

```typescript
async function generateRecommendation(projectId: string): Promise<Recommendation | null> {
  const profile = await loadProjectProfile(projectId);
  if (!shouldGenerateRecommendation(profile)) return null;
  
  const currentTeam = await loadCurrentTeam(projectId);
  const issues: RecommendationIssue[] = [];
  
  // 1. 找出明显不匹配的配置
  for (const [role, pluginScores] of Object.entries(profile.performanceByRole)) {
    for (const [pluginId, record] of Object.entries(pluginScores)) {
      // 当前 role 上的当前 plugin 表现差
      if (record.successRate < 0.7 && record.sampleSize >= 10) {
        // 找该 role 上的最佳 plugin
        const betterPlugin = findBestPluginForRole(role, profile);
        if (betterPlugin && betterPlugin !== pluginId) {
          issues.push({
            role,
            currentPlugin: pluginId,
            currentScore: record.successRate,
            currentSample: record.sampleSize,
            suggestedPlugin: betterPlugin.id,
            suggestedScore: betterPlugin.successRate,
            reasoning: `${pluginId} 在 ${role} 上的成功率仅 ${(record.successRate * 100).toFixed(0)}%，而 ${betterPlugin.id} 的成功率是 ${(betterPlugin.successRate * 100).toFixed(0)}%`
          });
        }
      }
    }
  }
  
  if (issues.length === 0) return null;
  
  return {
    type: 'TEAM_RECOMMENDATION',
    basedOn: {
      sampleSize: profile.totalTasks,
      periodDays: 30,
      projectMaturityDays: profile.projectAgeDays
    },
    issues,
    currentTeam,
    recommendedTeam: applyIssuesToTeam(currentTeam, issues),
    actions: ['APPLY', 'IGNORE', 'DETAILS']
  };
}
```

### 6.3 推荐展示（前端）

```
┌────────────────────────────────────────────────────┐
│  💡 TeamMind Recommendation                         │
│                                                    │
│  Based on 37 tasks in the last 30 days              │
│                                                    │
│  Issue 1: claude-code as Lead                       │
│  ────────────────────────────────────              │
│  Current performance: 72% (12 tasks)                 │
│  Better alternative: codex                          │
│  codex as Lead: 95% success (37 tasks)               │
│                                                    │
│  Issue 2: codex as Reviewer                         │
│  ────────────────────────────────────              │
│  Current performance: 81% (28 tasks)                 │
│  Better alternative: claude-code                     │
│  claude-code as Reviewer: 91% valid findings         │
│                                                    │
│  Recommended Team:                                  │
│  LEAD: codex                                       │
│  REVIEWER: claude-code                              │
│                                                    │
│  [Apply]  [Ignore]  [See Details]                   │
└────────────────────────────────────────────────────┘
```

---

## 七、Role Drift Detection

### 7.1 检测算法

```typescript
async function detectRoleDrift(projectId: string): Promise<DriftAlert[]> {
  const profile = await loadProjectProfile(projectId);
  const alerts: DriftAlert[] = [];
  
  for (const [role, pluginScores] of Object.entries(profile.performanceByRole)) {
    for (const [pluginId, history] of Object.entries(pluginScores)) {
      // 短期（30 天）vs 长期（90 天）对比
      const shortTerm = await getRecentPerformance(projectId, role, pluginId, 30);
      const longTerm = await getBaselinePerformance(projectId, role, pluginId, 90);
      
      if (shortTerm.sampleSize < 10 || longTerm.sampleSize < 10) continue;
      
      const drift = shortTerm.successRate - longTerm.successRate;
      
      if (Math.abs(drift) > 0.1) {
        alerts.push({
          role,
          pluginId,
          metric: 'success_rate',
          trend: drift > 0 ? 'IMPROVING' : 'DECLINING',
          change: drift,
          windowDays: 30,
          sinceDate: shortTerm.sinceDate,
          recommendation: generateDriftRecommendation(role, pluginId, drift)
        });
      }
    }
  }
  
  return alerts;
}
```

### 7.2 漂移通知

```
┌────────────────────────────────────────────────────┐
│  ⚠ Role Drift Detected                             │
│                                                    │
│  claude-code as Lead: ↓ 12% (last 30 days)          │
│  codex as Lead:      ↑ 9%  (last 30 days)          │
│                                                    │
│  Possible reasons:                                 │
│  - Codex model updates                              │
│  - Your codebase grew (Codex handles better)        │
│  - Different task types appearing                   │
│                                                    │
│  Recommendation:                                    │
│  Consider swapping LEAD role between claude-code    │
│  and codex based on recent performance.              │
│                                                    │
│  [Apply]  [Ignore]  [Investigate]                   │
└────────────────────────────────────────────────────┘
```

---

## 八、Project Memory（项目记忆）

### 8.1 三层记忆

```
Session Memory   →  当前任务的对话历史（短期）
Task Memory      →  历史任务的结果与产物（中期）
Project Memory   →  项目级路由 lessons + Profile（长期）★
```

### 8.2 Project Memory 内容

```typescript
interface ProjectMemory {
  projectId: string;
  
  // Agent Role History
  roleHistory: Array<{
    timestamp: string;
    role: string;
    pluginId: string;
    changeReason: string;        // 'manual' | 'recommendation' | 'drift_detection'
  }>;
  
  // 成功的模式
  successfulPatterns: Array<{
    pattern: string;            // "Codex implementation + Claude security review"
    evidenceCount: number;
    avgSuccessRate: number;
  }>;
  
  // 失败的模式
  failurePatterns: Array<{
    pattern: string;
    evidenceCount: number;
    avgSuccessRate: number;
  }>;
  
  // 项目特殊约束
  knownConstraints: string[];   // ["Flutter golden tests 需要本地字体"]
  
  // Routing Lessons（自动 + 手动）
  routingLessons: RoutingLesson[];
  
  // 用户偏好
  userPreferences: {
    preferredPluginForRole?: Record<string, string>;
    excludedPlugins: string[];
    lockedRoles: Array<{ roleId: string; pluginId: string }>;
  };
}
```

### 8.3 启动时的 Memory 应用

```
新 Task arrives
   ↓
1. 查询 project memory
   ↓
2. 匹配到 routing lesson？
   ├── 是 → 直接使用 lesson 推荐 team
   └── 否 → 走 capability routing 算法
   ↓
3. 应用 user preferences（locked roles）
   ↓
4. 输出最终 team config
```

---

## 九、用户控制权

### 9.1 三种控制粒度

```typescript
// 1. 完全自动：接受所有推荐
{
  autoApplyRecommendations: true,
  autoApplyDriftDetection: true
}

// 2. 半自动：收到推荐但需确认
{
  autoApplyRecommendations: false,  // 默认
  autoApplyDriftDetection: false
}

// 3. 锁定：完全不允许自动改
{
  lockedRoles: [
    { roleId: 'LEAD', pluginId: 'claude-code', reason: '我偏好 Claude' }
  ]
}
```

### 9.2 显式用户输入

```typescript
// 用户说："这个项目的 Lead 我想用 Codex"
await userPreferences.lockRole(projectId, 'LEAD', 'codex');

// 用户说："我觉得 Gemini CLI 在我们项目不行"
await userPreferences.excludePlugin(projectId, 'gemini-cli');

// 用户说："应用最新推荐"
await teamConfig.applyRecommendation(projectId, recommendation);
```

### 9.3 反馈循环

```typescript
// 用户对每个任务的结果给反馈
{
  taskId: 'T-101',
  userFeedback: {
    accepted: true,           // 用户接受结果
    rating: 4,                // 1-5
    notes: 'Codex 这次写得不错'
  }
}

// 这些反馈进入 Performance Signal
// 影响 Routing Lesson 的 confidence
```

---

## 十、飞轮效应

```
更多任务
   ↓
更多执行日志
   ↓
更多 Evidence
   ↓
更准确的 Profile
   ↓
更好的 Role Routing
   ↓
更高的任务成功率
   ↓
用户更多使用
   ↓
更多可靠数据
   ↓
更准确的 Team Recommendation
   ↓
用户更多信任
   ↓
更多任务
```

**这是 TeamMind 真正的护城河**：
- CLI 适配 → 可复制
- Plugin Runtime → 可复制
- **项目级 Performance Profile + Routing Lessons + Role History** → **不可复制**

---

## 十一、与其他文档的关系

| 文档 | 关系 |
|---|---|
| [core-model.md](core-model.md) | 数据结构 |
| [plugin-system.md](plugin-system.md) | Runtime 实现 |
| [capability-routing.md](capability-routing.md) | 路由算法 |
| [agent-philosophy-matrix.md](agent-philosophy-matrix.md) | 各 CLI 哲学（待写） |

---

## 十二、未来扩展

### 12.1 v0.2：跨项目学习

```
Project A 学会了 "auth change 走 Claude 安全审查"
Project B 也在做 auth change
→ TeamMind 建议 Project B 也用类似配置
→ 但只作为"初始建议"，用户可调整
```

### 12.2 v0.3：团队模板市场

```
用户 A 创建了 "金融项目 AI 团队" 模板
表现很好
→ 用户 A 发布到模板市场
→ 其他用户可一键导入
```

### 12.3 v1.0：多用户协作

```
团队共享 Project Memory
  ↓
A 做的改动 B 也能看到
  ↓
集体智能
```

---

**版本**：v0.1 Draft
**最后更新**：2026-08-14