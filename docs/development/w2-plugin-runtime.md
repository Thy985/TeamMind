# W2.1: Plugin Runtime 实现

> Cordis-like Plugin Runtime 的 Java 实现。
>
> 预计工作量：**2 天**

---

## 任务目标

实现 Plugin 系统的核心 Runtime：
- `Plugin` 接口（定义 Plugin 行为）
- `PluginManager`（生命周期管理）
- `TaskScheduler`（依赖调度）
- `EventBus`（事件分发）
- `HealthMonitor`（健康检查）

---

## DoD（Definition of Done）

- [ ] `Plugin` 接口定义完整，含 metadata / capabilities / lifecycle / invoke / cancel
- [ ] `PluginManager` 能加载、注册、卸载 Plugin
- [ ] `TaskScheduler` 支持任务依赖拓扑排序 + 重试 + 回退
- [ ] `EventBus` 支持发布/订阅，含类型安全
- [ ] `HealthMonitor` 周期性检查 Plugin 健康状态
- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 与现有 `WSEventPublisher` 兼容（不冲突）

---

## 1. Plugin 接口定义

### 1.1 核心接口

**位置**：`com.teammind.plugin.Plugin`

```java
package com.teammind.plugin;

import java.util.List;
import java.util.Map;

/**
 * 所有 Plugin 必须实现的接口。
 * 
 * Core 只与本接口交互，不关心具体实现。
 */
public interface Plugin {
    
    /** 元数据（id, name, version, type, philosophy...） */
    PluginMetadata metadata();
    
    /** 能力声明 */
    List<CapabilityDescriptor> capabilities();
    
    /** 生命周期钩子 */
    default PluginLifecycle lifecycle() {
        return PluginLifecycle.empty();
    }
    
    /** 调用入口 */
    PluginResult invoke(PluginContext context);
    
    /** 流式调用（可选） */
    default PluginResult stream(PluginContext context, ChunkHandler chunkHandler) {
        throw new UnsupportedOperationException("Stream not supported");
    }
    
    /** 取消执行 */
    default void cancel() {
        // 默认无操作
    }
    
    /** 健康检查 */
    default PluginHealth inspect() {
        return PluginHealth.healthy();
    }
}
```

### 1.2 元数据

**位置**：`com.teammind.plugin.PluginMetadata`

```java
package com.teammind.plugin;

import java.util.List;
import java.util.Map;

/**
 * Plugin 元数据。Plugin 注册时由 Plugin 自身提供。
 */
public record PluginMetadata(
    String id,                       // "claude-code" / "codex"
    PluginType type,                 // AGENT / VERIFIER / MEMORY / ...
    String name,                     // 人类可读名
    String version,                  // semver
    String author,
    String description,
    String homepage,
    AgentPhilosophy philosophy,      // 仅 AGENT 类型有
    Map<String, Object> runtimeHints // 任意附加元数据
) {
    
    public enum PluginType {
        AGENT,           // Claude / Codex / Aider
        VERIFIER,        // Test Runner / Lint
        MEMORY,          // Project Memory / Task Memory
        INTEGRATION,     // GitHub / GitLab / Jira
        CONTEXT          // Git / Obsidian / File System
    }
}
```

### 1.3 Agent Philosophy

**位置**：`com.teammind.plugin.AgentPhilosophy`

```java
package com.teammind.plugin;

import java.util.List;

/**
 * Agent Plugin 的设计哲学。
 * 数据来源：CLI 官方文档，非主观打分。
 */
public record AgentPhilosophy(
    List<String> primary,            // ['safety', 'controlled_action']
    List<String> designGoals,        // ['可控的权限边界', '...']
    List<String> preferredRoles,     // ['security_review', '...']
    List<String> weakRoles           // ['bulk_refactor', '...']
) {
    public static AgentPhilosophy empty() {
        return new AgentPhilosophy(List.of(), List.of(), List.of(), List.of());
    }
}
```

### 1.4 Capability Descriptor

**位置**：`com.teammind.plugin.CapabilityDescriptor`

```java
package com.teammind.plugin;

/**
 * 能力描述符。Plugin 声明自己能做什么。
 */
public record CapabilityDescriptor(
    String name,                     // "code_review" / "implementation"
    CapabilityQuality quality,      // EXCELLENT / GOOD / FAIR / POOR
    String description
) {
    public enum CapabilityQuality {
        EXCELLENT(3), GOOD(2), FAIR(1), POOR(0);
        
        public final int score;
        CapabilityQuality(int score) { this.score = score; }
    }
}
```

### 1.5 Plugin Lifecycle

**位置**：`com.teammind.plugin.PluginLifecycle`

```java
package com.teammind.plugin;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Plugin 生命周期钩子。Plugin 可选实现。
 */
public interface PluginLifecycle {
    
    /** Plugin 加载时调用（注入依赖、启动子进程） */
    default void onLoad() throws Exception {}
    
    /** Plugin 卸载时调用（清理资源） */
    default void onUnload() throws Exception {}
    
    /** 订阅事件 */
    default List<EventSubscription> hooks() {
        return List.of();
    }
    
    static PluginLifecycle empty() {
        return new PluginLifecycle() {};
    }
}

record EventSubscription(
    String eventType,
    Consumer<RuntimeEvent> handler
) {}
```

### 1.6 Plugin Context

**位置**：`com.teammind.plugin.PluginContext`

```java
package com.teammind.plugin;

import java.util.Map;

/**
 * Plugin 执行时的上下文。
 * 
 * 设计原则：Plugin 不需要自己拉取数据，所有数据由 Runtime 注入。
 */
public record PluginContext(
    AgentTask task,                  // 任务定义
    SharedStateRef sharedState,      // 项目级共享状态（只读视图）
    String workDir,                  // 工作目录
    long timeoutMs,                  // 超时
    PluginCapabilityAPI capabilities // 运行时能力 API
) {
    // 构造器省略
}
```

### 1.7 Plugin Result

**位置**：`com.teammind.plugin.PluginResult`

```java
package com.teammind.plugin;

import java.time.Duration;
import java.util.List;

/**
 * Plugin 执行结果。
 * 
 * 关键：不是 stdout 文本，而是结构化产物。
 */
public record PluginResult(
    String pluginId,
    String taskId,
    PluginStatus status,             // SUCCESS / FAILURE / PARTIAL / NEEDS_REVIEW
    String summary,                  // 一句话总结（给人看）
    List<Artifact> artifacts,        // 结构化产物
    List<Finding> findings,          // 发现的问题
    List<Question> questions,        // 需要 Lead 决策的问题
    List<Evidence> evidence,         // 可验证的证据
    SelfReport selfReport,           // 自评（仅供 Routing 参考）
    PerformanceMetrics performance,  // 性能数据
    NextAction nextAction            // 建议下一步
) {
    
    public enum PluginStatus {
        SUCCESS, FAILURE, PARTIAL, NEEDS_REVIEW
    }
    
    public static PluginResult success(
        String pluginId, String taskId, String summary,
        List<Artifact> artifacts, List<Evidence> evidence
    ) {
        return new PluginResult(
            pluginId, taskId, PluginStatus.SUCCESS,
            summary, artifacts, List.of(), List.of(),
            evidence,
            new SelfReport(null, null),
            new PerformanceMetrics(Duration.ZERO, null, null),
            null
        );
    }
}

record SelfReport(Double confidence, String quality) {}
record PerformanceMetrics(Duration duration, Integer tokensUsed, Double costUsd) {}
record NextAction(String suggestedRole, String suggestedCapability, String reason) {}
```

---

## 2. Plugin Manager

### 2.1 接口

**位置**：`com.teammind.plugin.PluginManager`

```java
package com.teammind.plugin;

import java.util.Collection;
import java.util.Optional;

public interface PluginManager {
    
    /** 加载并注册 Plugin */
    void register(Plugin plugin);
    
    /** 注销 Plugin */
    void unregister(String pluginId);
    
    /** 按 ID 获取 */
    Optional<Plugin> get(String id);
    
    /** 获取所有已注册 Plugin */
    Collection<Plugin> all();
    
    /** 按类型获取 */
    Collection<Plugin> byType(PluginMetadata.PluginType type);
    
    /** 按能力查找 */
    Collection<Plugin> findByCapability(String capabilityName);
    
    /** 启动（执行 onLoad + 注册事件订阅） */
    void start();
    
    /** 关闭（执行 onUnload + 清理） */
    void shutdown();
}
```

### 2.2 实现

**位置**：`com.teammind.plugin.DefaultPluginManager`

```java
package com.teammind.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class DefaultPluginManager implements PluginManager {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultPluginManager.class);
    
    private final ConcurrentHashMap<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final EventBus eventBus;
    private final CapabilityRegistry capabilityRegistry;
    
    public DefaultPluginManager(EventBus eventBus, CapabilityRegistry capabilityRegistry) {
        this.eventBus = eventBus;
        this.capabilityRegistry = capabilityRegistry;
    }
    
    @Override
    public void register(Plugin plugin) {
        PluginMetadata metadata = plugin.metadata();
        
        if (plugins.containsKey(metadata.id())) {
            throw new IllegalStateException("Plugin already registered: " + metadata.id());
        }
        
        // 1. 存储
        plugins.put(metadata.id(), plugin);
        
        // 2. 注册能力
        for (CapabilityDescriptor cap : plugin.capabilities()) {
            capabilityRegistry.register(cap, plugin);
        }
        
        // 3. 注册事件订阅
        for (EventSubscription sub : plugin.lifecycle().hooks()) {
            eventBus.subscribe(sub.eventType(), metadata.id(), sub.handler());
        }
        
        // 4. 执行 onLoad
        try {
            plugin.lifecycle().onLoad();
        } catch (Exception e) {
            log.error("Plugin onLoad failed: {}", metadata.id(), e);
            plugins.remove(metadata.id());
            throw new PluginLoadException(metadata.id(), e);
        }
        
        log.info("Plugin registered: {} ({})", metadata.id(), metadata.name());
        eventBus.emit(new RuntimeEvent("plugin.loaded", Map.of("pluginId", metadata.id())));
    }
    
    @Override
    public void unregister(String pluginId) {
        Plugin plugin = plugins.remove(pluginId);
        if (plugin == null) return;
        
        // 1. 注销能力
        capabilityRegistry.unregisterByPlugin(pluginId);
        
        // 2. 注销事件订阅
        eventBus.unsubscribeAll(pluginId);
        
        // 3. 执行 onUnload
        try {
            plugin.lifecycle().onUnload();
        } catch (Exception e) {
            log.warn("Plugin onUnload failed: {}", pluginId, e);
        }
        
        log.info("Plugin unregistered: {}", pluginId);
        eventBus.emit(new RuntimeEvent("plugin.unloaded", Map.of("pluginId", pluginId)));
    }
    
    @Override
    public Optional<Plugin> get(String id) {
        return Optional.ofNullable(plugins.get(id));
    }
    
    @Override
    public Collection<Plugin> all() {
        return plugins.values();
    }
    
    @Override
    public Collection<Plugin> byType(PluginMetadata.PluginType type) {
        return plugins.values().stream()
            .filter(p -> p.metadata().type() == type)
            .collect(Collectors.toList());
    }
    
    @Override
    public Collection<Plugin> findByCapability(String capabilityName) {
        return capabilityRegistry.findByCapability(capabilityName);
    }
    
    @Override
    public void start() {
        // 已由 Spring 启动
    }
    
    @Override
    public void shutdown() {
        // 注销所有 Plugin
        for (String id : plugins.keySet().toArray(new String[0])) {
            unregister(id);
        }
    }
}

class PluginLoadException extends RuntimeException {
    public PluginLoadException(String pluginId, Throwable cause) {
        super("Failed to load plugin: " + pluginId, cause);
    }
}
```

---

## 3. Task Scheduler

### 3.1 任务定义

**位置**：`com.teammind.plugin.ScheduledTask`

```java
package com.teammind.plugin;

import java.util.List;

public record ScheduledTask(
    String id,
    String pluginId,
    PluginContext context,
    List<String> dependsOn,           // 依赖的任务 IDs
    RetryPolicy retryPolicy,
    FailurePolicy onFailure,
    String fallbackPluginId,
    long timeoutMs
) {
    
    public record RetryPolicy(int maxRetries, long backoffMs) {
        public static RetryPolicy none() {
            return new RetryPolicy(0, 0);
        }
        public static RetryPolicy defaultPolicy() {
            return new RetryPolicy(2, 1000);
        }
    }
    
    public enum FailurePolicy {
        FAIL,           // 整体失败
        RETRY,          // 重试（由 retryPolicy 控制）
        FALLBACK,       // 切换到 fallbackPlugin
        ROLLBACK        // 回滚所有已完成任务
    }
}
```

### 3.2 Scheduler 实现

**位置**：`com.teammind.plugin.TaskScheduler`

```java
package com.teammind.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Component
public class TaskScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);
    
    private final PluginManager pluginManager;
    private final EventBus eventBus;
    private final ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() * 2
    );
    
    public TaskScheduler(PluginManager pluginManager, EventBus eventBus) {
        this.pluginManager = pluginManager;
        this.eventBus = eventBus;
    }
    
    /**
     * 执行一组任务，按依赖顺序。
     */
    public TaskRunResult run(List<ScheduledTask> tasks) {
        // 1. 拓扑排序
        List<ScheduledTask> ordered = topologicalSort(tasks);
        
        // 2. 状态
        Map<String, PluginResult> results = new ConcurrentHashMap<>();
        Set<String> completed = ConcurrentHashMap.newKeySet();
        Set<String> failed = ConcurrentHashMap.newKeySet();
        
        // 3. 按顺序执行
        for (ScheduledTask task : ordered) {
            // 等待依赖完成
            waitForDependencies(task, completed, failed);
            
            if (!failed.contains(task.id()) && 
                task.dependsOn().stream().noneMatch(failed::contains)) {
                executeTask(task, results, completed, failed);
            }
        }
        
        return new TaskRunResult(results, completed, failed);
    }
    
    private List<ScheduledTask> topologicalSort(List<ScheduledTask> tasks) {
        // Kahn's algorithm
        Map<String, ScheduledTask> byId = tasks.stream()
            .collect(Collectors.toMap(ScheduledTask::id, t -> t));
        
        Map<String, Integer> inDegree = new HashMap<>();
        for (ScheduledTask t : tasks) {
            inDegree.putIfAbsent(t.id(), 0);
            for (String dep : t.dependsOn()) {
                inDegree.merge(t.id(), 1, Integer::sum);
                inDegree.putIfAbsent(dep, 0);
            }
        }
        
        Queue<String> queue = new LinkedList<>();
        for (var e : inDegree.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }
        
        List<ScheduledTask> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            sorted.add(byId.get(id));
            
            for (ScheduledTask t : tasks) {
                if (t.dependsOn().contains(id)) {
                    int newDegree = inDegree.merge(t.id(), -1, Integer::sum);
                    if (newDegree == 0) queue.add(t.id());
                }
            }
        }
        
        if (sorted.size() != tasks.size()) {
            throw new IllegalStateException("Cyclic dependency detected");
        }
        
        return sorted;
    }
    
    private void executeTask(
        ScheduledTask task,
        Map<String, PluginResult> results,
        Set<String> completed,
        Set<String> failed
    ) {
        Plugin plugin = pluginManager.get(task.pluginId())
            .orElseThrow(() -> new PluginNotFoundException(task.pluginId()));
        
        eventBus.emit(new RuntimeEvent("task.started", Map.of(
            "taskId", task.id(), "pluginId", task.pluginId()
        )));
        
        // 提交到 executor
        Future<PluginResult> future = executor.submit(() -> 
            invokeWithRetry(plugin, task)
        );
        
        try {
            PluginResult result = future.get(task.timeoutMs(), TimeUnit.MILLISECONDS);
            results.put(task.id(), result);
            
            if (result.status() == PluginResult.PluginStatus.SUCCESS ||
                result.status() == PluginResult.PluginStatus.PARTIAL) {
                completed.add(task.id());
                eventBus.emit(new RuntimeEvent("task.completed", Map.of(
                    "taskId", task.id(), "result", result
                )));
            } else {
                handleFailure(task, failed);
            }
        } catch (TimeoutException e) {
            plugin.cancel();
            future.cancel(true);
            failed.add(task.id());
            handleFailure(task, failed);
        } catch (Exception e) {
            log.error("Task failed: {}", task.id(), e);
            failed.add(task.id());
            handleFailure(task, failed);
        }
    }
    
    private PluginResult invokeWithRetry(Plugin plugin, ScheduledTask task) {
        Exception lastError = null;
        
        for (int attempt = 0; attempt <= task.retryPolicy().maxRetries(); attempt++) {
            try {
                return plugin.invoke(task.context());
            } catch (Exception e) {
                lastError = e;
                log.warn("Plugin invoke failed (attempt {}/{}): {}", 
                    attempt + 1, task.retryPolicy().maxRetries() + 1, e.getMessage());
                
                if (attempt < task.retryPolicy().maxRetries()) {
                    try {
                        Thread.sleep(task.retryPolicy().backoffMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        throw new PluginInvocationException(
            "Plugin invocation failed after retries", lastError
        );
    }
    
    private void handleFailure(ScheduledTask task, Set<String> failed) {
        switch (task.onFailure()) {
            case FAIL -> { /* 默认失败 */ }
            case FALLBACK -> {
                if (task.fallbackPluginId() != null) {
                    log.info("Falling back to plugin: {}", task.fallbackPluginId());
                    // 重新调度（简化处理）
                }
            }
            case ROLLBACK -> {
                log.warn("Rollback policy triggered for task: {}", task.id());
            }
            case RETRY -> { /* 已由 invokeWithRetry 处理 */ }
        }
    }
    
    private void waitForDependencies(
        ScheduledTask task, Set<String> completed, Set<String> failed
    ) {
        long deadline = System.currentTimeMillis() + task.timeoutMs() * task.dependsOn().size();
        while (System.currentTimeMillis() < deadline) {
            boolean allDone = task.dependsOn().stream()
                .allMatch(dep -> completed.contains(dep) || failed.contains(dep));
            if (allDone) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}

record TaskRunResult(
    Map<String, PluginResult> results,
    Set<String> completed,
    Set<String> failed
) {}
```

---

## 4. Event Bus

### 4.1 接口

**位置**：`com.teammind.plugin.EventBus`

```java
package com.teammind.plugin;

import java.util.Map;
import java.util.function.Consumer;

public interface EventBus {
    void emit(RuntimeEvent event);
    void subscribe(String eventType, String subscriberId, Consumer<RuntimeEvent> handler);
    void unsubscribeAll(String subscriberId);
}
```

### 4.2 Runtime Event

**位置**：`com.teammind.plugin.RuntimeEvent`

```java
package com.teammind.plugin;

import java.time.Instant;
import java.util.Map;

public record RuntimeEvent(
    String type,                     // "plugin.loaded" / "task.completed"
    Map<String, Object> payload,
    Instant timestamp
) {
    public RuntimeEvent(String type, Map<String, Object> payload) {
        this(type, payload, Instant.now());
    }
}
```

### 4.3 实现

**位置**：`com.teammind.plugin.DefaultEventBus`

```java
package com.teammind.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class DefaultEventBus implements EventBus {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultEventBus.class);
    
    private final ConcurrentHashMap<String, List<Subscription>> subscriptions = new ConcurrentHashMap<>();
    
    @Override
    public void emit(RuntimeEvent event) {
        List<Subscription> handlers = subscriptions.getOrDefault(event.type(), List.of());
        for (Subscription sub : handlers) {
            try {
                sub.handler().accept(event);
            } catch (Exception e) {
                log.error("Event handler failed: type={}, subscriber={}", 
                    event.type(), sub.subscriberId(), e);
            }
        }
    }
    
    @Override
    public void subscribe(String eventType, String subscriberId, Consumer<RuntimeEvent> handler) {
        subscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add(new Subscription(subscriberId, handler));
    }
    
    @Override
    public void unsubscribeAll(String subscriberId) {
        for (List<Subscription> handlers : subscriptions.values()) {
            handlers.removeIf(s -> s.subscriberId().equals(subscriberId));
        }
    }
    
    private record Subscription(String subscriberId, Consumer<RuntimeEvent> handler) {}
}
```

---

## 5. Health Monitor

### 5.1 接口

**位置**：`com.teammind.plugin.HealthMonitor`

```java
package com.teammind.plugin;

public interface PluginHealth {
    Status status();
    String message();
    
    enum Status { HEALTHY, DEGRADED, UNHEALTHY }
    
    static PluginHealth healthy() {
        return new DefaultHealth(Status.HEALTHY, null);
    }
    static PluginHealth unhealthy(String msg) {
        return new DefaultHealth(Status.UNHEALTHY, msg);
    }
}

record DefaultHealth(PluginHealth.Status status, String message) implements PluginHealth {}
```

### 5.2 周期性监控

**位置**：`com.teammind.plugin.PluginHealthMonitor`

```java
package com.teammind.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PluginHealthMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(PluginHealthMonitor.class);
    
    private final PluginManager pluginManager;
    private final EventBus eventBus;
    private final Map<String, PluginHealth> lastHealth = new HashMap<>();
    
    public PluginHealthMonitor(PluginManager pluginManager, EventBus eventBus) {
        this.pluginManager = pluginManager;
        this.eventBus = eventBus;
    }
    
    @Scheduled(fixedDelay = 30_000) // 每 30 秒
    public void checkAll() {
        for (Plugin plugin : pluginManager.all()) {
            try {
                PluginHealth health = plugin.inspect();
                String id = plugin.metadata().id();
                
                PluginHealth previous = lastHealth.get(id);
                lastHealth.put(id, health);
                
                if (health.status() != PluginHealth.Status.HEALTHY) {
                    log.warn("Plugin unhealthy: {} - {}", id, health.message());
                    eventBus.emit(new RuntimeEvent("plugin.failed", Map.of(
                        "pluginId", id,
                        "health", health
                    )));
                } else if (previous != null && 
                           previous.status() != PluginHealth.Status.HEALTHY) {
                    // 恢复
                    eventBus.emit(new RuntimeEvent("plugin.healthy", Map.of(
                        "pluginId", id
                    )));
                }
            } catch (Exception e) {
                log.error("Health check exception: {}", plugin.metadata().id(), e);
            }
        }
    }
    
    public Map<String, PluginHealth> currentHealth() {
        return Map.copyOf(lastHealth);
    }
}
```

---

## 6. 与 Spring Boot 集成

### 6.1 自动配置

**位置**：`com.teammind.config.RuntimeAutoConfig`

```java
package com.teammind.config;

import com.teammind.plugin.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RuntimeAutoConfig {
    
    @Bean
    public PluginManager pluginManager(EventBus eventBus, CapabilityRegistry capabilityRegistry) {
        return new DefaultPluginManager(eventBus, capabilityRegistry);
    }
    
    @Bean
    public TaskScheduler taskScheduler(PluginManager pluginManager, EventBus eventBus) {
        return new TaskScheduler(pluginManager, eventBus);
    }
    
    @Bean
    public PluginHealthMonitor pluginHealthMonitor(
        PluginManager pluginManager, EventBus eventBus
    ) {
        return new PluginHealthMonitor(pluginManager, eventBus);
    }
}
```

---

## 7. 测试

### 7.1 PluginManagerTest

```java
package com.teammind.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginManagerTest {
    
    @Mock Plugin mockPlugin;
    
    PluginMetadata mockMetadata;
    DefaultPluginManager manager;
    DefaultEventBus eventBus;
    CapabilityRegistry registry;
    
    @BeforeEach
    void setUp() {
        mockMetadata = new PluginMetadata(
            "test-plugin", PluginMetadata.PluginType.AGENT,
            "Test Plugin", "1.0.0", null, "Test", null,
            AgentPhilosophy.empty(), null
        );
        when(mockPlugin.metadata()).thenReturn(mockMetadata);
        when(mockPlugin.capabilities()).thenReturn(List.of(
            new CapabilityDescriptor("code_review", CapabilityDescriptor.CapabilityQuality.GOOD, null)
        ));
        
        eventBus = new DefaultEventBus();
        registry = new DefaultCapabilityRegistry();
        manager = new DefaultPluginManager(eventBus, registry);
    }
    
    @Test
    void shouldRegisterPlugin() {
        manager.register(mockPlugin);
        
        assertThat(manager.get("test-plugin")).isPresent();
        assertThat(manager.findByCapability("code_review")).contains(mockPlugin);
    }
    
    @Test
    void shouldNotAllowDuplicateId() {
        manager.register(mockPlugin);
        
        assertThatThrownBy(() -> manager.register(mockPlugin))
            .isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    void shouldUnregisterPlugin() {
        manager.register(mockPlugin);
        manager.unregister("test-plugin");
        
        assertThat(manager.get("test-plugin")).isEmpty();
        assertThat(manager.findByCapability("code_review")).isEmpty();
    }
    
    @Test
    void shouldCallOnLoad() throws Exception {
        PluginLifecycle lifecycle = mock(PluginLifecycle.class);
        when(mockPlugin.lifecycle()).thenReturn(lifecycle);
        
        manager.register(mockPlugin);
        
        verify(lifecycle).onLoad();
    }
    
    @Test
    void shouldRollbackIfOnLoadFails() throws Exception {
        PluginLifecycle lifecycle = mock(PluginLifecycle.class);
        doThrow(new RuntimeException("onLoad failed")).when(lifecycle).onLoad();
        when(mockPlugin.lifecycle()).thenReturn(lifecycle);
        
        assertThatThrownBy(() -> manager.register(mockPlugin))
            .isInstanceOf(PluginLoadException.class);
        
        assertThat(manager.get("test-plugin")).isEmpty();
    }
}
```

### 7.2 TaskSchedulerTest

```java
@ExtendWith(MockitoExtension.class)
class TaskSchedulerTest {
    
    @Mock Plugin pluginA;
    @Mock Plugin pluginB;
    
    TaskScheduler scheduler;
    PluginManager pluginManager;
    DefaultEventBus eventBus;
    
    @BeforeEach
    void setUp() {
        eventBus = new DefaultEventBus();
        CapabilityRegistry registry = new DefaultCapabilityRegistry();
        pluginManager = new DefaultPluginManager(eventBus, registry);
        
        // Plugin A
        PluginMetadata metaA = new PluginMetadata(
            "plugin-a", PluginMetadata.PluginType.AGENT, "A", "1.0.0",
            null, null, null, AgentPhilosophy.empty(), null
        );
        when(pluginA.metadata()).thenReturn(metaA);
        when(pluginA.invoke(any())).thenReturn(PluginResult.success(
            "plugin-a", "task-a", "done", List.of(), List.of()
        ));
        
        // Plugin B
        PluginMetadata metaB = new PluginMetadata(
            "plugin-b", PluginMetadata.PluginType.AGENT, "B", "1.0.0",
            null, null, null, AgentPhilosophy.empty(), null
        );
        when(pluginB.metadata()).thenReturn(metaB);
        when(pluginB.invoke(any())).thenReturn(PluginResult.success(
            "plugin-b", "task-b", "done", List.of(), List.of()
        ));
        
        pluginManager.register(pluginA);
        pluginManager.register(pluginB);
        
        scheduler = new TaskScheduler(pluginManager, eventBus);
    }
    
    @Test
    void shouldExecuteInDependencyOrder() {
        ScheduledTask taskA = new ScheduledTask(
            "task-a", "plugin-a", null, List.of(),
            ScheduledTask.RetryPolicy.none(),
            ScheduledTask.FailurePolicy.FAIL, null, 5000
        );
        ScheduledTask taskB = new ScheduledTask(
            "task-b", "plugin-b", null, List.of("task-a"),
            ScheduledTask.RetryPolicy.none(),
            ScheduledTask.FailurePolicy.FAIL, null, 5000
        );
        
        TaskRunResult result = scheduler.run(List.of(taskA, taskB));
        
        assertThat(result.completed()).containsExactlyInAnyOrder("task-a", "task-b");
    }
    
    @Test
    void shouldDetectCyclicDependency() {
        ScheduledTask taskA = new ScheduledTask(
            "task-a", "plugin-a", null, List.of("task-b"),
            ScheduledTask.RetryPolicy.none(),
            ScheduledTask.FailurePolicy.FAIL, null, 5000
        );
        ScheduledTask taskB = new ScheduledTask(
            "task-b", "plugin-b", null, List.of("task-a"),
            ScheduledTask.RetryPolicy.none(),
            ScheduledTask.FailurePolicy.FAIL, null, 5000
        );
        
        assertThatThrownBy(() -> scheduler.run(List.of(taskA, taskB)))
            .isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    void shouldRetryOnFailure() {
        when(pluginA.invoke(any()))
            .thenThrow(new RuntimeException("First attempt failed"))
            .thenReturn(PluginResult.success("plugin-a", "task-a", "ok", List.of(), List.of()));
        
        ScheduledTask taskA = new ScheduledTask(
            "task-a", "plugin-a", null, List.of(),
            new ScheduledTask.RetryPolicy(2, 100),
            ScheduledTask.FailurePolicy.FAIL, null, 5000
        );
        
        TaskRunResult result = scheduler.run(List.of(taskA));
        
        verify(pluginA, times(2)).invoke(any());
        assertThat(result.completed()).contains("task-a");
    }
}
```

### 7.3 EventBusTest

```java
class EventBusTest {
    
    DefaultEventBus eventBus;
    
    @BeforeEach
    void setUp() {
        eventBus = new DefaultEventBus();
    }
    
    @Test
    void shouldDeliverEventToSubscribers() {
        AtomicInteger counter = new AtomicInteger();
        eventBus.subscribe("test.event", "sub1", e -> counter.incrementAndGet());
        
        eventBus.emit(new RuntimeEvent("test.event", Map.of()));
        
        assertThat(counter.get()).isEqualTo(1);
    }
    
    @Test
    void shouldIsolateSubscribers() {
        AtomicInteger c1 = new AtomicInteger();
        AtomicInteger c2 = new AtomicInteger();
        
        eventBus.subscribe("event.a", "sub1", e -> c1.incrementAndGet());
        eventBus.subscribe("event.b", "sub2", e -> c2.incrementAndGet());
        
        eventBus.emit(new RuntimeEvent("event.a", Map.of()));
        
        assertThat(c1.get()).isEqualTo(1);
        assertThat(c2.get()).isZero();
    }
    
    @Test
    void shouldUnsubscribe() {
        AtomicInteger counter = new AtomicInteger();
        eventBus.subscribe("test.event", "sub1", e -> counter.incrementAndGet());
        
        eventBus.unsubscribeAll("sub1");
        eventBus.emit(new RuntimeEvent("test.event", Map.of()));
        
        assertThat(counter.get()).isZero();
    }
    
    @Test
    void shouldNotBreakOnHandlerException() {
        eventBus.subscribe("test.event", "sub1", e -> { throw new RuntimeException("boom"); });
        AtomicInteger counter = new AtomicInteger();
        eventBus.subscribe("test.event", "sub2", e -> counter.incrementAndGet());
        
        eventBus.emit(new RuntimeEvent("test.event", Map.of()));
        
        assertThat(counter.get()).isEqualTo(1); // 不影响其他订阅者
    }
}
```

---

## 8. 验收清单

- [ ] 所有接口和实现类已创建
- [ ] 单元测试覆盖率 ≥ 85%（用 JaCoCo 验证）
- [ ] 所有测试通过：`mvn -B test`
- [ ] Spring 启动正常：`mvn -B spring-boot:run`
- [ ] PluginManager 能在 Spring 上下文启动时自动注册 plugin
- [ ] EventBus 与现有 WSEventPublisher 无冲突
- [ ] 后续文档（capability-registry / claude-plugin）可继续推进

---

## 9. 踩坑记录（实施时填写）

> 实现过程中遇到的非显性问题，更新在这里。

---

## 10. 接下来

- 读 [w2-capability-registry.md](w2-capability-registry.md)，实现能力注册表
- 或读 [w2-schema-migration.md](w2-schema-migration.md)，设计数据库 schema

---

**最后更新**：2026-08-14
**版本**：v0.1 Draft