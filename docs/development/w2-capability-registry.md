# W2.2: Capability Registry + Routing 实现

> 实现按能力而非名字调度 Agent 的核心算法。
>
> 预计工作量：**1.5 天**

---

## 任务目标

- `CapabilityRegistry`：按能力索引 Plugin
- `CapabilityRouter`：根据能力 + 哲学 + 历史表现打分
- 与 `ProjectAgentProfile` 集成（项目级数据驱动）

---

## DoD

- [ ] `CapabilityRegistry` 能按能力名查找 Plugin
- [ ] `CapabilityRouter` 评分函数完整（5 权重）
- [ ] 支持 `philosophyHint` 匹配
- [ ] 项目级数据不足时回退全局
- [ ] 单元测试覆盖率 ≥ 85%

---

## 1. Capability Registry

### 1.1 接口

**位置**：`com.teammind.plugin.CapabilityRegistry`

```java
package com.teammind.plugin;

import java.util.Collection;
import java.util.Optional;

public interface CapabilityRegistry {
    
    /** 注册 Plugin 的某个能力 */
    void register(CapabilityDescriptor capability, Plugin plugin);
    
    /** 按能力名查找所有 Plugin */
    Collection<Plugin> findByCapability(String name);
    
    /** 按能力名 + 最低质量查找 */
    Collection<Plugin> findByCapabilityAndQuality(
        String name, CapabilityDescriptor.CapabilityQuality minQuality
    );
    
    /** 注销某 Plugin 的所有能力 */
    void unregisterByPlugin(String pluginId);
    
    /** 临时熔断某个 Plugin（健康检查失败时） */
    void blacklist(String pluginId, String reason);
    
    /** 取消熔断 */
    void whitelist(String pluginId);
    
    /** 是否被熔断 */
    boolean isBlacklisted(String pluginId);
}
```

### 1.2 实现

**位置**：`com.teammind.plugin.DefaultCapabilityRegistry`

```java
package com.teammind.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class DefaultCapabilityRegistry implements CapabilityRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultCapabilityRegistry.class);
    
    private final ConcurrentHashMap<String, List<PluginEntry>> byCapability = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> pluginCapabilities = new ConcurrentHashMap<>();
    private final Set<String> blacklistedPlugins = ConcurrentHashMap.newKeySet();
    private final Map<String, String> blacklistReasons = new ConcurrentHashMap<>();
    
    @Override
    public void register(CapabilityDescriptor capability, Plugin plugin) {
        String pluginId = plugin.metadata().id();
        
        byCapability.computeIfAbsent(capability.name(), k -> new CopyOnWriteArrayList<>())
            .add(new PluginEntry(plugin, capability.quality()));
        
        pluginCapabilities.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet())
            .add(capability.name());
    }
    
    @Override
    public Collection<Plugin> findByCapability(String name) {
        List<PluginEntry> entries = byCapability.getOrDefault(name, List.of());
        return entries.stream()
            .map(PluginEntry::plugin)
            .filter(p -> !isBlacklisted(p.metadata().id()))
            .collect(Collectors.toList());
    }
    
    @Override
    public Collection<Plugin> findByCapabilityAndQuality(
        String name, CapabilityDescriptor.CapabilityQuality minQuality
    ) {
        List<PluginEntry> entries = byCapability.getOrDefault(name, List.of());
        return entries.stream()
            .filter(e -> e.quality().score >= minQuality.score)
            .map(PluginEntry::plugin)
            .filter(p -> !isBlacklisted(p.metadata().id()))
            .collect(Collectors.toList());
    }
    
    @Override
    public void unregisterByPlugin(String pluginId) {
        Set<String> caps = pluginCapabilities.remove(pluginId);
        if (caps == null) return;
        
        for (String cap : caps) {
            List<PluginEntry> entries = byCapability.get(cap);
            if (entries != null) {
                entries.removeIf(e -> e.plugin().metadata().id().equals(pluginId));
            }
        }
    }
    
    @Override
    public void blacklist(String pluginId, String reason) {
        blacklistedPlugins.add(pluginId);
        blacklistReasons.put(pluginId, reason);
        log.warn("Plugin blacklisted: {} ({})", pluginId, reason);
    }
    
    @Override
    public void whitelist(String pluginId) {
        blacklistedPlugins.remove(pluginId);
        blacklistReasons.remove(pluginId);
        log.info("Plugin whitelisted: {}", pluginId);
    }
    
    @Override
    public boolean isBlacklisted(String pluginId) {
        return blacklistedPlugins.contains(pluginId);
    }
    
    private record PluginEntry(Plugin plugin, CapabilityDescriptor.CapabilityQuality quality) {}
}
```

---

## 2. Capability Router

### 2.1 接口

**位置**：`com.teammind.capability.CapabilityRouter`

```java
package com.teammind.capability;

import com.teammind.plugin.Plugin;
import com.teammind.plugin.AgentTask;

import java.util.List;
import java.util.Optional;

public interface CapabilityRouter {
    
    /**
     * 根据任务路由到最合适的 Plugin。
     */
    Optional<Plugin> route(AgentTask task, RoutingContext context);
    
    /**
     * 推断任务需要的能力。
     */
    String inferRequiredCapability(AgentTask task);
}

record RoutingContext(
    com.teammind.domain.SharedState sharedState,
    com.teammind.domain.ProjectAgentProfile agentProfile
) {}
```

### 2.2 Routing Score Calculator

**位置**：`com.teammind.capability.RoutingScoreCalculator`

```java
package com.teammind.capability;

import com.teammind.plugin.*;
import com.teammind.domain.*;

import java.util.List;

public class RoutingScoreCalculator {
    
    private static final double WEIGHT_PROJECT_PERFORMANCE = 40.0;
    private static final double WEIGHT_PHILOSOPHY = 20.0;
    private static final double WEIGHT_CAPABILITY_QUALITY = 15.0;
    private static final double WEIGHT_USER_PREFERENCE = 10.0;
    
    /**
     * 计算 Plugin 对当前任务的适用性评分。
     */
    public double calculate(
        Plugin plugin,
        AgentTask task,
        ProjectAgentProfile profile
    ) {
        double score = 0;
        
        // ─── 权重 1（40）：项目级历史表现 ───
        score += projectPerformanceScore(plugin, task, profile);
        
        // ─── 权重 2（20）：哲学匹配 ───
        score += philosophyScore(plugin, task);
        
        // ─── 权重 3（15）：能力声明质量 ───
        score += capabilityQualityScore(plugin, task);
        
        // ─── 权重 4（10）：用户显式偏好 ───
        score += userPreferenceScore(plugin, task);
        
        // ─── 权重 5（扣分）：成本与延迟 ───
        score -= costLatencyPenalty(plugin);
        
        return score;
    }
    
    private double projectPerformanceScore(
        Plugin plugin, AgentTask task, ProjectAgentProfile profile
    ) {
        String pluginId = plugin.metadata().id();
        String role = task.role();
        
        // 项目级数据优先
        PerformanceRecord projectRec = profile
            .performanceByRole()
            .getOrDefault(role, Map.of())
            .get(pluginId);
        
        if (projectRec != null && projectRec.sampleSize() >= 5) {
            return projectRec.successRate() * WEIGHT_PROJECT_PERFORMANCE;
        }
        
        // 样本不足，回退全局
        PerformanceRecord globalRec = profile.globalPerformance().get(pluginId);
        if (globalRec != null) {
            return globalRec.successRate() * (WEIGHT_PROJECT_PERFORMANCE * 0.75);
        }
        
        // 无数据，中性
        return WEIGHT_PROJECT_PERFORMANCE * 0.5;
    }
    
    private double philosophyScore(Plugin plugin, AgentTask task) {
        List<String> hint = task.philosophyHint();
        if (hint == null || hint.isEmpty()) return 0;
        
        AgentPhilosophy philosophy = plugin.metadata().philosophy();
        if (philosophy == null || philosophy.primary().isEmpty()) return 0;
        
        long matches = hint.stream()
            .filter(philosophy.primary()::contains)
            .count();
        
        return ((double) matches / hint.size()) * WEIGHT_PHILOSOPHY;
    }
    
    private double capabilityQualityScore(Plugin plugin, AgentTask task) {
        return plugin.capabilities().stream()
            .filter(c -> c.name().equals(task.requiredCapability()))
            .findFirst()
            .map(c -> c.quality().score * (WEIGHT_CAPABILITY_QUALITY / 3.0))
            .orElse(0.0);
    }
    
    private double userPreferenceScore(Plugin plugin, AgentTask task) {
        if (task.preferredPluginId() != null 
            && task.preferredPluginId().equals(plugin.metadata().id())) {
            return WEIGHT_USER_PREFERENCE;
        }
        return 0;
    }
    
    private double costLatencyPenalty(Plugin plugin) {
        // 简化：从 metadata 提取
        PluginMetadata meta = plugin.metadata();
        if (meta.runtimeHints() == null) return 0;
        
        Object latency = meta.runtimeHints().get("avgLatencyMs");
        Object cost = meta.runtimeHints().get("costPerInvocation");
        
        double penalty = 0;
        if (latency instanceof Number n) {
            penalty += n.doubleValue() / 1000.0;  // 1秒 = 1分
        }
        if (cost instanceof Number n) {
            penalty += n.doubleValue() * 5.0;     // $1 = 5分
        }
        return penalty;
    }
}
```

### 2.3 Router 实现

**位置**：`com.teammind.capability.DefaultCapabilityRouter`

```java
package com.teammind.capability;

import com.teammind.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class DefaultCapabilityRouter implements CapabilityRouter {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultCapabilityRouter.class);
    
    private final CapabilityRegistry registry;
    private final RoutingScoreCalculator scoreCalculator;
    
    public DefaultCapabilityRouter(
        CapabilityRegistry registry,
        RoutingScoreCalculator scoreCalculator
    ) {
        this.registry = registry;
        this.scoreCalculator = scoreCalculator;
    }
    
    @Override
    public Optional<Plugin> route(AgentTask task, RoutingContext context) {
        String capability = inferRequiredCapability(task);
        
        Collection<Plugin> candidates = registry.findByCapability(capability);
        if (candidates.isEmpty()) {
            log.warn("No plugin found for capability: {}", capability);
            return Optional.empty();
        }
        
        // 评分
        List<ScoredPlugin> scored = candidates.stream()
            .map(p -> new ScoredPlugin(
                p,
                scoreCalculator.calculate(p, task, context.agentProfile())
            ))
            .sorted(Comparator.comparingDouble(ScoredPlugin::score).reversed())
            .collect(Collectors.toList());
        
        ScoredPlugin winner = scored.get(0);
        log.debug("Routed task {} to {} (score: {})", 
            task.taskId(), winner.plugin().metadata().id(), winner.score());
        
        return Optional.of(winner.plugin());
    }
    
    @Override
    public String inferRequiredCapability(AgentTask task) {
        // 显式优先
        if (task.requiredCapability() != null && !task.requiredCapability().isBlank()) {
            return task.requiredCapability();
        }
        
        // 关键字推理
        String objective = task.objective().toLowerCase();
        
        if (containsAny(objective, "审查", "review")) return "code_review";
        if (containsAny(objective, "实现", "implement", "写")) return "implementation";
        if (containsAny(objective, "测试", "test", "jest", "pytest")) return "test_generation";
        if (containsAny(objective, "架构", "architect", "design")) return "architecture_design";
        if (containsAny(objective, "重构", "refactor")) return "refactoring";
        if (containsAny(objective, "文档", "document", "readme")) return "documentation";
        if (containsAny(objective, "研究", "research", "搜索")) return "research";
        if (containsAny(objective, "安全", "security", "权限", "permission")) return "security_review";
        if (containsAny(objective, "调试", "debug", "bug")) return "debugging";
        
        return "general_purpose";
    }
    
    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }
    
    private record ScoredPlugin(Plugin plugin, double score) {}
}
```

---

## 3. AgentTask 扩展

**位置**：`com.teammind.plugin.AgentTask`

```java
package com.teammind.plugin;

import java.util.List;

public record AgentTask(
    String taskId,
    String role,                       // "LEAD" / "REVIEWER" / ...
    String objective,
    List<String> constraints,
    TaskContext context,
    List<String> philosophyHint,      // ["safety", "controlled_action"]
    String requiredCapability,        // 显式指定（可选）
    String preferredPluginId          // 用户偏好（可选）
) {
    // 简化构造器
    public static AgentTask of(String taskId, String role, String objective) {
        return new AgentTask(
            taskId, role, objective, List.of(), null, null, null, null
        );
    }
}

record TaskContext(
    String projectSummary,
    List<String> relevantContext,
    List<Artifact> previousArtifacts
) {}
```

---

## 4. 与 Event Bus 联动

注册到 EventBus：路由决策应被记录。

**位置**：`com.teammind.capability.RoutingEventRecorder`

```java
package com.teammind.capability;

import com.teammind.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Component
public class RoutingEventRecorder {
    
    private static final Logger log = LoggerFactory.getLogger(RoutingEventRecorder.class);
    
    private final EventBus eventBus;
    private final RoutingScoreCalculator scoreCalculator;
    private final CapabilityRegistry registry;
    
    public RoutingEventRecorder(
        EventBus eventBus,
        RoutingScoreCalculator scoreCalculator,
        CapabilityRegistry registry
    ) {
        this.eventBus = eventBus;
        this.scoreCalculator = scoreCalculator;
        this.registry = registry;
    }
    
    @PostConstruct
    public void registerHandlers() {
        eventBus.subscribe("task.scheduled", "routing-recorder", event -> {
            // 这里只演示结构，实际订阅 task.scheduled 需要先有事件发出
            log.debug("Task scheduled: {}", event.payload());
        });
    }
}
```

---

## 5. 测试

### 5.1 CapabilityRegistryTest

```java
class CapabilityRegistryTest {
    
    Plugin pluginA, pluginB, pluginC;
    DefaultCapabilityRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new DefaultCapabilityRegistry();
        
        // pluginA: code_review EXCELLENT
        pluginA = mockPlugin("a", cap("code_review", EXCELLENT));
        // pluginB: code_review GOOD
        pluginB = mockPlugin("b", cap("code_review", GOOD));
        // pluginC: implementation EXCELLENT
        pluginC = mockPlugin("c", cap("implementation", EXCELLENT));
    }
    
    @Test
    void shouldRegisterAndFind() {
        registry.register(cap("code_review", EXCELLENT), pluginA);
        
        assertThat(registry.findByCapability("code_review")).contains(pluginA);
    }
    
    @Test
    void shouldFilterByQuality() {
        registry.register(cap("code_review", EXCELLENT), pluginA);
        registry.register(cap("code_review", GOOD), pluginB);
        
        Collection<Plugin> excellentOnly = registry
            .findByCapabilityAndQuality("code_review", EXCELLENT);
        
        assertThat(excellentOnly).contains(pluginA).doesNotContain(pluginB);
    }
    
    @Test
    void shouldExcludeBlacklisted() {
        registry.register(cap("code_review", EXCELLENT), pluginA);
        registry.blacklist("a", "test");
        
        assertThat(registry.findByCapability("code_review")).isEmpty();
    }
    
    @Test
    void shouldUnregisterByPlugin() {
        registry.register(cap("code_review", EXCELLENT), pluginA);
        registry.register(cap("implementation", EXCELLENT), pluginA);
        
        registry.unregisterByPlugin("a");
        
        assertThat(registry.findByCapability("code_review")).isEmpty();
        assertThat(registry.findByCapability("implementation")).isEmpty();
    }
    
    private CapabilityDescriptor cap(String name, CapabilityQuality q) {
        return new CapabilityDescriptor(name, q, null);
    }
    
    private Plugin mockPlugin(String id, CapabilityDescriptor cap) {
        Plugin p = mock(Plugin.class);
        PluginMetadata meta = new PluginMetadata(
            id, PluginMetadata.PluginType.AGENT, id, "1.0",
            null, null, null, AgentPhilosophy.empty(), null
        );
        when(p.metadata()).thenReturn(meta);
        when(p.capabilities()).thenReturn(List.of(cap));
        return p;
    }
}
```

### 5.2 RoutingScoreCalculatorTest

```java
class RoutingScoreCalculatorTest {
    
    RoutingScoreCalculator calculator = new RoutingScoreCalculator();
    
    @Test
    void shouldWeightProjectPerformanceHighest() {
        Plugin plugin = mockPlugin("a");
        AgentTask task = AgentTask.of("t1", "LEAD", "do something");
        ProjectAgentProfile profile = new ProjectAgentProfile(
            "p1",
            Map.of("LEAD", Map.of("a", new PerformanceRecord(
                1.0, 0, 0, 100, "2024-01-01"
            ))),
            Map.of(),
            List.of(),
            List.of()
        );
        
        double score = calculator.calculate(plugin, task, profile);
        
        // 100% success * 40 = 40
        assertThat(score).isGreaterThanOrEqualTo(40);
    }
    
    @Test
    void shouldMatchPhilosophy() {
        Plugin plugin = mockPlugin("a", 
            new AgentPhilosophy(List.of("safety", "execution"), List.of(), List.of(), List.of())
        );
        AgentTask task = new AgentTask(
            "t1", "LEAD", "do", List.of(), null,
            List.of("safety"), null, null
        );
        
        double score = calculator.calculate(plugin, task, emptyProfile());
        
        // 100% philosophy match * 20 = 20
        assertThat(score).isGreaterThanOrEqualTo(20);
    }
    
    @Test
    void shouldFallbackToGlobalWhenProjectSampleInsufficient() {
        Plugin plugin = mockPlugin("a");
        AgentTask task = AgentTask.of("t1", "LEAD", "do");
        ProjectAgentProfile profile = new ProjectAgentProfile(
            "p1",
            Map.of("LEAD", Map.of("a", new PerformanceRecord(
                0.5, 0, 0, 2, "2024-01-01"  // 样本数 < 5
            ))),
            Map.of("a", new PerformanceRecord(1.0, 0, 0, 50, "2024-01-01")),
            List.of(), List.of()
        );
        
        double score = calculator.calculate(plugin, task, profile);
        
        // 全局 100% * 30 = 30（不是 40）
        assertThat(score).isBetween(30.0, 40.0);
    }
}
```

### 5.3 CapabilityRouterTest

```java
class CapabilityRouterTest {
    
    Plugin pluginA, pluginB;
    DefaultCapabilityRouter router;
    DefaultCapabilityRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new DefaultCapabilityRegistry();
        router = new DefaultCapabilityRouter(registry, new RoutingScoreCalculator());
        
        pluginA = mockPlugin("a", cap("implementation", EXCELLENT));
        pluginB = mockPlugin("b", cap("implementation", GOOD));
        
        registry.register(cap("implementation", EXCELLENT), pluginA);
        registry.register(cap("implementation", GOOD), pluginB);
    }
    
    @Test
    void shouldRouteByExplicitCapability() {
        AgentTask task = new AgentTask(
            "t1", "LEAD", "实现 JWT", List.of(), null,
            null, "implementation", null
        );
        RoutingContext ctx = new RoutingContext(null, emptyProfile());
        
        Optional<Plugin> result = router.route(task, ctx);
        
        assertThat(result).isPresent();
        // 默认两个插件都没项目数据，EXCELLENT 胜出
        assertThat(result.get()).isSameAs(pluginA);
    }
    
    @Test
    void shouldInferCapabilityFromObjective() {
        AgentTask task = AgentTask.of("t1", "REVIEWER", "审查这段代码");
        RoutingContext ctx = new RoutingContext(null, emptyProfile());
        
        Optional<Plugin> result = router.route(task, ctx);
        
        assertThat(result).isEmpty();  // 没有 code_review Plugin
    }
    
    @Test
    void shouldRespectPreferredPlugin() {
        AgentTask task = new AgentTask(
            "t1", "LEAD", "do", List.of(), null,
            null, "implementation", "b"  // 用户偏好 b
        );
        RoutingContext ctx = new RoutingContext(null, emptyProfile());
        
        Optional<Plugin> result = router.route(task, ctx);
        
        assertThat(result).contains(pluginB);
    }
}
```

---

## 6. Spring 集成

### 6.1 自动配置

添加到 `RuntimeAutoConfig`：

```java
@Bean
public CapabilityRegistry capabilityRegistry() {
    return new DefaultCapabilityRegistry();
}

@Bean
public RoutingScoreCalculator routingScoreCalculator() {
    return new RoutingScoreCalculator();
}

@Bean
public CapabilityRouter capabilityRouter(
    CapabilityRegistry registry,
    RoutingScoreCalculator calculator
) {
    return new DefaultCapabilityRouter(registry, calculator);
}
```

---

## 7. 验收清单

- [ ] CapabilityRegistry / Router 实现完成
- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 所有测试通过
- [ ] 路由决策有日志输出
- [ ] 黑名单机制生效
- [ ] 可与 ProjectAgentProfile 集成（即使数据为空也能工作）

---

## 8. 接下来

- 读 [w2-schema-migration.md](w2-schema-migration.md)，设计支持 project / agent profile 的 SQLite schema

---

**最后更新**：2026-08-14
**版本**：v0.1 Draft