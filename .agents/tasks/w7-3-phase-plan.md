# Phase 3 Plan: Recovery Hardening + Platform Foundation

## Strategy: 3B First, 3A Validates

```
Phase 3B (Platform)                    Phase 3A (Hard Standards)
──────────────────────                 ──────────────────────
1. CLIAdapter 抽象层                   4. RecoveryService 增强（验证所有注入 CLI）
2. GenericCLIPlugin 通用实现           5. ProjectPage.vue
3. CLIDiscoveryService 动态发现        6. Integration test
         ↓                                  ↓
   注册新 CLI 只需 YAML                3A 验证 3B 产出的 CLI 是否可达
```

---

## 3B: Platform — Multi-CLI Dynamic Integration

### 3B-1: CLIAdapter Interface

```java
// backend/src/main/java/com/teammind/plugin/adapter/CLIAdapter.java
public interface CLIAdapter extends Plugin {
    // 执行配置（由 YAML/JSON 描述，不硬编码）
    CLIConfig config();

    // 标准化输出格式：不同 CLI 的输出 → 统一 TeamMind 事件
    void parseOutput(String line, String taskId, PluginChunkHandler handler);

    // 进程管理
    void startProcess(String prompt, String workDir) throws IOException;
    ProcessHandle getProcessHandle();
    boolean isAlive();
    void kill();

    // 依赖检查（用于 ReadinessManager）
    default List<PluginDependency> dependencies() { return List.of(); }
}
```

### 3B-2: CLIConfig (YAML-driven)

```yaml
# resources/cli-adapters/codex.yaml
cli_id: codex
command: "codex"
args: ["<prompt>"]
env:
  OPENAI_API_KEY: "${ENV:OPENAI_API_KEY}"
working_dir: "."
timeout_minutes: 60
output_format: "text"        # text | ndjson | structured
health_check:
  command: "codex --version"
  expected_exit: 0
dependencies:
  - type: EXECUTABLE
    name: codex-cli
    check: "codex --version"
  - type: SERVICE
    name: local-provider
    endpoint: "http://127.0.0.1:57321/v1/models"
```

```yaml
# resources/cli-adapters/atomcode.yaml  ← 未来可加，无需改 Java
cli_id: atomcode
command: "atomcode"
args: ["--interactive", "<prompt>"]
env:
  ATOMCODE_API_KEY: "${ENV:ATOMCODE_API_KEY}"
output_format: "ndjson"
```

### 3B-3: GenericCLIPlugin

```java
// 从 YAML 加载配置，实现 CLIAdapter 接口
// 支持: text/ndjson/structured 输出格式解析
// 自动 emit: TASK_STARTED, AGENT_CHUNK, TASK_COMPLETED, TASK_FAILED
public class GenericCLIPlugin implements CLIAdapter {
    private final CLIConfig config;
    private volatile Process currentProcess;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override public String id() { return config.cliId(); }
    @Override public PluginType type() { return PluginType.AGENT; }

    // invoke() — 通用执行逻辑，所有 CLI 共享
    @Override
    public PluginResult invoke(PluginContext ctx) {
        startProcess(resolvePrompt(ctx), ctx.projectPath());
        // 读取 stdout → 按 output_format 解析 → emit events
        // 返回 PluginResult
    }

    // parseOutput() — 由 config.output_format 决定解析策略
    @Override
    public void parseOutput(String line, String taskId, PluginChunkHandler handler) {
        switch (config.outputFormat()) {
            case "ndjson" -> parseNDJSON(line, taskId, handler);  // Claude Code
            case "text"   -> parseText(line, taskId, handler);     // Codex
            case "structured" -> parseJSON(line, taskId, handler); // Atomcode?
            default -> handler.onChunk(line);
        }
    }
}
```

### 3B-4: CLIDiscoveryService

```java
// 启动时扫描 resources/cli-adapters/*.yaml
// 每个 YAML → 创建一个 GenericCLIPlugin → 注册到 PluginManager
@Component
public class CLIDiscoveryService implements CommandLineRunner {
    @Override
    public void run(String... args) {
        Path dir = Path.of("src/main/resources/cli-adapters");
        if (!Files.exists(dir)) return;

        Files.list(dir)
            .filter(p -> p.toString().endsWith(".yaml"))
            .forEach(this::loadAdapter);
    }

    private void loadAdapter(Path yamlPath) {
        CLIConfig config = loadYAML(yamlPath);
        GenericCLIPlugin plugin = new GenericCLIPlugin(config);
        pluginManager.register(plugin);
        log.info("Discovered CLI adapter: {} (from {})", config.cliId(), yamlPath.getFileName());
    }
}
```

### 3B-5: CLIProcessTracker

```java
// 统一管理所有 CLI 进程生命周期
@Component
public class CLIProcessTracker {
    // pid → ProcessHandle 映射（跨 plugin 共享）
    private final ConcurrentHashMap<String, ProcessHandle> processMap = new ConcurrentHashMap<>();

    public void register(String pluginId, ProcessHandle handle) {
        processMap.put(pluginId, handle);
    }

    public boolean isAlive(String pluginId) {
        return processMap.get(pluginId) != null && processMap.get(pluginId).isAlive();
    }

    public void killAll() {
        processMap.values().forEach(ProcessHandle::destroyForcibly);
        processMap.clear();
    }
}
```

---

## 3A: Hard Standards — Recovery + ProjectPage

### 3A-1: Enhanced RecoveryService

```java
// 现在可以验证任何注入的 CLI，不只是 Codex/Claude
@Component
public class RecoveryService implements CommandLineRunner {

    private final CLIProcessTracker processTracker;  // 新增
    private final PluginManager pluginManager;       // 新增
    private final TaskExecutionRepository executionRepo;

    @Override
    public void run(String... args) {
        // 1. 恢复 in-flight executions
        scanInFlightExecutions();

        // 2. 验证所有已注入 CLI 的健康状态
        scanCLIHealth();
    }

    private void scanCLIHealth() {
        for (Plugin plugin : pluginManager.getAll()) {
            if (plugin.type() != Plugin.PluginType.AGENT) continue;
            try {
                CLIAdapter adapter = (CLIAdapter) plugin;
                boolean alive = adapter.isAlive();
                if (alive) {
                    log.info("CLI {} is healthy (PID={})", adapter.id(),
                        adapter.getProcessHandle().pid());
                } else {
                    log.warn("CLI {} is not running — readiness check will mark DEGRADED", adapter.id());
                }
            } catch (ClassCastException e) {
                // 非 CLI 插件（Verifier/Memory 等），跳过
            }
        }
    }

    private void scanInFlightExecutions() {
        List<TaskExecution> inFlight = executionRepo.findAll().stream()
            .filter(e -> isRunningState(e.getExecutionState()))
            .toList();

        for (TaskExecution exec : inFlight) {
            Long pid = findPID(exec);  // 从 AgentInvocation 查 pid
            if (pid != null && ProcessHandle.of(pid).isPresent()) {
                exec.setExecutionState(TaskExecutionState.RECOVERING);
                exec.setErrorReason("PROCESS_ALIVE_PID=" + pid);
            } else {
                exec.setExecutionState(TaskExecutionState.FAILED);
                exec.setErrorReason("PROCESS_DIED");
            }
            executionRepo.save(exec);
        }
    }
}
```

### 3A-2: ProjectPage.vue

```vue
<!-- src/pages/ProjectPage.vue -->
<!-- 项目列表 + 新建项目表单 -->
<!-- 连接到 GET /api/mission-control/projects 和 POST /api/projects -->
```

### 3A-3: ProjectController + ProjectApi

```java
// GET  /api/projects          — 项目列表
// POST /api/projects          — 创建项目
// GET  /api/projects/{id}     — 项目详情
// PUT  /api/projects/{id}     — 更新项目
// DELETE /api/projects/{id}   — 删除项目
// GET  /api/projects/{id}/cli-health  — 该项目的 CLI 健康检查（3A 验证 3B 成果）
```

### 3A-4: Integration Test

```java
// Phase3IntegrationTest.java
// 1. 注册 GenericCLIPlugin (模拟)
// 2. RecoveryService 启动 → 验证 CLI 健康状态
// 3. 创建 Execution → kill 进程 → RecoveryService 重新扫描 → 状态变为 FAILED
// 4. Project CRUD API 验证
```

---

## 文件清单

### 新建 (Backend)
| 文件 | 行数估计 | 说明 |
|------|---------|------|
| `plugin/adapter/CLIAdapter.java` | ~60 | 抽象接口 |
| `plugin/adapter/CLIConfig.java` | ~40 | 配置 record |
| `plugin/adapter/GenericCLIPlugin.java` | ~200 | 通用 CLI 实现 |
| `plugin/adapter/CLIProcessTracker.java` | ~50 | 进程追踪 |
| `runtime/CLIDiscoveryService.java` | ~80 | YAML 发现服务 |
| `controller/ProjectController.java` | ~100 | 项目 CRUD |
| `resources/cli-adapters/codex.yaml` | ~30 | Codex 适配配置 |
| `resources/cli-adapters/claude-code.yaml` | ~30 | Claude Code 适配配置 |
| `test/Phase3IntegrationTest.java` | ~150 | 集成测试 |

### 修改 (Backend)
| 文件 | 变更 |
|------|------|
| `RecoveryService.java` | 注入 CLIProcessTracker，增强恢复逻辑 |
| `PluginRegistry.java` | 保留硬编码插件 + 加载 YAML 适配器 |
| `codex.yaml` / `claude-code.yaml` | 将硬编码逻辑迁移到配置 |

### 新建 (Frontend)
| 文件 | 行数估计 |
|------|---------|
| `src/pages/ProjectPage.vue` | ~200 |
| `src/api/axios.ts` 扩展 | +10 (projectApi) |

### 修改 (Frontend)
| 文件 | 变更 |
|------|------|
| `src/router/index.ts` | +1 route (Projects) |
| `src/layouts/MainLayout.vue` | +1 menu item (Projects) |

---

## 验证顺序

1. **3B-1** CLIAdapter 接口定义 → 编译通过
2. **3B-2** CLIConfig + GenericCLIPlugin → 单元测试
3. **3B-3** CLIDiscoveryService → 扫描 YAML 注册插件
4. **3B-4** CLIProcessTracker → 进程管理
5. **3A-1** RecoveryService 增强 → 验证 CLI 健康
6. **3A-2** ProjectPage + API → 前端页面
7. **3A-3** Integration Test → 全链路验证
8. **Commit** → `git commit` as Thy985

---

## Exit Criteria 对应

| ID | 标准 | 对应任务 |
|----|------|---------|
| H3 | 服务重启后状态可恢复 | 3A-1 RecoveryService 增强 |
| H6 | Evidence 与 Task 状态绑定 | 3A-1 状态机 + 3B 统一事件 |
| S6 | Worktree 隔离机制 | 3A-1 RecoveryService 检查 worktree |
| S7 | ProjectList 页面 | 3A-2 ProjectPage.vue |
| — | **平台化能力（战略）** | **3B 全部** |
