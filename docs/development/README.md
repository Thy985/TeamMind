# TeamMind 开发文档总入口

> 本文为开发者提供完整的实现指南：环境搭建、模块拆分、代码模板、测试策略。

---

## 文档结构

```
docs/
├── runtime/                  # 设计文档（What & Why）
│   ├── core-model.md
│   ├── plugin-system.md
│   ├── capability-routing.md
│   ├── role-evolution.md
│   └── agent-philosophy-matrix.md
├── adapters/                 # 协议规范
│   └── spec.md
├── research/                 # 市场调研
│   └── agent-cli-orchestration-landscape.md
├── development/              # ← 本目录：实现指南（How）
│   ├── README.md             # 本文件
│   ├── environment-setup.md  # 环境搭建
│   ├── w2-plugin-runtime.md       # W2：Plugin Runtime 实现
│   ├── w2-capability-registry.md  # W2：能力注册表
│   ├── w2-schema-migration.md     # W2：SQLite schema 迁移
│   ├── w3-claude-plugin.md        # W3：Claude Code Plugin
│   ├── w3-codex-plugin.md         # W3：Codex Plugin
│   ├── w3-verifier-plugins.md     # W3：Verifier Plugins
│   ├── w4-role-evolution.md       # W4：自适应闭环
│   └── testing-guide.md           # 测试策略
├── RFC-001-cli-orchestration.md
└── teammind-remediation-plan.md
```

---

## 项目结构（实施后）

```
backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/teammind/
│   │   │   ├── TeamMindApplication.java
│   │   │   ├── config/
│   │   │   │   ├── DataDirectoryBootstrap.java       # B8 已实现
│   │   │   │   ├── DataInitializer.java
│   │   │   │   ├── WebSocketConfig.java              # B1 已修复
│   │   │   │   └── SqliteConfig.java                # 新增
│   │   │   ├── plugin/
│   │   │   │   ├── PluginManager.java                # W2.1
│   │   │   │   ├── CapabilityRegistry.java           # W2.2
│   │   │   │   ├── TaskScheduler.java                # W2.1
│   │   │   │   ├── EventBus.java                     # W2.1
│   │   │   │   ├── HealthMonitor.java                # W2.1
│   │   │   │   ├── PluginContext.java                # W2.1
│   │   │   │   ├── PluginResult.java                 # W2.1
│   │   │   │   └── PluginLifecycle.java              # W2.1
│   │   │   ├── capability/
│   │   │   │   ├── CapabilityRouter.java             # W2.2
│   │   │   │   ├── CapabilityDescriptor.java         # W2.2
│   │   │   │   └── RoutingScoreCalculator.java       # W2.2
│   │   │   ├── evidence/
│   │   │   │   ├── EvidenceVerifier.java             # W2.3
│   │   │   │   ├── GitDiffVerifier.java              # W3.3
│   │   │   │   ├── TestExecutionVerifier.java        # W3.3
│   │   │   │   └── FileExistenceVerifier.java        # W3.3
│   │   │   ├── plugins/
│   │   │   │   ├── ClaudeCodePlugin.java             # W3.1
│   │   │   │   ├── CodexPlugin.java                  # W3.2
│   │   │   │   ├── AiderPlugin.java                  # W3.5
│   │   │   │   ├── GeminiCliPlugin.java              # W3.5
│   │   │   │   └── OpenCodePlugin.java               # W3.5
│   │   │   ├── verifiers/
│   │   │   │   ├── GitVerifier.java                  # W3.3
│   │   │   │   └── TestRunnerVerifier.java           # W3.3
│   │   │   ├── domain/
│   │   │   │   ├── Project.java
│   │   │   │   ├── TeamConfig.java
│   │   │   │   ├── SharedState.java
│   │   │   │   ├── TaskExecution.java
│   │   │   │   ├── TaskStep.java
│   │   │   │   ├── AgentResult.java
│   │   │   │   ├── AgentTask.java
│   │   │   │   ├── Artifact.java                     # 顶层抽象
│   │   │   │   ├── artifacts/                         # 各种 Artifact 类型
│   │   │   │   │   ├── CodeDiffArtifact.java
│   │   │   │   │   ├── TestReportArtifact.java
│   │   │   │   │   └── ReviewFindingsArtifact.java
│   │   │   │   ├── Evidence.java
│   │   │   │   ├── Decision.java
│   │   │   │   ├── RoutingLesson.java
│   │   │   │   ├── PerformanceRecord.java
│   │   │   │   └── DriftAlert.java
│   │   │   ├── evolution/
│   │   │   │   ├── PerformanceTracker.java           # W4.1
│   │   │   │   ├── RoleDriftDetector.java            # W4.2
│   │   │   │   ├── TeamRecommender.java              # W4.3
│   │   │   │   └── RoutingLessonExtractor.java       # W4.1
│   │   │   ├── repository/                           # Spring Data JPA
│   │   │   │   ├── ProjectRepository.java
│   │   │   │   ├── TeamConfigRepository.java
│   │   │   │   ├── TaskExecutionRepository.java
│   │   │   │   ├── PerformanceRecordRepository.java
│   │   │   │   ├── RoutingLessonRepository.java
│   │   │   │   └── DriftAlertRepository.java
│   │   │   ├── service/
│   │   │   │   ├── ProjectService.java               # CRUD
│   │   │   │   ├── TaskService.java                  # 任务执行入口
│   │   │   │   └── RecommendationService.java        # W4.3
│   │   │   ├── controller/
│   │   │   │   ├── ProjectController.java
│   │   │   │   ├── TaskController.java
│   │   │   │   ├── CliDiscoveryController.java
│   │   │   │   └── HealthController.java
│   │   │   └── websocket/
│   │   │       ├── WSEventPublisher.java             # 已存在
│   │   │       └── TaskProgressBroadcaster.java
│   │   ├── resources/
│   │   │   ├── application.yml
│   │   │   ├── application-local.yml
│   │   │   ├── META-INF/spring/
│   │   │   │   └── org.springframework.boot.env.EnvironmentPostProcessor.imports
│   │   │   ├── db/migration/
│   │   │   │   └── V2__add_project_runtime.sql       # W2.4
│   │   │   ├── adapters/                             # Plugin metadata
│   │   │   │   ├── claude-code.yaml
│   │   │   │   ├── codex.yaml
│   │   │   │   ├── aider.yaml
│   │   │   │   ├── gemini-cli.yaml
│   │   │   │   └── opencode.yaml
│   │   │   └── logback-spring.xml
│   └── test/
│       ├── java/com/teammind/
│       │   ├── plugin/
│       │   │   ├── PluginManagerTest.java
│       │   │   ├── CapabilityRegistryTest.java
│       │   │   ├── TaskSchedulerTest.java
│       │   │   └── EventBusTest.java
│       │   ├── capability/
│       │   │   └── CapabilityRouterTest.java
│       │   ├── evidence/
│       │   │   └── EvidenceVerifierTest.java
│       │   ├── plugins/
│       │   │   ├── ClaudeCodePluginTest.java        # mock CLI
│       │   │   └── CodexPluginTest.java
│       │   ├── evolution/
│       │   │   ├── PerformanceTrackerTest.java
│       │   │   ├── RoleDriftDetectorTest.java
│       │   │   └── RoutingLessonExtractorTest.java
│       │   └── integration/
│       │       ├── EndToEndTaskFlowTest.java
│       │       └── PluginLifecycleTest.java
│       └── resources/
│           ├── application-test.yml
│           └── fixtures/
│               └── sample-task-execution.json
└── target/

frontend/
├── package.json
├── src/
│   ├── views/
│   │   ├── ProjectListView.vue                     # 重写：项目列表
│   │   ├── ProjectDetailView.vue                    # 新增：项目详情
│   │   ├── TaskExecutionView.vue                    # 新增：任务执行视图
│   │   ├── PluginManagementView.vue                 # 新增：Plugin 管理
│   │   └── TeamRecommendationView.vue               # 新增：W4 推荐 UI
│   ├── components/
│   │   ├── TeamBuilder.vue                         # 新增：组建团队 UI
│   │   ├── CapabilityMatrix.vue                     # 新增：能力矩阵可视化
│   │   ├── PluginCard.vue                           # 新增：Plugin 卡片
│   │   ├── TaskStepTimeline.vue                     # 新增：任务步骤时间线
│   │   └── DriftAlertBanner.vue                     # 新增：漂移告警
│   ├── stores/
│   │   ├── project.ts
│   │   ├── task.ts
│   │   ├── plugin.ts
│   │   └── recommendation.ts
│   └── api/
│       ├── project.ts
│       ├── task.ts
│       └── plugin.ts
└── tests/
    └── unit/
        ├── CapabilityMatrix.test.ts
        └── TeamBuilder.test.ts
```

---

## 实施路线（开发视角）

### Phase 1：核心骨架（W2）

| 任务 | 文档 | 工作量 |
|---|---|---|
| Plugin Runtime 框架 | [w2-plugin-runtime.md](w2-plugin-runtime.md) | 2 天 |
| Capability Registry | [w2-capability-registry.md](w2-capability-registry.md) | 1.5 天 |
| SQLite schema 升级 | [w2-schema-migration.md](w2-schema-migration.md) | 1 天 |
| Repository + Service 基础 | （见各文档） | 1 天 |

### Phase 2：第一个 Plugin（W3）

| 任务 | 文档 | 工作量 |
|---|---|---|
| Claude Code Plugin | [w3-claude-plugin.md](w3-claude-plugin.md) | 2 天 |
| Codex Plugin | [w3-codex-plugin.md](w3-codex-plugin.md) | 1.5 天 |
| Verifier Plugins | [w3-verifier-plugins.md](w3-verifier-plugins.md) | 1 天 |

### Phase 3：自适应（W4）

| 任务 | 文档 | 工作量 |
|---|---|---|
| Performance Tracker | [w4-role-evolution.md](w4-role-evolution.md) | 1.5 天 |
| Drift Detector | [w4-role-evolution.md](w4-role-evolution.md) | 1 天 |
| Team Recommender | [w4-role-evolution.md](w4-role-evolution.md) | 1 天 |

### Phase 4：发布（W5）

- 录视频、写 README、GitHub Release v0.1

---

## 通用规范

### 包命名

```
com.teammind.plugin.*       // 插件系统
com.teammind.capability.*   // 能力路由
com.teammind.evidence.*     // 证据验证
com.teammind.plugins.*      // Agent Plugin 实现
com.teammind.verifiers.*    // Verifier Plugin 实现
com.teammind.domain.*       // 领域模型
com.teammind.evolution.*    // 自适应
com.teammind.repository.*   // Spring Data
com.teammind.service.*      // 业务服务
com.teammind.controller.*   // REST API
com.teammind.websocket.*    // WebSocket
```

### 命名约定

| 类型 | 命名 | 示例 |
|---|---|---|
| Interface | 名词 + 形容词 | `Plugin`, `AgentPlugin`, `VerifierPlugin` |
| Implementation | 具体名 | `ClaudeCodePlugin`, `GitVerifier` |
| Service | 名词 + Service | `ProjectService`, `TaskService` |
| Repository | 名词 + Repository | `ProjectRepository` |
| Controller | 名词 + Controller | `ProjectController` |
| Event | 动名词 + 事件 | `task.completed`, `plugin.failed` |
| 私有方法 | camelCase | `calculateScore` |
| 常量 | UPPER_SNAKE | `MAX_RETRY_COUNT` |

### Java 编码规范

- 使用 **Java 17**（保持现有 JDK 17.0.2）
- 用 `record` 表示不可变 DTO（AgentTask, AgentResult）
- 用 `sealed interface` 表示有限类型（Artifact, Evidence）
- 用 `Optional<T>` 显式表达可能为空
- 不使用 Lombok（已有项目无依赖，保持一致）
- 异常用领域特定异常，不滥用 RuntimeException

### Spring 注入

- 优先用**构造器注入**
- `@Service`、`@Component`、`@Configuration` 标准注解
- Bean 不暴露 getter/setter（用构造器一次性注入）

### 测试规范

- JUnit 5（已有依赖）
- Mock 用 Mockito
- 不依赖外部 CLI（Plugin 测试用 ProcessBuilder mock）
- SQLite 测试用 Testcontainers 或临时文件
- 覆盖率目标：核心 Runtime ≥ 85%

详见 [testing-guide.md](testing-guide.md)

---

## 与现有代码的整合

### 保留的部分

| 模块 | 用途 |
|---|---|
| `DataDirectoryBootstrap` | 保留（B8 修复） |
| `DataInitializer` | 保留，扩展 |
| `WebSocketConfig` | 保留，扩展 |
| `WSEventPublisher` | 保留，新增 TaskProgressBroadcaster |
| `SQLiteWriteLockService` | 保留 |
| `config/SqliteConfig` | 新增，配置 SQLite |

### 砍掉的部分

| 模块 | 砍掉理由 |
|---|---|
| `evolution/` 旧目录 | 重新设计为 plugin + evolution |
| `EvolutionGateService` | 新架构不需要 |
| `AutomaticEvolutionScheduler` | 由 TeamRecommender 取代 |
| `AgentMetricsService` | 简化为 PerformanceTracker |
| `AgentService` | 重写为 PluginManager + TaskService |
| `MissionService` | 重写为 TaskService |
| `TemplateService` | 砍掉（v0.2 不做模板） |
| `AuthService` / `JwtAuthFilter` | 砍掉（单用户本地工具） |
| `llm/` | 砍掉（AgentPlugin 抽象取代） |
| `auth/` | 砍掉 |

---

## 开发工作流

### 1. 接到任务

```
1. 读对应 W 文档（如 w2-plugin-runtime.md）
2. 读对应设计文档（如 runtime/plugin-system.md）
3. 找 DoD（Definition of Done）
4. 按章节顺序实现
```

### 2. 实现

```
1. 创建 domain class（先于 service）
2. 创建 repository（基于 domain）
3. 创建 service（基于 repository）
4. 创建单元测试
5. 创建 controller
6. 端到端测试
```

### 3. 验收

```
1. 单元测试全过
2. DoD 全打勾
3. PR description 完整
4. 与现有测试套件无回归
```

---

## 持续文档维护

- 每个 W 任务完成后，对应 W 文档的"实现进度"小节更新
- 每个发现的非显性问题，写入对应 W 文档的"踩坑记录"
- 设计文档（`runtime/`）和开发文档（`development/`）保持同步

---

## 接下来要读

| 我要做什么 | 先读 |
|---|---|
| 设置开发环境 | [environment-setup.md](environment-setup.md) |
| 实现 Plugin Runtime | [w2-plugin-runtime.md](w2-plugin-runtime.md) |
| 实现能力注册表 | [w2-capability-registry.md](w2-capability-registry.md) |
| 数据库迁移 | [w2-schema-migration.md](w2-schema-migration.md) |
| 写 Claude Code Plugin | [w3-claude-plugin.md](w3-claude-plugin.md) |
| 写 Codex Plugin | [w3-codex-plugin.md](w3-codex-plugin.md) |
| 写 Verifier | [w3-verifier-plugins.md](w3-verifier-plugins.md) |
| 实现自适应闭环 | [w4-role-evolution.md](w4-role-evolution.md) |
| 写测试 | [testing-guide.md](testing-guide.md) |

---

**最后更新**：2026-08-14
**版本**：v0.1
**状态**：伴随 W2-W4 实现逐步完善