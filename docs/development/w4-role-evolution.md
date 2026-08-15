# W4: Adaptive Role Evolution 实现

> 把"执行 → 观察 → 评估 → 推荐 → 调整"的闭环真正实现出来。
>
> 预计工作量：**3.5 天**

---

## 任务目标

实现三个核心组件：

1. **PerformanceTracker**：采集任务执行结果，写入 performance_records
3. **RoleDriftDetector**：检测 project 级 role 表现漂移
4. **TeamRecommender**：生成团队配置推荐

---

## DoD

- [ ] PerformanceTracker 能记录每次任务的成功 / 失败 / 性能数据
- [ ] RoleDriftDetector 能检测到 trend 变化并发出告警
- [ ] TeamRecommender 能生成基于证据的推荐
- [ ] RoutingLessonExtractor 能从历史提炼 lessons
- [ ] 单元测试覆盖率 ≥ 85%

---

## 1. PerformanceTracker

### 1.1 接口

**位置**：`com.teammind.evolution.PerformanceTracker`

```java
package com.teammind.evolution;

import com.teammind.domain.*;

import java.util.Optional;

public interface PerformanceTracker {
    
    /**
     * 记录任务步骤执行结果。
     */
    void record(TaskStep taskStep, TaskExecution taskExecution);
    
    /**
     * 获取项目级 Agent 表现档案。
     */
    Optional<PerformanceRecord> getProjectRecord(
        String projectId, String pluginId, String role
    );
    
    /**
     * 获取全局 Agent 表现档案。
     */
    Optional<PerformanceRecord> getGlobalRecord(String pluginId);
}
```

### 1.2 实现

**位置**：`com.teammind.evolution.DefaultPerformanceTracker`

```java
package com.teammind.evolution;

import com.teammind.domain.*;
import com.teammind.repository.PerformanceRecordRepository;
import com.teammind.plugin.PluginResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional
public class DefaultPerformanceTracker implements PerformanceTracker {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultPerformanceTracker.class);
    
    private final PerformanceRecordRepository repo;
    
    public DefaultPerformanceTracker(PerformanceRecordRepository repo) {
        this.repo = repo;
    }
    
    @Override
    public void record(TaskStep step, TaskExecution task) {
        // 1. 提取数据
        String pluginId = step.getPluginId();
        String role = step.getRole();
        String projectId = task.getProjectId();
        
        boolean success = step.getStatus() == TaskStep.TaskStepStatus.SUCCESS;
        long durationMs = step.getDurationMs() != null ? step.getDurationMs() : 0;
        boolean verified = step.isEvidenceVerified();
        
        // 2. 更新项目级记录
        updateRecord(projectId, pluginId, role, success, durationMs, verified,
                     PerformanceRecord.Scope.PROJECT);
        
        // 3. 更新全局记录
        updateRecord(projectId, pluginId, role, success, durationMs, verified,
                     PerformanceRecord.Scope.GLOBAL);
    }
    
    private void updateRecord(
        String projectId, String pluginId, String role,
        boolean success, long durationMs, boolean verified,
        PerformanceRecord.Scope scope
    ) {
        // 1. 查询现有记录
        PerformanceRecord record = repo
            .findByProjectIdAndPluginIdAndRoleAndScope(
                projectId, pluginId, role, scope
            )
            .orElseGet(() -> createNew(projectId, pluginId, role, scope));
        
        // 2. 更新统计
        int newSampleSize = record.getSampleSize() + 1;
        double newSuccessRate = rollingAverage(
            record.getSuccessRate(), record.getSampleSize(), success ? 1.0 : 0.0
        );
        long newAvgDuration = rollingAverage(
            record.getAvgDurationMs(), record.getSampleSize(), durationMs
        );
        
        record.setSuccessRate(newSuccessRate);
        record.setAvgDurationMs(newAvgDuration);
        record.setSampleSize(newSampleSize);
        record.setLastUpdated(Instant.now());
        
        // 如果这是 review 类任务，更新 false positive / miss rate
        if ("REVIEWER".equals(role) || "SECURITY_GATE".equals(role)) {
            // 简化：verified = true 表示高质量 review
            double prev = record.getFalsePositiveRate() != null ? record.getFalsePositiveRate() : 0.5;
            record.setFalsePositiveRate(rollingAverage(prev, record.getSampleSize(), verified ? 0.0 : 1.0));
        }
        
        // 3. 保存
        repo.save(record);
        
        log.debug("Performance recorded: project={}, plugin={}, role={}, success={}, sampleSize={}",
            projectId, pluginId, role, success, newSampleSize);
    }
    
    private PerformanceRecord createNew(
        String projectId, String pluginId, String role, PerformanceRecord.Scope scope
    ) {
        PerformanceRecord record = new PerformanceRecord();
        record.setProjectId(projectId);
        record.setPluginId(pluginId);
        record.setRole(role);
        record.setSuccessRate(0.5);  // 中性起始值
        record.setAvgIterations(0);
        record.setAvgDurationMs(0);
        record.setSampleSize(0);
        record.setScope(scope);
        return record;
    }
    
    private double rollingAverage(double currentAvg, int currentCount, double newValue) {
        return (currentAvg * currentCount + newValue) / (currentCount + 1);
    }
    
    private long rollingAverage(long currentAvg, int currentCount, long newValue) {
        return (currentAvg * currentCount + newValue) / (currentCount + 1);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<PerformanceRecord> getProjectRecord(
        String projectId, String pluginId, String role
    ) {
        return repo.findByProjectIdAndPluginIdAndRoleAndScope(
            projectId, pluginId, role, PerformanceRecord.Scope.PROJECT
        );
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<PerformanceRecord> getGlobalRecord(String pluginId) {
        return repo.findGlobalByPluginId(pluginId);
    }
}
```

---

## 2. RoutingLessonExtractor

### 2.1 接口

**位置**：`com.teammind.evolution.RoutingLessonExtractor`

```java
package com.teammind.evolution;

import com.teammind.domain.TaskExecution;

public interface RoutingLessonExtractor {
    
    /**
     * 从已完成任务中提炼 routing lesson。
     */
    void extract(TaskExecution task);
}
```

### 2.2 实现

```java
package com.teammind.evolution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.domain.*;
import com.teammind.repository.RoutingLessonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class DefaultRoutingLessonExtractor implements RoutingLessonExtractor {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultRoutingLessonExtractor.class);
    
    private final RoutingLessonRepository repo;
    private final ObjectMapper json = new ObjectMapper();
    
    public DefaultRoutingLessonExtractor(RoutingLessonRepository repo) {
        this.repo = repo;
    }
    
    @Override
    public void extract(TaskExecution task) {
        if (task.getStatus() != TaskExecution.TaskStatus.COMPLETED) {
            return;
        }
        
        // 1. 提取模式
        List<Pattern> patterns = extractPatterns(task.getObjective());
        
        // 2. 提取成功的团队配置
        String recommendedTeamJson = serializeTeamFromTask(task);
        
        // 3. 为每个模式更新 lesson
        for (Pattern pattern : patterns) {
            String lessonKey = pattern.key();
            updateLesson(task.getProjectId(), lessonKey, pattern.description(), 
                        recommendedTeamJson, task);
        }
    }
    
    private List<Pattern> extractPatterns(String objective) {
        List<Pattern> patterns = new ArrayList<>();
        String lower = objective.toLowerCase();
        
        if (matches(lower, "auth", "权限", "登录", "认证", "oauth", "jwt")) {
            patterns.add(new Pattern("auth-change", "Task involves auth changes"));
        }
        if (matches(lower, "测试", "test", "e2e", "unit")) {
            patterns.add(new Pattern("test-generation", "Task involves test generation"));
        }
        if (matches(lower, "重构", "refactor", "restructure")) {
            patterns.add(new Pattern("refactor", "Task involves refactoring"));
        }
        if (matches(lower, "安全", "security", "漏洞", "vulnerab")) {
            patterns.add(new Pattern("security-review", "Task involves security review"));
        }
        if (matches(lower, "api", "接口", "endpoint", "rest")) {
            patterns.add(new Pattern("api-design", "Task involves API design"));
        }
        if (matches(lower, "文档", "doc", "readme")) {
            patterns.add(new Pattern("documentation", "Task involves documentation"));
        }
        
        return patterns;
    }
    
    private boolean matches(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }
    
    private void updateLesson(
        String projectId, String lessonKey, String conditionDesc,
        String recommendedTeamJson, TaskExecution task
    ) {
        Optional<RoutingLesson> existing = repo.findByProjectIdAndLessonKey(projectId, lessonKey);
        
        boolean taskSuccess = task.getOverallScore() != null && task.getOverallScore() >= 0.7;
        
        if (existing.isPresent()) {
            RoutingLesson lesson = existing.get();
            int newCount = lesson.getEvidenceCount() + 1;
            
            // Bayesian 更新 confidence
            double newConfidence = bayesianUpdate(
                lesson.getConfidence(),
                taskSuccess,
                newCount
            );
            
            lesson.setEvidenceCount(newCount);
            lesson.setConfidence(newConfidence);
            lesson.setLastValidatedAt(Instant.now());
            
            // 如果推荐变了，更新
            if (!lesson.getRecommendedTeamJson().equals(recommendedTeamJson) && newCount >= 5) {
                lesson.setRecommendedTeamJson(recommendedTeamJson);
            }
            
            repo.save(lesson);
            
            if (lesson.getEvidenceCount() == 5 || lesson.getEvidenceCount() == 20) {
                log.info("Routing lesson matured: {} (count={}, confidence={})",
                    lessonKey, lesson.getEvidenceCount(), newConfidence);
            }
        } else {
            // 新 lesson
            RoutingLesson lesson = new RoutingLesson();
            lesson.setProjectId(projectId);
            lesson.setLessonKey(lessonKey);
            lesson.setConditionDesc(conditionDesc);
            lesson.setRecommendedTeamJson(recommendedTeamJson);
            lesson.setEvidenceCount(1);
            lesson.setConfidence(taskSuccess ? 0.6 : 0.4);
            lesson.setLearnedAt(Instant.now());
            repo.save(lesson);
            
            log.info("New routing lesson: {} for project {}", lessonKey, projectId);
        }
    }
    
    private double bayesianUpdate(double prior, boolean success, int totalSamples) {
        // Beta-Binomial 简化版
        double alpha = prior * 10;
        double beta = (1 - prior) * 10;
        
        double newAlpha = alpha + (success ? 1 : 0);
        double newBeta = beta + (success ? 0 : 1);
        
        return newAlpha / (newAlpha + newBeta);
    }
    
    private String serializeTeamFromTask(TaskExecution task) {
        // 简化实现：从 task 中提取 team 配置
        // 实际实现需要查询 team_configs 表
        Map<String, Object> team = new HashMap<>();
        team.put("lead", task.getLeadPluginId());
        team.put("taskId", task.getId());
        // ... 更详细信息
        
        try {
            return json.writeValueAsString(team);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
    
    private record Pattern(String key, String description) {}
}
```

---

## 3. RoleDriftDetector

### 3.1 接口

**位置**：`com.teammind.evolution.RoleDriftDetector`

```java
package com.teammind.evolution;

import com.teammind.domain.DriftAlert;

import java.util.List;

public interface RoleDriftDetector {
    
    /**
     * 检测项目的 role drift。
     */
    List<DriftAlert> detect(String projectId);
}
```

### 3.2 实现

```java
package com.teammind.evolution;

import com.teammind.domain.*;
import com.teammind.repository.DriftAlertRepository;
import com.teammind.repository.PerformanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class DefaultRoleDriftDetector implements RoleDriftDetector {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultRoleDriftDetector.class);
    
    private static final double DRIFT_THRESHOLD = 0.10;
    private static final int SHORT_WINDOW_DAYS = 30;
    private static final int LONG_WINDOW_DAYS = 90;
    private static final int MIN_SAMPLE_SIZE = 10;
    
    private final PerformanceRecordRepository perfRepo;
    private final DriftAlertRepository alertRepo;
    
    public DefaultRoleDriftDetector(
        PerformanceRecordRepository perfRepo,
        DriftAlertRepository alertRepo
    ) {
        this.perfRepo = perfRepo;
        this.alertRepo = alertRepo;
    }
    
    @Override
    @Transactional
    public List<DriftAlert> detect(String projectId) {
        List<DriftAlert> newAlerts = new ArrayList<>();
        
        // 1. 获取所有 (role, plugin) 组合
        List<PerformanceRecord> projectRecords = perfRepo.findAll().stream()
            .filter(r -> r.getProjectId().equals(projectId))
            .filter(r -> r.getScope() == PerformanceRecord.Scope.PROJECT)
            .toList();
        
        // 2. 对每个组合检测 drift
        for (PerformanceRecord rec : projectRecords) {
            DriftAlert alert = checkDrift(projectId, rec);
            if (alert != null) {
                alertRepo.save(alert);
                newAlerts.add(alert);
            }
        }
        
        if (!newAlerts.isEmpty()) {
            log.info("Detected {} drift alerts for project {}", newAlerts.size(), projectId);
        }
        
        return newAlerts;
    }
    
    private DriftAlert checkDrift(String projectId, PerformanceRecord current) {
        // 简化实现：用 record 的当前 successRate 作为短期，
        // 与 sample size 推断的 baseline 对比
        
        if (current.getSampleSize() < MIN_SAMPLE_SIZE) {
            return null;
        }
        
        // 实际实现需要分时间段查询，简化处理
        // 短期 vs 长期对比
        double currentRate = current.getSuccessRate();
        double baseline = 0.5; // 简化：从长期数据查询
        
        double change = currentRate - baseline;
        
        if (Math.abs(change) < DRIFT_THRESHOLD) {
            return null;
        }
        
        DriftAlert alert = new DriftAlert();
        alert.setProjectId(projectId);
        alert.setPluginId(current.getPluginId());
        alert.setRole(current.getRole());
        alert.setMetric("success_rate");
        alert.setTrend(change > 0 ? DriftAlert.Trend.IMPROVING : DriftAlert.Trend.DECLINING);
        alert.setChangeAmount(change);
        alert.setWindowDays(SHORT_WINDOW_DAYS);
        alert.setDetectedAt(Instant.now());
        alert.setRecommendation(generateRecommendation(current, change));
        
        return alert;
    }
    
    private String generateRecommendation(PerformanceRecord rec, double change) {
        if (change < 0) {
            return String.format(
                "Consider swapping %s in %s role. Recent decline of %.0f%%.",
                rec.getPluginId(), rec.getRole(), Math.abs(change) * 100
            );
        } else {
            return String.format(
                "%s showing strong improvement in %s role (+%.0f%%). Consider expanding its role.",
                rec.getPluginId(), rec.getRole(), change * 100
            );
        }
    }
}
```

---

## 4. TeamRecommender

### 4.1 接口

**位置**：`com.teammind.evolution.TeamRecommender`

```java
package com.teammind.evolution;

import com.teammind.domain.Recommendation;

import java.util.Optional;

public interface TeamRecommender {
    
    /**
     * 为项目生成团队推荐。
     */
    Optional<Recommendation> generate(String projectId);
}
```

### 4.2 Recommendation 类型

```java
package com.teammind.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record Recommendation(
    String projectId,
    Instant generatedAt,
    SampleBase basedOn,
    List<RecommendationIssue> issues,
    Map<String, String> currentTeam,
    Map<String, String> recommendedTeam,
    List<String> actions
) {
    
    public record SampleBase(int taskCount, int periodDays, int projectAgeDays) {}
    
    public record RecommendationIssue(
        String role,
        String currentPlugin,
        double currentScore,
        int currentSample,
        String suggestedPlugin,
        double suggestedScore,
        String reasoning
    ) {}
}
```

### 4.3 实现

```java
package com.teammind.evolution;

import com.teammind.domain.*;
import com.teammind.repository.PerformanceRecordRepository;
import com.teammind.repository.TaskExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class DefaultTeamRecommender implements TeamRecommender {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultTeamRecommender.class);
    
    private static final int MIN_TASK_COUNT = 30;
    private static final int COOLDOWN_DAYS = 7;
    private static final double PERFORMANCE_THRESHOLD = 0.7;
    
    private final TaskExecutionRepository taskRepo;
    private final PerformanceRecordRepository perfRepo;
    
    public DefaultTeamRecommender(
        TaskExecutionRepository taskRepo,
        PerformanceRecordRepository perfRepo
    ) {
        this.taskRepo = taskRepo;
        this.perfRepo = perfRepo;
    }
    
    @Override
    public Optional<Recommendation> generate(String projectId) {
        // 1. 获取项目所有任务
        List<TaskExecution> tasks = taskRepo.findByProjectIdOrderByStartedAtDesc(projectId);
        
        if (tasks.size() < MIN_TASK_COUNT) {
            log.debug("Project {} has only {} tasks, not enough for recommendation",
                projectId, tasks.size());
            return Optional.empty();
        }
        
        // 2. 获取当前团队配置
        // (简化：从最近的 task 推断)
        Map<String, String> currentTeam = extractCurrentTeam(tasks.get(0));
        Map<String, String> recommendedTeam = new HashMap<>(currentTeam);
        
        // 3. 检测问题
        List<Recommendation.RecommendationIssue> issues = new ArrayList<>();
        
        for (var entry : currentTeam.entrySet()) {
            String role = entry.getKey();
            String currentPlugin = entry.getValue();
            
            PerformanceRecord currentRec = perfRepo
                .findByProjectIdAndPluginIdAndRoleAndScope(
                    projectId, currentPlugin, role, PerformanceRecord.Scope.PROJECT
                )
                .orElse(null);
            
            if (currentRec == null || currentRec.getSampleSize() < 10) continue;
            
            if (currentRec.getSuccessRate() < PERFORMANCE_THRESHOLD) {
                // 找更好的 plugin
                Optional<Recommendation.RecommendationIssue> better = findBetterPlugin(
                    projectId, role, currentPlugin, currentRec
                );
                
                if (better.isPresent()) {
                    issues.add(better.get());
                    recommendedTeam.put(role, better.get().suggestedPlugin());
                }
            }
        }
        
        if (issues.isEmpty()) {
            return Optional.empty();
        }
        
        Recommendation rec = new Recommendation(
            projectId,
            Instant.now(),
            new Recommendation.SampleBase(
                tasks.size(), 30, 0  // projectAgeDays 需要单独计算
            ),
            issues,
            currentTeam,
            recommendedTeam,
            List.of("APPLY", "IGNORE", "DETAILS")
        );
        
        log.info("Generated recommendation for {}: {} issues", projectId, issues.size());
        return Optional.of(rec);
    }
    
    private Optional<Recommendation.RecommendationIssue> findBetterPlugin(
        String projectId, String role, String currentPlugin,
        PerformanceRecord currentRec
    ) {
        List<PerformanceRecord> allInRole = perfRepo.findByProjectIdAndRole(projectId, role);
        
        PerformanceRecord best = null;
        for (PerformanceRecord rec : allInRole) {
            if (rec.getPluginId().equals(currentPlugin)) continue;
            if (rec.getSampleSize() < 10) continue;
            
            if (best == null || rec.getSuccessRate() > best.getSuccessRate()) {
                best = rec;
            }
        }
        
        if (best == null || best.getSuccessRate() <= currentRec.getSuccessRate()) {
            return Optional.empty();
        }
        
        String reasoning = String.format(
            "%s in %s role: success rate %.0f%% (%d samples). %s: %.0f%% (%d samples).",
            currentPlugin, role,
            currentRec.getSuccessRate() * 100, currentRec.getSampleSize(),
            best.getPluginId(), best.getSuccessRate() * 100, best.getSampleSize()
        );
        
        return Optional.of(new Recommendation.RecommendationIssue(
            role, currentPlugin,
            currentRec.getSuccessRate(), currentRec.getSampleSize(),
            best.getPluginId(), best.getSuccessRate(),
            reasoning
        ));
    }
    
    private Map<String, String> extractCurrentTeam(TaskExecution recentTask) {
        Map<String, String> team = new HashMap<>();
        if (recentTask.getLeadPluginId() != null) {
            team.put("LEAD", recentTask.getLeadPluginId());
        }
        // 实际应从 team_configs 表查
        return team;
    }
}
```

---

## 5. EventBus 集成

注册到 EventBus，让推荐自动生成：

**位置**：`com.teammind.evolution.EvolutionEventHandlers`

```java
package com.teammind.evolution;

import com.teammind.plugin.EventBus;
import com.teammind.plugin.RuntimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EvolutionEventHandlers {
    
    private static final Logger log = LoggerFactory.getLogger(EvolutionEventHandlers.class);
    
    private final EventBus eventBus;
    private final PerformanceTracker performanceTracker;
    private final RoutingLessonExtractor lessonExtractor;
    private final RoleDriftDetector driftDetector;
    private final TeamRecommender recommender;
    
    public EvolutionEventHandlers(
        EventBus eventBus,
        PerformanceTracker performanceTracker,
        RoutingLessonExtractor lessonExtractor,
        RoleDriftDetector driftDetector,
        TeamRecommender recommender
    ) {
        this.eventBus = eventBus;
        this.performanceTracker = performanceTracker;
        this.lessonExtractor = lessonExtractor;
        this.driftDetector = driftDetector;
        this.recommender = recommender;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        // 任务完成时记录表现
        eventBus.subscribe("task.completed", "perf-tracker", event -> {
            handleTaskCompleted(event);
        });
        
        // 任务完成时提取 lesson
        eventBus.subscribe("task.completed", "lesson-extractor", event -> {
            handleTaskCompleted(event);
        });
    }
    
    private void handleTaskCompleted(RuntimeEvent event) {
        Object stepObj = event.payload().get("step");
        Object taskObj = event.payload().get("task");
        
        if (!(stepObj instanceof com.teammind.domain.TaskStep step)) return;
        if (!(taskObj instanceof com.teammind.domain.TaskExecution task)) return;
        
        try {
            performanceTracker.record(step, task);
            lessonExtractor.extract(task);
            
            // 每 10 次任务检测一次 drift
            if (task.getTotalTasks() != null && task.getTotalTasks() % 10 == 0) {
                driftDetector.detect(task.getProjectId());
            }
            
            // 每 30 次任务生成一次推荐
            if (task.getTotalTasks() != null && task.getTotalTasks() % 30 == 0) {
                recommender.generate(task.getProjectId()).ifPresent(rec ->
                    log.info("Recommendation generated for {}: {}", task.getProjectId(), rec)
                );
            }
        } catch (Exception e) {
            log.error("Evolution handler failed", e);
        }
    }
}
```

---

## 6. 单元测试

### 6.1 PerformanceTrackerTest

```java
@DataJpaTest
class PerformanceTrackerTest {
    
    @Autowired PerformanceRecordRepository repo;
    
    PerformanceTracker tracker;
    
    @BeforeEach
    void setUp() {
        tracker = new DefaultPerformanceTracker(repo);
    }
    
    @Test
    void shouldRecordFirstSample() {
        TaskExecution task = createTask("p1");
        TaskStep step = createStep("claude-code", "LEAD", TaskStep.TaskStepStatus.SUCCESS);
        
        tracker.record(step, task);
        
        PerformanceRecord rec = tracker.getProjectRecord("p1", "claude-code", "LEAD")
            .orElseThrow();
        assertThat(rec.getSuccessRate()).isEqualTo(1.0);
        assertThat(rec.getSampleSize()).isEqualTo(1);
    }
    
    @Test
    void shouldRollingAverage() {
        TaskExecution task = createTask("p1");
        
        // 5 successes, 5 failures → 0.5
        for (int i = 0; i < 10; i++) {
            TaskStep step = createStep("claude-code", "LEAD",
                i < 5 ? TaskStep.TaskStepStatus.SUCCESS : TaskStep.TaskStepStatus.FAILURE);
            tracker.record(step, task);
        }
        
        PerformanceRecord rec = tracker.getProjectRecord("p1", "claude-code", "LEAD")
            .orElseThrow();
        assertThat(rec.getSuccessRate()).isCloseTo(0.5, within(0.01));
        assertThat(rec.getSampleSize()).isEqualTo(10);
    }
}
```

### 6.2 RoutingLessonExtractorTest

```java
class RoutingLessonExtractorTest {
    
    @Mock RoutingLessonRepository repo;
    RoutingLessonExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new DefaultRoutingLessonExtractor(repo);
    }
    
    @Test
    void shouldExtractAuthPattern() {
        TaskExecution task = createTask("p1");
        task.setObjective("修改 OAuth 权限");
        task.setStatus(TaskExecution.TaskStatus.COMPLETED);
        task.setOverallScore(0.9);
        
        extractor.extract(task);
        
        verify(repo).save(argThat(lesson ->
            "auth-change".equals(lesson.getLessonKey())
        ));
    }
    
    @Test
    void shouldSkipIncompleteTasks() {
        TaskExecution task = createTask("p1");
        task.setStatus(TaskExecution.TaskStatus.FAILED);
        
        extractor.extract(task);
        
        verify(repo, never()).save(any());
    }
}
```

---

## 7. 验收清单

- [ ] PerformanceTracker 实现完整
- [ ] RoutingLessonExtractor 实现完整
- [ ] RoleDriftDetector 实现完整
- [ ] TeamRecommender 实现完整
- [ ] EventBus 集成
- [ ] 单测覆盖率 ≥ 85%
- [ ] 所有测试通过

---

## 8. 接下来

- 读 [testing-guide.md](testing-guide.md)，学习测试策略
- 或开始 W5：发布

---

**最后更新**：2026-08-14
**版本**：v0.1 Draft