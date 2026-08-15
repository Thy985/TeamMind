# TeamMind 测试策略

> 测试规范、最佳实践、覆盖率目标。

---

## 测试金字塔

```
            ┌─────────┐
            │   E2E   │   5 个场景，< 5% 用例
            ├─────────┤
            │ 集成测试 │   关键路径，~15% 用例
            ├─────────┤
            │ 单元测试 │   业务逻辑，~80% 用例
            └─────────┘
```

| 层 | 工具 | 占比 | 速度 |
|---|---|---|---|
| 单元 | JUnit 5 + Mockito | 80% | < 100ms |
| 集成 | Spring Boot Test + 真实 SQLite | 15% | < 5s |
| E2E | WebSocket + 真实 Plugin mock | 5% | < 30s |

---

## 覆盖率目标

| 模块 | 目标 |
|---|---|
| `plugin.*` | ≥ 90% |
| `capability.*` | ≥ 90% |
| `evidence.*` | ≥ 85% |
| `evolution.*` | ≥ 85% |
| `service.*` | ≥ 80% |
| `controller.*` | ≥ 70% |
| **整体** | **≥ 85%** |

用 JaCoCo 验证：

```cmd
mvn -B test jacoco:report
```

报告：`target/site/jacoco/index.html`

---

## 单元测试规范

### 1. 测试命名

```java
// 类名: XxxTest
class PluginManagerTest { }

// 方法名: should{行为}_when{条件}
@Test
void shouldRegisterPlugin_whenMetadataValid() { }

@Test
void shouldReject_whenDuplicateId() { }

@Test
void shouldRollback_whenOnLoadFails() { }
```

### 2. AAA 模式（Arrange-Act-Assert）

```java
@Test
void shouldCalculateScore() {
    // Arrange
    Plugin plugin = mockPlugin("a");
    AgentTask task = AgentTask.of("t1", "LEAD", "do");
    
    // Act
    double score = calculator.calculate(plugin, task, emptyProfile());
    
    // Assert
    assertThat(score).isGreaterThan(0);
}
```

### 3. Mock 规范

```java
// 优先用 @ExtendWith(MockitoExtension.class)
@ExtendWith(MockitoExtension.class)
class XxxTest {
    @Mock Plugin pluginA;
    @Mock Plugin pluginB;
}

// 避免 mock 静态方法、final class、private method
// 必要时用 mockito-inline

// Stubbing
when(plugin.invoke(any())).thenReturn(successResult);

// Verification
verify(plugin, times(2)).invoke(any());
verify(plugin, never()).cancel();
```

### 4. AssertJ 优先

```java
// ✅ 好
assertThat(result.status()).isEqualTo(SUCCESS);
assertThat(result.findings()).hasSize(2);
assertThat(list).containsExactly("a", "b", "c");

// ❌ 避免
assertEquals(SUCCESS, result.status());
```

### 5. 异常断言

```java
assertThatThrownBy(() -> manager.register(plugin))
    .isInstanceOf(IllegalStateException.class)
    .hasMessageContaining("already registered");
```

---

## 关键测试场景

### Plugin 系统

| 场景 | 测试方法 |
|---|---|
| 正常注册 Plugin | `shouldRegisterPlugin` |
| 重复 ID 拒绝 | `shouldReject_whenDuplicateId` |
| onLoad 失败回滚 | `shouldRollback_whenOnLoadFails` |
| 注销清理能力 | `shouldUnregisterCapabilities` |
| 事件订阅在注销时清理 | `shouldUnsubscribeOnUnregister` |
| 健康检查失败熔断 | `shouldBlacklist_whenUnhealthy` |

### TaskScheduler

| 场景 | 测试方法 |
|---|---|
| 任务依赖顺序 | `shouldExecuteInDependencyOrder` |
| 循环依赖检测 | `shouldDetectCyclicDependency` |
| 重试策略 | `shouldRetryOnFailure` |
| 超时取消 | `shouldCancel_onTimeout` |
| Fallback | `shouldFallback_whenFailure` |

### CapabilityRouter

| 场景 | 测试方法 |
|---|---|
| 按显式能力路由 | `shouldRouteByExplicitCapability` |
| 按 objective 推理能力 | `shouldInferCapability` |
| 哲学匹配优先 | `shouldPreferPhilosophyMatch` |
| 项目历史加权 | `shouldWeightProjectPerformance` |
| 全局 fallback | `shouldFallbackToGlobal` |
| 黑名单排除 | `shouldExcludeBlacklisted` |

### Verifier

| 场景 | 测试方法 |
|---|---|
| 验证 git diff 一致 | `shouldVerifyAllFiles` |
| 验证发现缺失文件 | `shouldDetectMissingFiles` |
| 测试运行成功 | `shouldRunTests` |
| 测试失败报告 | `shouldReportTestFailure` |
| 无测试框架 | `shouldSkipWhenNoFramework` |

### Evolution

| 场景 | 测试方法 |
|---|---|
| 第一次记录 | `shouldRecordFirstSample` |
| 滚动平均正确 | `shouldRollingAverage` |
| Bayesian 更新 | `shouldBayesianUpdate` |
| Lesson 自动提炼 | `shouldExtractPattern` |
| Drift 检测 | `shouldDetectDrift` |
| 推荐生成 | `shouldGenerateRecommendation` |

---

## 集成测试

### Schema 集成测试

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class SchemaIntegrationTest {
    
    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    
    @Test
    void shouldApplyAllMigrations() {
        flyway.migrate();
        // 验证表结构
    }
    
    @Test
    void shouldSupportBasicCrud() {
        // 用真实 SQLite 测 CRUD
    }
}
```

### Plugin 集成测试（不带真实 CLI）

```java
@SpringBootTest
class PluginIntegrationTest {
    
    @Autowired PluginManager pluginManager;
    @MockBean ClaudeCodePlugin mockClaude;
    @MockBean CodexPlugin mockCodex;
    
    @Test
    void shouldRouteTaskToBestPlugin() {
        // 注册 mock plugins
        when(mockClaude.invoke(any())).thenReturn(successResult);
        when(mockCodex.invoke(any())).thenReturn(successResult);
        pluginManager.register(mockClaude);
        pluginManager.register(mockCodex);
        
        // 创建 task 并路由
        AgentTask task = AgentTask.of("t1", "LEAD", "实现");
        // 验证路由到 EXCELLENT 的那个
    }
}
```

---

## E2E 测试

### 关键场景

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndToEndTaskFlowTest {
    
    @LocalServerPort int port;
    @MockBean ClaudeCodePlugin mockClaude;
    @MockBean CodexPlugin mockCodex;
    
    @Test
    void shouldCompleteFullTaskFlow() {
        // 1. 创建项目
        String projectId = createProject("/tmp/test");
        
        // 2. 配置团队
        configureTeam(projectId, "LEAD", "claude-code");
        configureTeam(projectId, "REVIEWER", "codex");
        
        // 3. 提交任务
        String taskId = submitTask(projectId, "实现 JWT");
        
        // 4. 等待完成（用 polling 或 WebSocket）
        await().atMost(30, SECONDS).until(() -> taskStatus(taskId) == COMPLETED);
        
        // 5. 验证
        TaskExecution task = getTask(taskId);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getLeadPluginId()).isEqualTo("claude-code");
        
        // 6. 验证有 verifier 证据
        assertThat(taskSteps(taskId)).anyMatch(s -> 
            "git-verifier".equals(s.getPluginId())
        );
        
        // 7. 验证 Performance 记录被写入
        List<PerformanceRecord> records = performanceRepo.findByProjectIdAndRole(projectId, "LEAD");
        assertThat(records).isNotEmpty();
    }
}
```

---

## 测试替身

### Mock CLI Process

```java
// 使用 ProcessFactory 接口注入
class MockProcessFactory implements ProcessFactory {
    private String stdout;
    private int exitCode;
    private IOException failure;
    
    public MockProcess mockProcess(String stdout, int exitCode) {
        this.stdout = stdout;
        this.exitCode = exitCode;
        this.failure = null;
        return new MockProcess(this);
    }
    
    public void mockProcessFailure(IOException failure) {
        this.failure = failure;
    }
    
    @Override
    public Process start(ProcessBuilder pb) throws IOException {
        if (failure != null) throw failure;
        return new MockProcess(this);
    }
}

class MockProcess extends Process {
    // 实现所有 Process 抽象方法
    // 通常用 ByteArrayInputStream 模拟 stdout/stderr
}
```

---

## 性能测试

### Plugin 调用基准

```java
@Test
void shouldHandle1000TasksPerMinute() {
    // 模拟 1000 个任务
    // 验证能在 60s 内完成
}
```

### SQLite 并发

```java
@Test
void shouldHandleConcurrentWrites() {
    // 多线程并发写入
    // 验证 WAL 模式正常工作
}
```

---

## Testcontainers（可选）

如果需要真实数据库测试（不推荐，因已有 SQLite）：

```java
@Testcontainers
class PostgresIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:15");
    
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
    }
}
```

> **不推荐用于本项目**。SQLite 内存模式已足够。

---

## 测试运行

### 跑所有测试

```cmd
mvn -B test
```

### 跑特定测试

```cmd
mvn -B test -Dtest=PluginManagerTest
```

### 跑特定方法

```cmd
mvn -B test -Dtest=PluginManagerTest#shouldRegisterPlugin
```

### 跑特定包

```cmd
mvn -B test -Dtest='com.teammind.plugin.*'
```

### 跳过测试（仅编译）

```cmd
mvn -B compile
mvn -B package -DskipTests
```

---

## CI 集成（未来）

```yaml
# .github/workflows/test.yml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: 17
      - run: mvn -B test jacoco:report
      - uses: codecov/codecov-action@v3
```

---

## 踩坑清单

> 测试中遇到的问题，写在这里。

### 1. SQLite 测试要每次新建库

```java
@BeforeEach
void cleanDb() {
    // 内存 SQLite 自动重置
    // 文件 SQLite 需要手动清
}
```

### 2. Mock Process 的 destroyForcibly

```java
@Override
public Process destroyForcibly() {
    destroyed = true;
    return this;  // 必须 return this
}
```

### 3. 时间相关测试用 Clock 注入

```java
class DriftingDetectorTest {
    Clock clock = Clock.fixed(...);
    // 注入 clock 而非 Instant.now()
}
```

---

## 接下来

- 读 [environment-setup.md](environment-setup.md)，准备开发环境

---

**最后更新**：2026-08-14
**版本**：v0.1