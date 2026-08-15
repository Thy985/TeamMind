# TeamMind Web UI 信息架构

> Mission Control 设计：把"对话"和"状态"分开，让用户随时观察、干预、接管。

---

## 一、核心设计原则

### 1.1 不是聊天机器人，是 Mission Control

```
❌ 错误（聊天机器人模式）：
┌──────────────────────────────┐
│  User: 重构 auth             │
│  Claude: 好的，我来...        │
│  User: 继续...               │
│  Codex: 已完成               │
└──────────────────────────────┘
  → 用户看到的是文本流
  → 看不出真实状态

✅ 正确（Mission Control 模式）：
┌──────────────────────────────────────┐
│  TASK                                  │
│  重构 auth 为 JWT                      │
├──────────────────────────────────────┤
│  TEAM STATUS                           │
│  🔵 Codex (LEAD)      Implementing   │
│  ⚪ Claude (SECURITY) Waiting         │
│  ⚪ Aider  (TESTER)   Waiting         │
├──────────────────────────────────────┤
│  LIVE EVENTS                         │
│  08:22 Codex modified auth.ts        │
│  08:23 Codex tests passed            │
│  08:24 Claude reviewing              │
├──────────────────────────────────────┤
│  ARTIFACTS      TESTS      REVIEW     │
│  7 files        42 PASS    0 findings │
├──────────────────────────────────────┤
│  CONVERSATION                          │
│  [User ↔ Lead only]                   │
└──────────────────────────────────────┘
```

### 1.2 对话 ≠ 状态

```
对话（Conversation）：
  用户 ↔ Lead Agent 的沟通层
  只保留关键交互（提交任务 / 提问 / 审批）
  
状态（State）：
  TaskState / AgentState / ArtifactState / VerificationState / RoutingState
  实时反映系统真实状态
```

---

## 二、页面架构

### 2.1 导航结构

```
TeamMind
├── 🏠 Dashboard                    # 项目列表 + 概览
├── 📁 Projects
│   └── {projectName}
│       ├── ▶️ Active Task           # 当前任务（Mission Control）
│       ├── 📋 History               # 历史任务
│       ├── 👥 Team Config           # 团队配置
│       ├── 🧠 Project Memory        # 共享状态 / Decision / Lessons
│       ├── 📊 Profile               # Agent Performance Profile
│       └── ⚙️ Settings              # 项目设置
├── 🔌 CLI Management                # CLI 探测 + Plugin 状态
└── ⚙️ Global Settings
```

### 2.2 Dashboard 页面

```
┌──────────────────────────────────────────────────────────────────┐
│  TeamMind                    🔍 [搜索项目...]          👤 Admin  │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  最近项目                                                         │
│  ┌─────────────────────┐  ┌─────────────────────┐               │
│  │ 🛒 电商后端          │  │ 🧮 FormulaFix        │               │
│  │                     │  │                     │               │
│  │ Team: High Assurance│  │ Team: Balanced       │               │
│  │ Lead: Codex         │  │ Lead: Claude Code    │               │
│  │ Last: 2h ago        │  │ Last: 30m ago       │               │
│  │ 37 tasks, 94% success│ │ 12 tasks, 88%       │               │
│  │                     │  │                     │               │
│  │ [▶ Continue]        │  │ [▶ Continue]        │               │
│  └─────────────────────┘  └─────────────────────┘               │
│                                                                   │
│  快速操作                                                         │
│  [+ 新建项目]  [📥 导入项目]                                       │
│                                                                   │
├──────────────────────────────────────────────────────────────────┤
│  活跃任务                                                         │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  FormulaFix — 修复 LaTeX 渲染错位                        │    │
│  │  Codex (LEAD) ✅  Claude (REVIEW) ⏳  Aider (TESTER) ⏳  │    │
│  │  [▶️ 查看]                                                │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                   │
│  推荐                                                             │
│  💡 FormulaFix 基于历史表现，建议将 LEAD 从 Claude 切换到 Codex   │
│  [查看建议]  [忽略]                                               │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### 2.3 Mission Control（核心页面）

这是最重要的页面。用户 80% 的时间在这里。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ← 返回    📁 FormulaFix                              团队: High Assurance  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ━━━ TASK ━━━                                                              │
│  修复 LaTeX 渲染错位问题，不破坏 E2E 测试                                   │
│  [修改任务] [暂停] [取消]                                                   │
│                                                                             │
│  ━━━ TEAM STATUS ━━━                                                       │
│                                                                             │
│   ┌─────────────────────┐   ┌─────────────────────┐   ┌────────────────┐  │
│   │  🔵 CODEX           │   │  ⚪ CLAUDE CODE      │   │  ⚪ AIDER      │  │
│   │  LEAD               │   │  SECURITY_GATE       │   │  TESTER        │  │
│   │                     │   │                      │   │                │  │
│   │  ⟳ Analyzing...     │   │  Waiting             │   │  Waiting       │  │
│   │  last: 3s ago       │   │                      │   │                │  │
│   │  [▶ View]           │   │                      │   │                │  │
│   └─────────────────────┘   └─────────────────────┘   └────────────────┘  │
│                                                                             │
│  ━━━ LIVE EVENTS ━━━                                                       │
│                                                                             │
│  08:24:03  Codex      开始分析 auth 模块                                     │
│  08:24:16  Codex      读取 src/auth/session.ts                              │
│  08:24:22  Codex      修改 src/auth/jwt.ts   (+47 -12)                      │
│  08:24:45  Tests       运行 npm test...                                     │
│  08:25:01  Tests       ✅ 31 passed, 0 failed (15.2s)                       │
│  08:25:03  Routing     → Claude Code (security review)                      │
│                                                                             │
│  ━━━ ARTIFACTS ━━━                                                          │
│                                                                             │
│  ┌─ Code Diff ──────────────────────────┐  ┌─ Test Report ──────────────┐  │
│  │ src/auth/jwt.ts                      │  │ Framework: jest            │  │
│  │ @@ -12,7 +12,7 @@                    │  │ Total:  31                 │  │
│  │ -session-based-auth                  │  │ Passed: 31 ✅                │  │
│  │ +jwt-auth-with-refresh-token         │  │ Failed: 0                    │  │
│  │                                      │  │ Duration: 15.2s             │  │
│  │ [View Full Diff →]                   │  │ [View Details →]             │  │
│  └──────────────────────────────────────┘  └──────────────────────────────┘  │
│                                                                             │
│  ━━━ APPROVALS ━━━ (仅在有审批需求时显示)                                   │
│                                                                             │
│  ⚠️ 需要审批                                                               │
│  Codex 准备修改 src/config/security.ts                                     │
│  此文件包含生产密钥配置                                                     │
│  [✅ 批准]  [❌ 拒绝]  [💬 提问]                                            │
│                                                                             │
│  ━━━ CONVERSATION ━━━                                                      │
│                                                                             │
│  你:  修复 LaTeX 渲染错位问题，不破坏 E2E 测试                               │
│  Codex: 好的，我来分析并实现。先读取现有代码...                             │
│  [直接提问 Lead Agent →]                                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.4 Team Config 页面

```
┌─────────────────────────────────────────────────────────────────────┐
│  ← 返回    📁 FormulaFix / Team Config                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  团队 Profile: [High Assurance ▼]                                   │
│                                                                     │
│  Lead Engineer                                                      │
│  ┌─────────────────────────────────────────────────────┐           │
│  │  🎯 Codex                                            │           │
│  │  执行导向 / 迭代构建 / 测试闭环                       │           │
│  │  成功率: 94% (37 tasks)    平均耗时: 11.2min          │           │
│  │  [更换]  [查看详情]                                    │           │
│  └─────────────────────────────────────────────────────┘           │
│                                                                     │
│  Security Reviewer                                                  │
│  ┌─────────────────────────────────────────────────────┐           │
│  │  🔒 Claude Code                                      │           │
│  │  安全导向 / 显式审批 / 沙箱执行                       │           │
│  │  有效发现率: 91%    误报率: 4%                        │           │
│  │  [更换]  [查看详情]                                    │           │
│  └─────────────────────────────────────────────────────┘           │
│                                                                     │
│  Test Engineer                                                      │
│  ┌─────────────────────────────────────────────────────┐           │
│  │  🧪 Codex                                            │           │
│  │  成功率: 95% (31 tasks)                               │           │
│  │  [更换]  [查看详情]                                    │           │
│  └─────────────────────────────────────────────────────┘           │
│                                                                     │
│  ─────────────────────────────────────────────────────────────────  │
│                                                                     │
│  自定义角色                                                          │
│  [+ 添加角色]                                                        │
│                                                                     │
│  行为模式:                                                          │
│  ○ Autopilot（自动执行，高风险时暂停）                               │
│  ● Supervised（关键节点需确认）                                      │
│  ○ Manual（每一步都需确认）                                          │
│                                                                     │
│  [保存配置]                                                         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.5 History 页面

```
┌─────────────────────────────────────────────────────────────────────┐
│  ← 返回    📁 FormulaFix / History                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  筛选: [所有状态 ▼] [最近 30 天 ▼] [搜索任务...]                    │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │ T-101  修复 LaTeX 渲染错位              ✅ 完成  95%  2h ago  │  │
│  │   Codex(LEAD) → Claude(REVIEW) → Aider(TEST)                 │  │
│  │   7 files changed, 42 tests passed                           │  │
│  │   [查看]  [重新执行]  [对比]                                  │  │
│  ├─────────────────────────────────────────────────────────────┤  │
│  │ T-100  添加公式预览功能               ✅ 完成  88%  1d ago    │  │
│  │   Codex(LEAD) → Codex(TEST)                                  │  │
│  │   3 files changed, 28 tests passed                           │  │
│  │   [查看]  [重新执行]                                          │  │
│  ├─────────────────────────────────────────────────────────────┤  │
│  │ T-99   重构 auth 模块                  ⚠ 需决策  3d ago      │  │
│  │   Claude(LEAD) → Codex(REVIEW)                               │  │
│  │   用户介入：批准安全配置修改                                   │  │
│  │   [查看]  [重新执行]                                          │  │
│  ├─────────────────────────────────────────────────────────────┤  │
│  │ T-98   添加单元测试                   ❌ 失败  5d ago         │  │
│  │   Codex(LEAD) → Tests failed (3/42)                          │  │
│  │   [查看]  [重新执行]                                          │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  共 42 条记录  每页 20 条  < 1 2 3 ... >                            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.6 Project Memory 页面

```
┌─────────────────────────────────────────────────────────────────────┐
│  ← 返回    📁 FormulaFix / Project Memory                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  🧠 Shared Context                                                  │
│  ────────────────────────────────────────────────────────────────   │
│  Architecture:  React 18 + MathJax 3 + Express backend              │
│  Coding rules:  [禁止 any, 必走 Result<T>, 测试覆盖率 > 80%]         │
│  ADRs:                                                                    │
│    • ADR-001: JWT over session (2026-07-01)                           │
│    • ADR-002: React Query for data fetching (2026-06-15)              │
│  [编辑 Context]                                                        │
│                                                                     │
│  📜 Decisions (共 8)                                                 │
│  ────────────────────────────────────────────────────────────────   │
│  • 选择 JWT 而非 Session（Claude, 2026-08-10）                         │
│  • 使用 React Query 管理服务端状态（Codex, 2026-08-05）                │
│  [查看所有]                                                            │
│                                                                     │
│  🎯 Routing Lessons (共 5)                                           │
│  ────────────────────────────────────────────────────────────────   │
│  • auth-change → LEAD=Codex, SECURITY=Claude  (confidence: 0.92)    │
│  • large-refactor → LEAD=Aider, REVIEW=Claude  (confidence: 0.78)   │
│  • test-generation → TESTER=Codex  (confidence: 0.85)               │
│  [编辑 Lessons]                                                      │
│                                                                     │
│  📊 Agent Performance                                                │
│  ────────────────────────────────────────────────────────────────   │
│  Codex:           37 tasks | 94% success | 11.2min avg              │
│  Claude Code:     23 tasks | 91% success | 14.5min avg              │
│  Aider:           15 tasks | 88% success | 8.3min avg               │
│  [详细报告]                                                            │
│                                                                     │
│  ⚠️ Drift Alerts                                                     │
│  ────────────────────────────────────────────────────────────────   │
│  • Claude LEAD performance ↓12% (last 30 days)                       │
│    建议：考虑将 LEAD 角色从 Claude 切换到 Codex                        │
│  [处理]  [忽略]                                                      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 三、组件树

### 3.1 核心组件

```
src/views/
├── ProjectListView.vue              # 项目列表
├── ProjectDetailView.vue            # 项目详情（路由到子页面）
├── task/
│   ├── TaskDetailView.vue           # Mission Control 主页面
│   │   ├── TaskHeader.vue           # 任务标题 + 操作按钮
│   │   ├── TeamStatus.vue           # Agent 状态卡片（动态网格）
│   │   │   ├── AgentCard.vue        # 单个 Agent 卡片
│   │   │   └── AgentStatusBadge.vue # 状态指示器
│   │   ├── LiveEvents.vue           # 实时事件时间线
│   │   │   └── EventRow.vue         # 单个事件行
│   │   ├── ArtifactsPanel.vue       # 产物面板（可切换 tab）
│   │   │   ├── CodeDiffView.vue     # diff 查看器
│   │   │   ├── TestReportView.vue   # 测试报告
│   │   │   └── ReviewFindingsView.vue # 审查发现
│   │   ├── ApprovalPanel.vue        # 审批面板（条件显示）
│   │   ├── ConversationPanel.vue    # 对话面板
│   │   └── TaskFooter.vue           # 底部操作栏
│   └── TaskHistoryView.vue          # 历史任务列表
├── team/
│   ├── TeamConfigView.vue           # 团队配置
│   │   ├── RoleSelector.vue         # 角色选择器
│   │   ├── AgentPicker.vue          # Agent 选择
│   │   └── ModeSelector.vue         # 控制模式选择
│   └── TeamProfileViewer.vue        # Agent Profile 查看
├── memory/
│   ├── SharedContextView.vue        # 共享上下文
│   ├── DecisionsView.vue            # 决策记录
│   ├── RoutingLessonsView.vue       # Routing Lessons
│   └── PerformanceView.vue          # Performance 可视化
└── settings/
    ├── ProjectSettingsView.vue
    └── GlobalSettingsView.vue

src/components/
├── MissionControlCanvas.vue         # 顶部 Mission Control 概览
├── AgentTimeline.vue                # Agent 时间线（横向）
├── DiffViewer.vue                   # Diff 对比查看器
├── FindingCard.vue                  # 审查发现卡片
├── ApprovalModal.vue                # 审批弹窗
├── ControlModeBanner.vue            # 控制模式横幅
├── DriftAlertBanner.vue             # 漂移告警横幅
└── RecommendationBanner.vue         # 推荐横幅

src/stores/
├── project.ts                       # 项目状态
├── task.ts                          # 任务状态（含 WebSocket 订阅）
├── agent.ts                         # Agent 状态
├── artifact.ts                      # Artifact 状态
└── recommendation.ts                # 推荐状态

src/api/
├── project.ts
├── task.ts
└── plugin.ts

src/services/
├── websocket.ts                     # WebSocket 连接管理
├── eventDecoder.ts                  # 事件解码（标准协议 → Vue 响应式数据）
└── eventDispatcher.ts               # 事件分发（根据 type 路由到不同 store）
```

---

## 四、WebSocket 事件处理

### 4.1 连接管理

```typescript
// src/services/websocket.ts
class TeamMindWebSocket {
  private ws: WebSocket | null = null;
  private reconnectTimer: number | null = null;
  private eventHandlers = new Map<string, Set<Function>>();
  
  connect(projectId: string) {
    this.ws = new WebSocket(`ws://localhost:8080/ws/tasks?projectId=${projectId}`);
    
    this.ws.onopen = () => {
      // 订阅所有事件类型
      this.send({ type: 'subscribe', channel: 'tasks', filters: {} });
    };
    
    this.ws.onmessage = (event) => {
      const frame = JSON.parse(event.data);
      this.dispatch(frame.payload);
    };
    
    this.ws.onclose = () => this.scheduleReconnect();
  }
  
  private dispatch(event: TeamMindEvent) {
    const handlers = this.eventHandlers.get(event.type) || [];
    for (const handler of handlers) {
      handler(event);
    }
    // 也路由到对应 store
    eventDispatcher.handle(event);
  }
  
  on(type: EventType, handler: Function) {
    if (!this.eventHandlers.has(type)) {
      this.eventHandlers.set(type, new Set());
    }
    this.eventHandlers.get(type)!.add(handler);
  }
}
```

### 4.2 Event Dispatcher（分发到 Store）

```typescript
// src/services/eventDispatcher.ts
class EventDispatcher {
  handle(event: TeamMindEvent) {
    switch (event.type) {
      // 任务状态
      case 'task.started':
        taskStore.onTaskStarted(event);
        break;
      case 'task.completed':
        taskStore.onTaskCompleted(event);
        break;
      case 'task.failed':
        taskStore.onTaskFailed(event);
        break;
      
      // Agent 状态
      case 'agent.started':
        agentStore.onAgentStarted(event);
        break;
      case 'agent.chunk':
        agentStore.onAgentChunk(event);
        break;
      case 'agent.completed':
        agentStore.onAgentCompleted(event);
        break;
      
      // 事件
      case 'tool.called':
        agentStore.onToolCalled(event);
        break;
      case 'file.changed':
        artifactStore.onFileChanged(event);
        break;
      case 'artifact.created':
        artifactStore.onArtifactCreated(event);
        break;
      
      // 验证
      case 'evidence.verified':
        verificationStore.onEvidenceVerified(event);
        break;
      case 'test.result':
        verificationStore.onTestResult(event);
        break;
      
      // 审查
      case 'finding.created':
        reviewStore.onFindingCreated(event);
        break;
      case 'review.approved':
        reviewStore.onReviewApproved(event);
        break;
      
      // 路由
      case 'routing.decided':
        routingStore.onRoutingDecided(event);
        break;
      
      // 审批
      case 'approval.required':
        approvalStore.onApprovalRequired(event);
        break;
      case 'approval.granted':
        approvalStore.onApprovalGranted(event);
        break;
      
      // 进化
      case 'profile.updated':
        evolutionStore.onProfileUpdated(event);
        break;
      case 'drift.detected':
        evolutionStore.onDriftDetected(event);
        break;
      case 'recommendation.generated':
        evolutionStore.onRecommendationGenerated(event);
        break;
    }
  }
}
```

### 4.3 Task Store（状态管理）

```typescript
// src/stores/task.ts
export const useTaskStore = defineStore('task', {
  state: () => ({
    currentTask: null as TaskState | null,
    taskHistory: [] as TaskRecord[],
    liveEvents: [] as LiveEvent[],
    maxEvents: 200  // 内存限制
  }),
  
  getters: {
    state: (state) => state.currentTask?.state,
    isRunning: (state) => 
      state.currentTask?.state === 'EXECUTING' ||
      state.currentTask?.state === 'VERIFYING' ||
      state.currentTask?.state === 'REVIEWING' ||
      state.currentTask?.state === 'ORCHESTRATING',
    liveEventCount: (state) => state.liveEvents.length,
    recentFindings: (state) => 
      state.liveEvents.filter(e => e.type === 'finding.created').slice(-5)
  },
  
  actions: {
    onTaskStarted(event: TeamMindEvent) {
      this.currentTask = {
        id: event.taskId,
        state: 'SUBMITTED',
        objective: event.metadata.objective,
        startedAt: new Date(event.timestamp),
        steps: []
      };
      this.addLiveEvent(event);
    },
    
    onAgentChunk(event: TeamMindEvent) {
      const task = this.currentTask;
      if (task?.state === 'EXECUTING') {
        const step = task.steps.find(s => s.pluginId === event.pluginId);
        if (step) {
          step.outputChunks.push(event.metadata.content);
        }
      }
      this.addLiveEvent(event);
    },
    
    addLiveEvent(event: TeamMindEvent) {
      this.liveEvents.push({
        timestamp: event.timestamp,
        type: event.type,
        pluginId: event.pluginId,
        role: event.role,
        summary: this.extractSummary(event)
      });
      // 保持上限
      if (this.liveEvents.length > this.maxEvents) {
        this.liveEvents = this.liveEvents.slice(-this.maxEvents);
      }
    },
    
    extractSummary(event: TeamMindEvent): string {
      switch (event.type) {
        case 'agent.chunk':
          return `${event.pluginId}: ${event.metadata.content.slice(0, 50)}...`;
        case 'tool.called':
          return `${event.pluginId}: calling ${event.metadata.toolName}`;
        case 'file.changed':
          return `${event.pluginId}: modified ${event.metadata.filePath}`;
        case 'test.result':
          const m = event.metadata;
          return `Tests: ${m.passed}/${m.total} passed`;
        case 'routing.decided':
          return `→ ${event.metadata.toAgent} (${event.metadata.toRole})`;
        case 'approval.required':
          return `⚠ ${event.metadata.question}`;
        default:
          return event.type;
      }
    }
  }
});
```

---

## 五、Mission Control Canvas（顶部概览）

### 5.1 实时 Agent 状态卡片

```vue
<!-- AgentCard.vue -->
<template>
  <div 
    class="agent-card"
    :class="[
      `status-${status}`,
      agent.role,
      { 'has-approval': hasPendingApproval }
    ]"
  >
    <!-- 状态指示灯 -->
    <div class="status-dot" :class="statusClass" />
    
    <!-- Agent 图标 -->
    <div class="agent-icon">{{ agent.icon }}</div>
    
    <!-- 名称 + Role -->
    <div class="agent-info">
      <div class="agent-name">{{ agent.name }}</div>
      <div class="agent-role">{{ agent.role }}</div>
    </div>
    
    <!-- 当前状态 -->
    <div class="agent-state">
      <span v-if="status === 'running'" class="spinner" />
      {{ stateLabel }}
    </div>
    
    <!-- 进度指示 -->
    <div v-if="hasProgress" class="progress-bar">
      <div class="progress-fill" :style="{ width: progress + '%' }" />
    </div>
    
    <!-- 审批徽标 -->
    <div v-if="hasPendingApproval" class="approval-badge">⚠</div>
    
    <!-- 操作按钮 -->
    <div class="agent-actions">
      <button @click="pauseAgent" title="暂停">⏸</button>
      <button @click="takeOver" title="接管">🎮</button>
      <button @click="viewDetails" title="详情">👁</button>
    </div>
  </div>
</template>

<script setup>
// 状态映射
const statusClass = computed(() => {
  switch (props.agent.state) {
    case 'running': return 'running';
    case 'thinking': return 'thinking';
    case 'waiting': return 'waiting';
    case 'error': return 'error';
    case 'done': return 'done';
    default: return 'idle';
  }
});

const stateLabel = computed(() => {
  switch (props.agent.state) {
    case 'running': return props.agent.currentAction || '正在执行...';
    case 'thinking': return '思考中...';
    case 'waiting': return '等待中';
    case 'error': return '出错';
    case 'done': return '完成';
    default: return '-';
  }
});
</script>
```

### 5.2 CSS 状态样式

```css
/* Agent 状态颜色 */
.status-running { border-color: #22c55e; box-shadow: 0 0 12px rgba(34,197,94,0.3); }
.status-thinking { border-color: #3b82f6; box-shadow: 0 0 12px rgba(59,130,246,0.3); }
.status-waiting { border-color: #94a3b8; }
.status-error { border-color: #ef4444; box-shadow: 0 0 12px rgba(239,68,68,0.3); }
.status-done { border-color: #22c55e; }
.status-idle { border-color: #e2e8f0; }

/* 状态灯 */
.status-dot.running { background: #22c55e; animation: pulse 1.5s infinite; }
.status-dot.thinking { background: #3b82f6; animation: pulse 2s infinite; }
.status-dot.waiting { background: #94a3b8; }
.status-dot.error { background: #ef4444; }
.status-dot.done { background: #22c55e; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* 审批徽标 */
.approval-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  background: #f59e0b;
  color: white;
  border-radius: 50%;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  cursor: pointer;
  animation: bounce 1s infinite;
}
```

---

## 六、Live Events 时间线

### 6.1 事件渲染策略

```typescript
// 不同事件类型的图标和颜色
const EVENT_STYLES: Record<string, { icon: string; color: string; bg: string }> = {
  'task.started':    { icon: '▶', color: '#22c55e', bg: 'bg-green-50' },
  'task.completed':  { icon: '✓', color: '#22c55e', bg: 'bg-green-50' },
  'task.failed':     { icon: '✗', color: '#ef4444', bg: 'bg-red-50' },
  
  'agent.started':   { icon: '🤖', color: '#3b82f6', bg: 'bg-blue-50' },
  'agent.chunk':     { icon: '💬', color: '#64748b', bg: 'bg-gray-50' },
  'agent.completed': { icon: '✓', color: '#22c55e', bg: 'bg-green-50' },
  'agent.failed':    { icon: '✗', color: '#ef4444', bg: 'bg-red-50' },
  
  'tool.called':     { icon: '🔧', color: '#8b5cf6', bg: 'bg-violet-50' },
  'tool.result':     { icon: '📤', color: '#06b6d4', bg: 'bg-cyan-50' },
  
  'file.changed':    { icon: '📝', color: '#f59e0b', bg: 'bg-amber-50' },
  'artifact.created':{ icon: '📦', color: '#10b981', bg: 'bg-emerald-50' },
  
  'test.started':    { icon: '🧪', color: '#ec4899', bg: 'bg-pink-50' },
  'test.passed':     { icon: '✅', color: '#22c55e', bg: 'bg-green-50' },
  'test.failed':     { icon: '❌', color: '#ef4444', bg: 'bg-red-50' },
  
  'evidence.verified':    { icon: '🔍', color: '#22c55e', bg: 'bg-green-50' },
  'evidence.failed':      { icon: '⚠', color: '#f59e0b', bg: 'bg-amber-50' },
  
  'finding.created':      { icon: '🔴', color: '#ef4444', bg: 'bg-red-50' },
  'finding.resolved':     { icon: '🟢', color: '#22c55e', bg: 'bg-green-50' },
  'review.approved':      { icon: '👍', color: '#22c55e', bg: 'bg-green-50' },
  'review.rejected':      { icon: '👎', color: '#ef4444', bg: 'bg-red-50' },
  
  'routing.decided':      { icon: '🔄', color: '#3b82f6', bg: 'bg-blue-50' },
  'handoff.requested':    { icon: '⇄', color: '#8b5cf6', bg: 'bg-violet-50' },
  
  'approval.required':    { icon: '⚠', color: '#f59e0b', bg: 'bg-amber-100', bold: true },
  'approval.granted':     { icon: '✅', color: '#22c55e', bg: 'bg-green-50' },
  'approval.denied':      { icon: '❌', color: '#ef4444', bg: 'bg-red-50' },
  
  'profile.updated':      { icon: '📊', color: '#6366f1', bg: 'bg-indigo-50' },
  'drift.detected':       { icon: '⚡', color: '#f59e0b', bg: 'bg-amber-100', bold: true },
  'recommendation.generated': { icon: '💡', color: '#10b981', bg: 'bg-emerald-50' },
  
  'error.critical':       { icon: '🚨', color: '#ef4444', bg: 'bg-red-100', bold: true },
};
```

### 6.2 时间线组件

```vue
<!-- LiveEvents.vue -->
<template>
  <div class="live-events">
    <div class="events-header">
      <span>LIVE EVENTS</span>
      <span class="event-count">{{ events.length }}</span>
      <button @click="clearEvents">清除</button>
    </div>
    
    <div class="events-list" ref="eventsList">
      <div
        v-for="event in events"
        :key="`${event.timestamp}-${event.type}-${event.pluginId}`"
        class="event-row"
        :class="[
          EVENT_STYLES[event.type]?.bg || 'bg-gray-50',
          { 'event-highlight': event.isImportant }
        ]"
      >
        <span class="event-time">{{ formatTime(event.timestamp) }}</span>
        <span class="event-icon">{{ EVENT_STYLES[event.type]?.icon || '•' }}</span>
        <span class="event-plugin">{{ event.pluginId }}</span>
        <span class="event-type">{{ formatEventType(event.type) }}</span>
        <span class="event-summary">{{ event.summary }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue';

const props = defineProps<{ events: LiveEvent[] }>();
const eventsList = ref(null);

// 自动滚动到底部
watch(() => props.events.length, async () => {
  await nextTick();
  if (eventsList.value) {
    eventsList.value.scrollTop = eventsList.value.scrollHeight;
  }
});

const formatTime = (timestamp: number) => {
  const d = new Date(timestamp);
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
};

const formatEventType = (type: string) => {
  return type.replace('.', ' → ');
};
</script>
```

---

## 七、Control Mode Banner

```vue
<!-- ControlModeBanner.vue -->
<template>
  <div class="control-mode-banner" :class="`mode-${mode}`">
    <span class="mode-indicator">
      <span v-if="mode === 'AUTOMATED'">🤖</span>
      <span v-else-if="mode === 'SUPERVISED'">👁</span>
      <span v-else>🎮</span>
      {{ modeLabel }}
    </span>
    
    <span class="mode-description">{{ modeDescription }}</span>
    
    <div class="mode-switcher">
      <button 
        v-for="m in modes" 
        :key="m.value"
        :class="{ active: currentMode === m.value }"
        @click="switchMode(m.value)"
      >
        {{ m.label }}
      </button>
    </div>
    
    <span v-if="pendingApprovals > 0" class="approval-count">
      ⚠ {{ pendingApprovals }} 待审批
    </span>
  </div>
</template>
```

---

## 八、审批面板

```vue
<!-- ApprovalPanel.vue -->
<template>
  <div v-if="activeApprovals.length > 0" class="approval-panel">
    <div class="panel-header">
      <span>⚠ 需要审批</span>
      <span class="count">{{ activeApprovals.length }}</span>
    </div>
    
    <div
      v-for="approval in activeApprovals"
      :key="approval.id"
      class="approval-item"
      :class="`severity-${approval.severity}`"
    >
      <div class="approval-context">
        <strong>{{ approval.pluginId }}</strong> 请求审批
      </div>
      <div class="approval-question">{{ approval.question }}</div>
      <div v-if="approval.context.diff" class="approval-diff">
        <pre>{{ approval.context.diff }}</pre>
      </div>
      <div class="approval-actions">
        <button class="btn-approve" @click="approve(approval.id)">✅ 批准</button>
        <button class="btn-deny" @click="deny(approval.id)">❌ 拒绝</button>
        <button class="btn-question" @click="question(approval.id)">💬 提问</button>
        <button class="btn-skip" @click="skip(approval.id)">⏭ 跳过此步</button>
      </div>
      <div class="approval-timer">
        超时: {{ formatRemaining(approval.timeoutRemaining) }}
      </div>
    </div>
  </div>
</template>
```

---

## 九、关键 UX 设计决策

### 9.1 为什么不用传统聊天 UI

| 传统聊天 UI | Mission Control UI |
|---|---|
| 文本流 | 状态面板 |
| 所有信息混在一起 | 信息分层（Task / Team / Events / Artifacts / Conversation）|
| 被动阅读 | 主动干预（审批 / 接管 / 重定向）|
| 看不到结构 | 看到整个团队结构 |
| 不区分对话和状态 | 对话和状态完全分离 |

### 9.2 为什么不用自由画布

| 自由画布（n8n / LangGraph） | Mission Control |
|---|---|
| 用户自己画 workflow | Runtime 自动编排 |
| 用户需要理解依赖关系 | 用户只管理团队 |
| 学习成本高 | 开箱即用 |
| 适合技术用户 | 适合所有开发者 |
| 可视化"怎么执行" | 可视化"正在发生什么" |

**画布只用于观察，不用于编排**。

### 9.3 用户干预的层次

```
Level 1: Observe（观察）
  → 随时查看任务进度、Agent 状态、实时事件流
  
Level 2: Interrupt（中断）
  → 暂停当前 Agent
  → 取消整个任务
  → 查看当前 Agent 上下文
  
Level 3: Redirect（重定向）
  → 切换当前负责的 Agent
  → 修改任务目标
  → 调整团队配置
  
Level 4: Take Over（接管）
  → 直接跟某个 Agent 对话
  → 手动执行下一步
  → 成为临时的 "Lead"
```

---

## 十、与后端协议的对应

| 前端需求 | 后端事件 | 后端 State |
|---|---|---|
| 显示当前 Agent 状态 | `agent.started/chunk/completed` | TaskStep.status |
| 显示团队拓扑 | `routing.decided` | TeamConfig.roles |
| 显示实时事件流 | 所有事件类型 | Runtime Event Bus |
| 显示产物 | `artifact.created` | Artifact entity |
| 审批面板 | `approval.required/granted/denied` | Approval entity |
| 性能更新 | `profile.updated` | PerformanceRecord |
| 漂移告警 | `drift.detected` | DriftAlert |
| 推荐 | `recommendation.generated` | Recommendation |

---

## 十一、实施顺序建议

| 优先级 | 页面 | 理由 |
|---|---|---|
| P0 | Mission Control 基础版 | 核心体验 |
| P1 | Team Config + 控制模式切换 | 产品差异化 |
| P2 | History + 任务详情 | 可追溯性 |
| P3 | Project Memory | 长期价值 |
| P4 | Dashboard | 全局概览 |

---

**版本**：v0.1 Draft
**最后更新**：2026-08-14