# TeamMind Plugin System Design (v0.1 Draft)

> Cordis-like Plugin Runtime：让 TeamMind Core 保持极小，能力全部外置成可插拔模块。

---

## 一、设计目标

| 目标 | 说明 |
|---|---|
| **核心极小** | Core 只懂 Plugin 接口，不懂具体实现 |
| **能力外置** | 任何能力都是 Plugin，可以独立添加/替换/删除 |
| **可发现** | Plugin 自动注册自己的 Capability |
| **可观测** | Plugin 生命周期全程监控 |
| **可测试** | Plugin 接口可独立 mock |
| **可降级** | Plugin 失败时不影响其他 Plugin |

---

## 二、为什么是 Cordis-like

### 2.1 借鉴的 Cordis 思想

```
DeepSeek Harness / Cordis 的核心哲学：
  - 核心系统保持极小
  - 能力全部外置成可插拔模块
  - 由统一 Runtime 负责生命周期、依赖、调度和通信
```

### 2.2 映射到 TeamMind

```
Cordis 概念          TeamMind 对应
─────────────────────────────────────
Context             PluginContext
Service             Plugin
EventBus            EventBus
Hook                Plugin Hook
Scheduler           TaskScheduler
Lifecycle           PluginLifecycle
```

---

## 三、Plugin 接口

### 3.1 核心接口

```typescript
interface Plugin {
  // 元数据
  metadata: PluginMetadata;
  
  // 能力声明
  capabilities: CapabilityDescriptor[];
  
  // 生命周期
  lifecycle: PluginLifecycle;
  
  // 调用接口
  invoke(context: PluginContext): Promise<PluginResult>;
  
  // 流式调用（可选）
  stream?(context: PluginContext, onChunk: (chunk: any) => void): Promise<PluginResult>;
  
  // 取消
  cancel?(): Promise<void>;
  
  // 健康检查
  inspect?(): Promise<PluginHealth>;
}

interface PluginMetadata {
  id: string;                    // "claude-code" / "codex" / "git" / ...
  type: PluginType;              // AGENT / TOOL / VERIFIER / MEMORY / INTEGRATION / CONTEXT
  name: string;
  version: string;
  author?: string;
  description?: string;
  homepage?: string;
  
  // 设计哲学（Agent Plugin 专属）
  philosophy?: AgentPhilosophy;
}

type PluginType = 
  | 'AGENT'           // Claude / Codex / Aider
  | 'TOOL'            // Terminal / Browser / Docker
  | 'VERIFIER'        // Test Runner / Lint / Static Analysis
  | 'MEMORY'          // Project Memory / Task Memory
  | 'INTEGRATION'     // GitHub / GitLab / Jira
  | 'CONTEXT';        // Git / Obsidian / Project KB
```

### 3.2 Plugin Context

```typescript
interface PluginContext {
  // 任务信息
  task: AgentTask;
  
  // 项目上下文（Plugin 不需要自己拉取）
  sharedState: SharedStateRef;
  
  // 工作目录
  workDir: string;
  
  // 超时（由 Scheduler 设置）
  timeoutMs: number;
  
  // 取消信号
  abortSignal: AbortSignal;
  
  // 运行时能力（Plugin 可以调用其他 Plugin）
  capabilities: PluginCapabilityAPI;
}

interface PluginCapabilityAPI {
  // Plugin A 可以调用 Plugin B 的能力
  invoke(pluginId: string, context: PluginContext): Promise<PluginResult>;
  
  // 发布事件到 EventBus
  emit(event: RuntimeEvent): void;
  
  // 记录 Evidence
  recordEvidence(evidence: Evidence): Promise<void>;
  
  // 读取 Shared State（受控）
  readSharedState(scope: 'CONTEXT' | 'ARTIFACTS' | 'DECISIONS' | 'ROUTING_LESSONS'): any;
  
  // 写回 Shared State（受控）
  writeSharedState(scope: string, data: any): Promise<void>;
}
```

### 3.3 Plugin Result

```typescript
interface PluginResult {
  pluginId: string;
  taskId: string;
  status: 'SUCCESS' | 'FAILURE' | 'PARTIAL' | 'NEEDS_REVIEW';
  
  // 结构化产物
  artifacts: Artifact[];
  findings: Finding[];
  questions: Question[];
  
  // 证据（Plugin 自报，可被独立验证）
  evidence: Evidence[];
  
  // 自评（仅供 Routing 参考，不可信）
  selfReport?: {
    confidence: number;          // 0-1
    quality: 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR';
  };
  
  // 摘要（给人看）
  summary: string;
  
  // 性能数据（用于 Adaptive Evolution）
  performance: {
    durationMs: number;
    tokensUsed?: number;
    costUsd?: number;
  };
  
  // 建议下一步
  nextAction?: {
    suggestedRole?: string;
    suggestedCapability?: string;
    reason: string;
  };
}
```

---

## 四、Plugin Lifecycle

```
Discovery → Loading → Registration → HealthCheck → Ready → Executing → Cleanup → Unloading
   ↓           ↓           ↓              ↓            ↓          ↓           ↓           ↓
扫描可用   加载元数据   注册到          周期性      接收       执行任务   清理        注销
插件        + 接口      Capability     探活       任务       返回结果   资源       注册
                          Registry
```

### 4.1 Discovery

```typescript
// 1. CLI Plugin：扫描 PATH
const cliPlugins = await discoverCliPlugins([
  'opencode', 'claude', 'codex', 'gemini', 'aider'
]);

// 2. 内置 Plugin：bundled
const builtinPlugins = loadBundledPlugins('classpath:plugins/*.json');

// 3. 文件系统 Plugin：~/.teammind/plugins/
const userPlugins = await loadUserPlugins('~/.teammind/plugins/');

return [...builtinPlugins, ...userPlugins, ...cliPlugins];
```

### 4.2 Loading

```typescript
async function loadPlugin(metadata: PluginMetadata): Promise<Plugin> {
  // 1. 实例化 Plugin
  const plugin = await pluginFactory.create(metadata);
  
  // 2. 校验接口完整性
  validatePluginInterface(plugin);
  
  // 3. 调用 lifecycle.onLoad
  if (plugin.lifecycle?.onLoad) {
    await plugin.lifecycle.onLoad();
  }
  
  // 4. 注册到 Capability Registry
  for (const cap of plugin.capabilities) {
    capabilityRegistry.register(cap, plugin);
  }
  
  // 5. 订阅事件
  for (const hook of plugin.lifecycle?.hooks || []) {
    eventBus.on(hook.event, hook.handler);
  }
  
  return plugin;
}
```

### 4.3 Health Check

```typescript
class PluginHealthMonitor {
  async check(plugin: Plugin): Promise<PluginHealth> {
    try {
      if (plugin.inspect) {
        return await plugin.inspect();
      }
      // 默认：ping-like 调用
      return await this.defaultPing(plugin);
    } catch (error) {
      return { status: 'UNHEALTHY', error: error.message };
    }
  }
  
  // 周期性任务
  startMonitoring(intervalMs: number = 30000) {
    setInterval(async () => {
      for (const plugin of this.plugins.values()) {
        const health = await this.check(plugin);
        if (health.status === 'UNHEALTHY') {
          eventBus.emit('plugin.failed', { pluginId: plugin.metadata.id, error: health.error });
        }
      }
    }, intervalMs);
  }
}
```

### 4.4 Unloading

```typescript
async function unloadPlugin(pluginId: string): Promise<void> {
  const plugin = plugins.get(pluginId);
  if (!plugin) return;
  
  // 1. 等待正在执行的任务完成（或取消）
  await scheduler.drainForPlugin(pluginId);
  
  // 2. 注销 Capabilities
  capabilityRegistry.unregisterByPlugin(pluginId);
  
  // 3. 取消事件订阅
  eventBus.unsubscribeAll(pluginId);
  
  // 4. 调用 lifecycle.onUnload
  if (plugin.lifecycle?.onUnload) {
    await plugin.lifecycle.onUnload();
  }
  
  // 5. 移除 Plugin
  plugins.delete(pluginId);
}
```

---

## 五、Capability Registry

### 5.1 数据结构

```typescript
class CapabilityRegistry {
  // capability -> [Plugin]
  private byCapability: Map<string, Plugin[]> = new Map();
  
  // Plugin ID -> Plugin
  private byId: Map<string, Plugin> = new Map();
  
  register(capability: CapabilityDescriptor, plugin: Plugin): void {
    if (!this.byCapability.has(capability.name)) {
      this.byCapability.set(capability.name, []);
    }
    this.byCapability.get(capability.name)!.push(plugin);
    this.byId.set(plugin.metadata.id, plugin);
  }
  
  findByCapability(name: string): Plugin[] {
    return this.byCapability.get(name) || [];
  }
  
  getById(id: string): Plugin | undefined {
    return this.byId.get(id);
  }
  
  // 索引：能力质量
  findByCapabilityAndQuality(
    name: string,
    minQuality: 'FAIR' | 'GOOD' | 'EXCELLENT'
  ): Plugin[] {
    const qualityOrder = { 'FAIR': 1, 'GOOD': 2, 'EXCELLENT': 3 };
    return this.findByCapability(name).filter(plugin => {
      const cap = plugin.capabilities.find(c => c.capability === name);
      return cap && qualityOrder[cap.quality] >= qualityOrder[minQuality];
    });
  }
}
```

### 5.2 Capability Descriptor

```typescript
interface CapabilityDescriptor {
  name: string;                   // "code_review" / "implementation" / "test_generation"
  quality: 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR';
  description?: string;
  requirements?: string[];        // 依赖的其他能力
}
```

---

## 六、Scheduler

### 6.1 任务依赖

```typescript
interface ScheduledTask {
  id: string;
  pluginId: string;
  method: string;
  context: PluginContext;
  
  // 依赖：必须先完成的任务 IDs
  dependsOn: string[];
  
  // 重试策略
  retryPolicy?: {
    maxRetries: number;
    backoffMs: number;
  };
  
  // 失败处理
  onFailure?: 'FAIL' | 'RETRY' | 'FALLBACK' | 'ROLLBACK';
  fallbackPluginId?: string;
  
  // 超时
  timeoutMs: number;
}

class TaskScheduler {
  private pending: ScheduledTask[] = [];
  private running: Map<string, Promise<any>> = new Map();
  private completed: Set<string> = new Set();
  private failed: Set<string> = new Set();
  
  async run(tasks: ScheduledTask[]): Promise<Map<string, PluginResult>> {
    // 拓扑排序
    const ordered = this.topologicalSort(tasks);
    
    // 按依赖顺序执行
    for (const task of ordered) {
      if (this.canRun(task)) {
        await this.execute(task);
      }
    }
    
    return this.results;
  }
  
  private canRun(task: ScheduledTask): boolean {
    return task.dependsOn.every(dep => this.completed.has(dep));
  }
  
  private async execute(task: ScheduledTask): Promise<void> {
    const plugin = pluginManager.getById(task.pluginId);
    const promise = this.invokeWithRetry(plugin, task);
    this.running.set(task.id, promise);
    
    try {
      await promise;
      this.completed.add(task.id);
    } catch (error) {
      this.failed.add(task.id);
      // 按 onFailure 处理
    } finally {
      this.running.delete(task.id);
    }
  }
}
```

### 6.2 重试与回退

```typescript
async function invokeWithRetry(plugin: Plugin, task: ScheduledTask): Promise<PluginResult> {
  let lastError: Error;
  
  for (let attempt = 0; attempt <= (task.retryPolicy?.maxRetries || 0); attempt++) {
    try {
      const result = await withTimeout(plugin.invoke(task.context), task.timeoutMs);
      if (result.status === 'SUCCESS' || result.status === 'PARTIAL') {
        return result;
      }
    } catch (error) {
      lastError = error;
      await sleep(task.retryPolicy?.backoffMs || 1000);
    }
  }
  
  // 尝试 fallback
  if (task.onFailure === 'FALLBACK' && task.fallbackPluginId) {
    const fallback = pluginManager.getById(task.fallbackPluginId);
    return await fallback.invoke(task.context);
  }
  
  throw lastError!;
}
```

---

## 七、Event Bus

### 7.1 事件类型

```typescript
type RuntimeEventType =
  // Plugin 生命周期
  | 'plugin.discovered' | 'plugin.loaded' | 'plugin.unloaded'
  | 'plugin.healthy' | 'plugin.failed'
  
  // 任务执行
  | 'task.scheduled' | 'task.started' | 'task.completed' | 'task.failed'
  
  // Evidence 验证
  | 'evidence.verified' | 'evidence.failed'
  
  // 路由
  | 'role.routed' | 'role.drift_detected'
  
  // 进化
  | 'profile.updated' | 'recommendation.generated';
```

### 7.2 订阅示例

```typescript
// Plugin 自定义事件处理
eventBus.on('task.completed', async (event) => {
  const { result } = event.payload;
  await performanceTracker.record(result);
  await sharedState.updateProjectProfile(result);
});

eventBus.on('evidence.failed', async (event) => {
  const { taskId, evidence, reason } = event.payload;
  // 触发 Reviewer 复查
  await scheduler.run([
    {
      id: `review-${taskId}`,
      pluginId: 'claude-code',
      method: 'executeTask',
      context: { task: { objective: `Verify failed evidence: ${reason}` } }
    }
  ]);
});

eventBus.on('plugin.failed', async (event) => {
  const { pluginId, error } = event.payload;
  // 熔断：从 Capability Registry 临时移除
  capabilityRegistry.blacklist(pluginId, error);
});
```

---

## 八、Plugin 编写示例

### 8.1 Claude Code Agent Plugin（简化）

```typescript
// adapters/claude-code/index.ts
import { AgentPlugin, AgentTask, PluginResult } from '@teammind/runtime';

class ClaudeCodePlugin implements AgentPlugin {
  metadata = {
    id: 'claude-code',
    type: 'AGENT',
    name: 'Claude Code',
    version: '1.0.0',
    author: 'Anthropic',
    description: 'Anthropic 官方的 AI Agent CLI，主打最强推理质量',
    homepage: 'https://github.com/anthropics/claude-code',
    philosophy: {
      primary: ['safety', 'controlled_action', 'explicit_permission', 'cautious_execution'],
      designGoals: ['可控的权限边界', '对每个操作要求显式审批'],
      preferredRoles: ['security_review', 'architecture_review', 'risk_analysis'],
      weakRoles: ['bulk_refactor', 'bulk_formatting']
    }
  };
  
  capabilities = [
    { name: 'code_review', quality: 'EXCELLENT' },
    { name: 'implementation', quality: 'GOOD' },
    { name: 'architecture_design', quality: 'EXCELLENT' },
    { name: 'risk_analysis', quality: 'EXCELLENT' }
  ];
  
  profile = {
    avgLatencyMs: 45000,
    reliabilityScore: 0.92
  };
  
  async invoke(context: PluginContext): Promise<PluginResult> {
    const prompt = this.buildPrompt(context);
    
    // 1. 启动 CLI 子进程
    const child = spawn('claude', ['--print', '--output-format', 'stream-json'], {
      cwd: context.workDir
    });
    
    // 2. 写入 prompt
    child.stdin.write(prompt);
    child.stdin.end();
    
    // 3. 流式读取 stdout
    const artifacts = [];
    const findings = [];
    const evidence = [];
    let summary = '';
    
    for await (const line of readLines(child.stdout)) {
      const event = JSON.parse(line);
      switch (event.type) {
        case 'text':
          summary += event.content;
          // 实时推送到前端
          context.capabilities.emit({ type: 'agent.chunk', content: event.content });
          break;
        case 'tool_call':
          // 记录 tool call
          break;
        case 'done':
          evidence.push({ type: 'COMMAND_EXIT', command: 'claude', exitCode: event.exit_code });
          break;
      }
    }
    
    await new Promise((resolve) => child.on('close', resolve));
    
    return {
      pluginId: this.metadata.id,
      taskId: context.task.taskId,
      status: 'SUCCESS',
      artifacts,
      findings,
      questions: [],
      evidence,
      summary,
      performance: { durationMs: Date.now() - context.task.startTime },
      nextAction: { suggestedCapability: 'code_review', reason: 'Need verification' }
    };
  }
  
  async inspect(): Promise<PluginHealth> {
    try {
      const result = spawnSync('claude', ['--version']);
      return result.status === 0
        ? { status: 'HEALTHY', version: result.stdout.toString().trim() }
        : { status: 'UNHEALTHY', error: result.stderr.toString() };
    } catch (error) {
      return { status: 'UNHEALTHY', error: error.message };
    }
  }
  
  cancel(): Promise<void> {
    if (this.currentChild) {
      this.currentChild.kill('SIGTERM');
      setTimeout(() => this.currentChild?.kill('SIGKILL'), 5000);
    }
    return Promise.resolve();
  }
}
```

### 8.2 Git Verifier Plugin

```typescript
class GitVerifierPlugin implements Plugin {
  metadata = {
    id: 'git',
    type: 'VERIFIER',
    name: 'Git Verifier',
    version: '1.0.0'
  };
  
  capabilities = [
    { name: 'verify_git_diff', quality: 'EXCELLENT' }
  ];
  
  async invoke(context: PluginContext): Promise<PluginResult> {
    const { workDir } = context;
    const evidence = context.task.context.previousArtifacts
      .filter(a => a.type === 'CODE_DIFF')
      .flatMap(a => a.files)
      .map(f => f.path);
    
    // 独立运行 git diff 验证
    const diffResult = await this.runCommand('git diff --name-only', workDir);
    const verifiedFiles = diffResult.split('\n').filter(Boolean);
    
    const allPresent = evidence.every(f => verifiedFiles.includes(f));
    
    return {
      pluginId: this.metadata.id,
      taskId: context.task.taskId,
      status: allPresent ? 'SUCCESS' : 'FAILURE',
      artifacts: [],
      findings: allPresent ? [] : [{
        severity: 'HIGH',
        message: `Expected files not in git diff: ${evidence.filter(f => !verifiedFiles.includes(f)).join(', ')}`
      }],
      evidence: [{
        type: 'COMMAND_EXIT',
        command: 'git diff --name-only',
        exitCode: 0,
        stdout: diffResult
      }],
      summary: allPresent ? 'All claimed changes verified in git' : 'Some changes not found',
      performance: { durationMs: 0 }
    };
  }
}
```

### 8.3 Plugin Manifest

```yaml
# ~/.teammind/plugins/my-custom-plugin/plugin.yaml
id: my-custom-plugin
type: AGENT
name: My Custom Agent
version: 1.0.0
author: me
description: 我自己写的 Agent Plugin

entry: ./index.js

capabilities:
  - name: code_review
    quality: GOOD
  - name: implementation
    quality: FAIR

philosophy:
  primary: ['flexible', 'fast']

runtime:
  command: my-agent
  args: ['--mode', 'agent']
```

---

## 九、与现有代码的整合

### 9.1 保留的部分

| 现有模块 | Plugin Runtime 对应 |
|---|---|
| `WebSocketConfig` | Runtime → Frontend 通信 |
| `WebSocketAuthChannelInterceptor` | 单用户本地工具，去掉 |
| `WSEventPublisher` | EventBus（精简） |
| `ThreadPoolConfig` | Plugin Runtime Scheduler |
| `SQLiteWriteLockService` | 保留（SQLite 仍需要） |
| `DataDirectoryBootstrap` | 保留 |

### 9.2 砍掉的部分

| 现有模块 | 砍掉理由 |
|---|---|
| `evolution/` 整个目录 | 自适应通过 Plugin Runtime + Evidence 实现 |
| `evolution/EvolutionGateService` | 同上 |
| `evolution/AutomaticEvolutionScheduler` | 同上 |
| `service/AgentMetricsService` | 简化为 ProjectAgentProfile |
| `service/AgentService` | 重写为 PluginManager |
| `service/MissionService` | 重写为 TaskScheduler |
| `service/TemplateService` | 砍掉 |
| `service/AuthService` | 砍掉（单用户本地工具） |
| `auth/JwtAuthFilter` | 砍掉 |
| `llm/` 整个目录 | 通过 AgentPlugin 抽象，不直接调 LLM |

### 9.3 新增的部分

| 新模块 | 用途 |
|---|---|
| `plugin/PluginManager.java` | Plugin 加载 / 卸载 / 注册 |
| `plugin/CapabilityRegistry.java` | 按能力索引 Plugin |
| `plugin/TaskScheduler.java` | 任务依赖排序 + 重试 + 回退 |
| `plugin/EventBus.java` | 事件分发 |
| `plugin/HealthMonitor.java` | Plugin 探活 |
| `capability/CapabilityRouter.java` | 能力路由算法 |
| `evidence/EvidenceVerifier.java` | 独立验证 |
| `evolution/PerformanceTracker.java` | 表现采集 |
| `evolution/RoleDriftDetector.java` | 漂移检测 |
| `evolution/TeamRecommender.java` | 推荐生成 |

---

## 十、与 Cordis 真实差异

| 维度 | Cordis (DeepSeek) | TeamMind Plugin Runtime |
|---|---|---|
| 服务对象 | 单一 LLM 推理 + 工具 | 多 CLI Agent 协作 |
| 能力单位 | Tool (function calling) | Capability (跨 Agent 抽象) |
| 调度器 | 请求 / 响应 | 任务依赖图 + Evidence 验证 |
| 核心优势 | LLM 工具生态 | 异质 Agent 哲学互补 |
| 自适应 | 无（配置驱动） | **Role Evolution 闭环** |

---

**版本**：v0.1 Draft
**最后更新**：2026-08-14