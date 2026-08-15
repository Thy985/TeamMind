# W2.4: SQLite Schema Migration（v1 → v2）

> 升级数据库 schema 以支持 Project / TeamConfig / TaskExecution / Performance / RoutingLesson。
>
> 预计工作量：**1 天**

---

## 任务目标

把数据库从单任务模式升级到"项目级 AI 团队 Runtime"模式：

| 旧表（v1） | 处理 | 新表（v2） |
|---|---|---|
| `users` | 砍掉（单用户本地工具） | - |
| `missions` | 重命名为 `task_executions` | `task_executions` |
| `tasks` | 拆分到 `task_steps` | `task_steps` |
| `templates` | 砍掉 | - |
| `workspaces` | 整合到 `projects` | `projects` |
| `evolution_*` | 重写为 `performance_records` / `drift_alerts` / `routing_lessons` | （见下） |
| - | 新增 | `team_configs` |
| - | 新增 | `team_roles` |
| - | 新增 | `shared_states` |
| - | 新增 | `artifacts` |
| - | 新增 | `evidence` |
| - | 新增 | `decisions` |

---

## DoD

- [ ] Flyway migration V2 脚本就绪
- [ ] 所有新表创建成功
- [ ] JPA entity 映射正确
- [ ] 现有数据（如果有）平滑迁移
- [ ] 单元测试覆盖 schema 校验

---

## 1. 现有 v1 Schema（先看）

读取现有 schema：

```sql
sqlite> .schema
```

预期现有表：

```
users                  -- 砍
workspaces             -- 改 projects
missions               -- 改 task_executions
tasks                  -- 拆分到 task_steps
templates              -- 砍
evolution_runs         -- 砍，重写
evolution_artifacts    -- 砍
evolution_metrics      -- 重写为 performance_records
evolution_recommendations -- 改 routing_lessons
```

> 实际表名以 `db/migration/V1__init.sql` 或现有 schema 为准。

---

## 2. 新 V2 Schema

### 2.1 完整 DDL

**位置**：`backend/src/main/resources/db/migration/V2__add_project_runtime.sql`

```sql
-- ============================================
-- TeamMind V2: Project AI Team Runtime Schema
-- ============================================

-- ============================================
-- 1. projects（一等公民）
-- ============================================
CREATE TABLE projects (
    id              TEXT PRIMARY KEY,            -- UUID
    name            TEXT NOT NULL,
    description     TEXT,
    root_path       TEXT NOT NULL,               -- 工作目录
    team_profile    TEXT,                        -- JSON: TeamProfile 命名（High Assurance 等）
    
    -- 时间戳
    created_at      TEXT NOT NULL,               -- ISO 8601
    updated_at      TEXT NOT NULL,
    last_run_at     TEXT,
    
    -- 统计
    total_tasks     INTEGER NOT NULL DEFAULT 0,
    project_age_days INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_projects_updated_at ON projects(updated_at DESC);
CREATE INDEX idx_projects_name ON projects(name);

-- ============================================
-- 2. team_configs（团队配置）
-- ============================================
CREATE TABLE team_configs (
    id              TEXT PRIMARY KEY,
    project_id      TEXT NOT NULL,
    profile_name    TEXT,                        -- 'High Assurance' 等
    description     TEXT,
    
    -- 序列化字段（如果 team 设计简单）
    -- 实际可用 team_roles 表，复杂配置存这里
    serialized      TEXT,                        -- JSON 完整配置
    
    is_active       BOOLEAN NOT NULL DEFAULT 1,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_team_configs_project_id ON team_configs(project_id);
CREATE INDEX idx_team_configs_active ON team_configs(project_id, is_active);

-- ============================================
-- 3. team_roles（角色定义）
-- ============================================
CREATE TABLE team_roles (
    id              TEXT PRIMARY KEY,
    team_config_id  TEXT NOT NULL,
    
    role_id         TEXT NOT NULL,               -- "LEAD" / "REVIEWER" / "SECURITY_GATE"
    philosophy_pref TEXT,                        -- JSON: 哲学偏好数组
    assigned_plugin_id TEXT,                     -- 当前绑定的 Plugin（可空，Runtime 自动选）
    triggers        TEXT,                        -- JSON: 触发条件
    
    sort_order      INTEGER NOT NULL DEFAULT 0,
    
    FOREIGN KEY (team_config_id) REFERENCES team_configs(id) ON DELETE CASCADE
);

CREATE INDEX idx_team_roles_team_config ON team_roles(team_config_id);

-- ============================================
-- 4. shared_states（项目级共享状态）
-- ============================================
CREATE TABLE shared_states (
    project_id      TEXT PRIMARY KEY,
    
    -- 项目上下文（JSON）
    context_json    TEXT NOT NULL DEFAULT '{}',  -- architecture, adrs, codingRules...
    
    -- 当前任务（运行中）
    current_task_id TEXT,
    
    -- 统计
    decisions_count INTEGER NOT NULL DEFAULT 0,
    lessons_count   INTEGER NOT NULL DEFAULT 0,
    
    updated_at      TEXT NOT NULL,
    
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- ============================================
-- 5. task_executions（任务执行历史）
-- ============================================
CREATE TABLE task_executions (
    id              TEXT PRIMARY KEY,
    project_id      TEXT NOT NULL,
    
    objective       TEXT NOT NULL,
    constraints     TEXT,                        -- JSON array
    
    started_at      TEXT NOT NULL,
    completed_at    TEXT,
    total_duration_ms INTEGER,
    
    status          TEXT NOT NULL DEFAULT 'IN_PROGRESS',
                    -- IN_PROGRESS | COMPLETED | FAILED | NEEDS_HUMAN_DECISION | CANCELLED
    
    lead_plugin_id  TEXT,                        -- 主负责 Agent
    
    -- 最终验证结果
    verification    TEXT,                        -- JSON: VerificationResult
    overall_score   REAL,                        -- 0-1 综合评分
    
    -- 元数据
    user_feedback   TEXT,                        -- 用户反馈文本
    user_rating     INTEGER,                     -- 1-5
    
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_task_executions_project ON task_executions(project_id, started_at DESC);
CREATE INDEX idx_task_executions_status ON task_executions(status);

-- ============================================
-- 6. task_steps（每个 Agent 的执行步骤）
-- ============================================
CREATE TABLE task_steps (
    id              TEXT PRIMARY KEY,
    task_execution_id TEXT NOT NULL,
    step_index      INTEGER NOT NULL,            -- 步骤序号
    
    plugin_id       TEXT NOT NULL,
    role            TEXT NOT NULL,               -- LEAD | REVIEWER | TESTER | ...
    
    started_at      TEXT NOT NULL,
    completed_at    TEXT,
    duration_ms     INTEGER,
    
    status          TEXT NOT NULL,               -- SUCCESS | FAILURE | PARTIAL | NEEDS_REVIEW
    
    -- 输入
    input_json      TEXT,                        -- 输入上下文（JSON）
    
    -- 输出
    output_summary  TEXT,                        -- 一句话总结
    output_json     TEXT,                        -- 完整 PluginResult（JSON）
    
    -- 验证
    evidence_verified BOOLEAN NOT NULL DEFAULT 0,
    verification_notes TEXT,
    
    FOREIGN KEY (task_execution_id) REFERENCES task_executions(id) ON DELETE CASCADE
);

CREATE INDEX idx_task_steps_execution ON task_steps(task_execution_id, step_index);
CREATE INDEX idx_task_steps_plugin_role ON task_steps(plugin_id, role);

-- ============================================
-- 7. artifacts（结构化产物）
-- ============================================
CREATE TABLE artifacts (
    id              TEXT PRIMARY KEY,
    task_step_id    TEXT,
    task_execution_id TEXT,
    
    type            TEXT NOT NULL,               -- CODE_DIFF | TEST_REPORT | REVIEW_FINDINGS | RESEARCH
    
    -- 序列化（按 type 不同结构不同）
    payload         TEXT NOT NULL,               -- JSON
    
    created_at      TEXT NOT NULL,
    
    FOREIGN KEY (task_step_id) REFERENCES task_steps(id) ON DELETE SET NULL,
    FOREIGN KEY (task_execution_id) REFERENCES task_executions(id) ON DELETE CASCADE
);

CREATE INDEX idx_artifacts_type ON artifacts(type);
CREATE INDEX idx_artifacts_task_step ON artifacts(task_step_id);
CREATE INDEX idx_artifacts_task_execution ON artifacts(task_execution_id);

-- ============================================
-- 8. evidence（可验证证据）
-- ============================================
CREATE TABLE evidence (
    id              TEXT PRIMARY KEY,
    task_step_id    TEXT,
    
    type            TEXT NOT NULL,               -- GIT_DIFF | TEST_EXECUTION | FILE_EXISTENCE | COMMAND_EXIT
    
    payload         TEXT NOT NULL,               -- JSON
    
    verified        BOOLEAN NOT NULL DEFAULT 0,
    verified_at     TEXT,
    verification_method TEXT,
    
    created_at      TEXT NOT NULL,
    
    FOREIGN KEY (task_step_id) REFERENCES task_steps(id) ON DELETE CASCADE
);

CREATE INDEX idx_evidence_task_step ON evidence(task_step_id);
CREATE INDEX idx_evidence_verified ON evidence(verified);

-- ============================================
-- 9. decisions（架构决策记录）
-- ============================================
CREATE TABLE decisions (
    id              TEXT PRIMARY KEY,
    project_id      TEXT NOT NULL,
    task_execution_id TEXT,
    
    decision        TEXT NOT NULL,
    rationale       TEXT,
    alternatives    TEXT,                        -- JSON array
    
    decided_by      TEXT,                        -- plugin_id or "HUMAN"
    
    created_at      TEXT NOT NULL,
    
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (task_execution_id) REFERENCES task_executions(id) ON DELETE SET NULL
);

CREATE INDEX idx_decisions_project ON decisions(project_id, created_at DESC);

-- ============================================
-- 10. performance_records（项目级 Agent 表现）
-- ============================================
CREATE TABLE performance_records (
    id              TEXT PRIMARY KEY,
    project_id      TEXT NOT NULL,
    plugin_id       TEXT NOT NULL,
    role            TEXT NOT NULL,
    
    -- 通用
    success_rate    REAL NOT NULL DEFAULT 0.5,
    avg_iterations  REAL NOT NULL DEFAULT 0,
    avg_duration_ms INTEGER NOT NULL DEFAULT 0,
    sample_size     INTEGER NOT NULL DEFAULT 0,
    last_updated    TEXT NOT NULL,
    
    -- 角色特定
    false_positive_rate REAL,                    -- review 类任务
    miss_rate           REAL,                    -- review 类任务
    user_acceptance_rate REAL,
    
    -- 范围（项目级 vs 全局）
    scope           TEXT NOT NULL,               -- PROJECT | GLOBAL
    
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_perf_unique 
    ON performance_records(project_id, plugin_id, role, scope);
CREATE INDEX idx_perf_project ON performance_records(project_id, role);

-- ============================================
-- 11. drift_alerts（角色漂移告警）
-- ============================================
CREATE TABLE drift_alerts (
    id              TEXT PRIMARY KEY,
    project_id      TEXT NOT NULL,
    plugin_id       TEXT NOT NULL,
    role            TEXT NOT NULL,
    
    metric          TEXT NOT NULL,               -- success_rate | avg_iterations | ...
    trend           TEXT NOT NULL,               -- IMPROVING | DECLINING
    change_amount   REAL NOT NULL,               -- 变化幅度
    window_days     INTEGER NOT NULL DEFAULT 30,
    
    detected_at     TEXT NOT NULL,
    acknowledged_at TEXT,
    recommendation  TEXT,
    
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_drift_project ON drift_alerts(project_id, detected_at DESC);
CREATE INDEX idx_drift_acknowledged ON drift_alerts(acknowledged_at);

-- ============================================
-- 12. routing_lessons（自动提炼的路由经验）
-- ============================================
CREATE TABLE routing_lessons (
    id              TEXT PRIMARY KEY,
    project_id      TEXT NOT NULL,
    
    lesson_key      TEXT NOT NULL,               -- 'auth-change' 等
    condition_desc  TEXT NOT NULL,               -- 自然语言描述
    
    recommended_team_json TEXT NOT NULL,         -- JSON: 推荐 team 配置
    
    evidence_count  INTEGER NOT NULL DEFAULT 1,
    confidence      REAL NOT NULL DEFAULT 0.5,
    
    learned_at      TEXT NOT NULL,
    last_validated_at TEXT,
    
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_lessons_project_key 
    ON routing_lessons(project_id, lesson_key);

-- ============================================
-- 13. plugin_health（Plugin 健康状态历史）
-- ============================================
CREATE TABLE plugin_health (
    id              TEXT PRIMARY KEY,
    plugin_id       TEXT NOT NULL,
    
    status          TEXT NOT NULL,               -- HEALTHY | DEGRADED | UNHEALTHY
    message         TEXT,
    
    checked_at      TEXT NOT NULL
);

CREATE INDEX idx_plugin_health_plugin ON plugin_health(plugin_id, checked_at DESC);

-- ============================================
-- 数据迁移（从 v1）
-- ============================================

-- 1. workspaces → projects
INSERT INTO projects (id, name, root_path, created_at, updated_at, last_run_at)
SELECT id, name, path, created_at, updated_at, last_run_at
FROM workspaces;

-- 2. missions → task_executions
INSERT INTO task_executions (id, project_id, objective, started_at, completed_at, status)
SELECT id, workspace_id, description, created_at, completed_at, status
FROM missions;

-- 3. tasks → task_steps（部分映射）
INSERT INTO task_steps (id, task_execution_id, step_index, plugin_id, role, started_at, completed_at, status, output_summary)
SELECT id, mission_id, sequence, agent_type, role, created_at, completed_at, status, output_text
FROM tasks;

-- 4. evolution_metrics → performance_records
INSERT INTO performance_records (id, project_id, plugin_id, role, success_rate, avg_iterations, sample_size, last_updated, scope)
SELECT id, workspace_id, agent_id, role, success_rate, avg_retries, sample_size, updated_at, 'PROJECT'
FROM evolution_metrics;

-- 5. evolution_recommendations → routing_lessons
INSERT INTO routing_lessons (id, project_id, lesson_key, condition_desc, recommended_team_json, evidence_count, confidence, learned_at)
SELECT id, workspace_id, type, description, recommended_config, supporting_evidence_count, confidence, created_at
FROM evolution_recommendations;

-- 6. 删除旧表
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS templates;
DROP TABLE IF EXISTS evolution_runs;
DROP TABLE IF EXISTS evolution_artifacts;
DROP TABLE IF EXISTS evolution_metrics;
DROP TABLE IF EXISTS evolution_recommendations;
DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS missions;
DROP TABLE IF EXISTS workspaces;

-- ============================================
-- 视图：项目摘要（便于查询）
-- ============================================
CREATE VIEW v_project_summary AS
SELECT 
    p.id,
    p.name,
    p.root_path,
    p.team_profile,
    p.total_tasks,
    p.project_age_days,
    COUNT(DISTINCT te.id) AS task_count,
    AVG(te.overall_score) AS avg_score,
    MAX(te.started_at) AS last_task_at,
    (SELECT COUNT(*) FROM drift_alerts da WHERE da.project_id = p.id AND da.acknowledged_at IS NULL) AS unread_alerts
FROM projects p
LEFT JOIN task_executions te ON te.project_id = p.id
GROUP BY p.id;
```

---

## 3. JPA Entities

### 3.1 Project

**位置**：`com.teammind.domain.Project`

```java
package com.teammind.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    @Column(name = "root_path", nullable = false)
    private String rootPath;
    
    @Column(name = "team_profile")
    private String teamProfile;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "last_run_at")
    private Instant lastRunAt;
    
    @Column(name = "total_tasks", nullable = false)
    private int totalTasks;
    
    @Column(name = "project_age_days", nullable = false)
    private int projectAgeDays;
    
    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    
    // getters / setters ...
}
```

### 3.2 TaskExecution

```java
@Entity
@Table(name = "task_executions")
public class TaskExecution {
    
    @Id
    private String id;
    
    @Column(name = "project_id", nullable = false)
    private String projectId;
    
    @Column(nullable = false)
    private String objective;
    
    @Column(columnDefinition = "TEXT")
    private String constraints;  // JSON
    
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "total_duration_ms")
    private Long totalDurationMs;
    
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    
    @Column(name = "lead_plugin_id")
    private String leadPluginId;
    
    @Column(columnDefinition = "TEXT")
    private String verification;  // JSON
    
    @Column(name = "overall_score")
    private Double overallScore;
    
    @Column(name = "user_feedback")
    private String userFeedback;
    
    @Column(name = "user_rating")
    private Integer userRating;
    
    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (startedAt == null) startedAt = Instant.now();
    }
    
    public enum TaskStatus {
        IN_PROGRESS, COMPLETED, FAILED, NEEDS_HUMAN_DECISION, CANCELLED
    }
    
    // getters / setters ...
}
```

### 3.3 TaskStep

```java
@Entity
@Table(name = "task_steps")
public class TaskStep {
    
    @Id
    private String id;
    
    @Column(name = "task_execution_id", nullable = false)
    private String taskExecutionId;
    
    @Column(name = "step_index", nullable = false)
    private int stepIndex;
    
    @Column(name = "plugin_id", nullable = false)
    private String pluginId;
    
    @Column(nullable = false)
    private String role;
    
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "duration_ms")
    private Long durationMs;
    
    @Enumerated(EnumType.STRING)
    private TaskStepStatus status;
    
    @Column(name = "input_json", columnDefinition = "TEXT")
    private String inputJson;
    
    @Column(name = "output_summary")
    private String outputSummary;
    
    @Column(name = "output_json", columnDefinition = "TEXT")
    private String outputJson;
    
    @Column(name = "evidence_verified", nullable = false)
    private boolean evidenceVerified;
    
    @Column(name = "verification_notes")
    private String verificationNotes;
    
    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (startedAt == null) startedAt = Instant.now();
    }
    
    public enum TaskStepStatus {
        SUCCESS, FAILURE, PARTIAL, NEEDS_REVIEW
    }
    
    // getters / setters ...
}
```

### 3.4 PerformanceRecord

```java
@Entity
@Table(name = "performance_records",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"project_id", "plugin_id", "role", "scope"}
       ))
public class PerformanceRecord {
    
    @Id
    private String id;
    
    @Column(name = "project_id", nullable = false)
    private String projectId;
    
    @Column(name = "plugin_id", nullable = false)
    private String pluginId;
    
    @Column(nullable = false)
    private String role;
    
    @Column(name = "success_rate", nullable = false)
    private double successRate;
    
    @Column(name = "avg_iterations", nullable = false)
    private double avgIterations;
    
    @Column(name = "avg_duration_ms", nullable = false)
    private long avgDurationMs;
    
    @Column(name = "sample_size", nullable = false)
    private int sampleSize;
    
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;
    
    @Column(name = "false_positive_rate")
    private Double falsePositiveRate;
    
    @Column(name = "miss_rate")
    private Double missRate;
    
    @Column(name = "user_acceptance_rate")
    private Double userAcceptanceRate;
    
    @Enumerated(EnumType.STRING)
    private Scope scope;
    
    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (lastUpdated == null) lastUpdated = Instant.now();
    }
    
    public enum Scope {
        PROJECT, GLOBAL
    }
    
    // getters / setters ...
}
```

### 3.5 RoutingLesson

```java
@Entity
@Table(name = "routing_lessons",
       uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "lesson_key"}))
public class RoutingLesson {
    
    @Id
    private String id;
    
    @Column(name = "project_id", nullable = false)
    private String projectId;
    
    @Column(name = "lesson_key", nullable = false)
    private String lessonKey;
    
    @Column(name = "condition_desc", nullable = false)
    private String conditionDesc;
    
    @Column(name = "recommended_team_json", nullable = false, columnDefinition = "TEXT")
    private String recommendedTeamJson;
    
    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;
    
    @Column(nullable = false)
    private double confidence;
    
    @Column(name = "learned_at", nullable = false)
    private Instant learnedAt;
    
    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;
    
    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (learnedAt == null) learnedAt = Instant.now();
    }
    
    // getters / setters ...
}
```

### 3.6 DriftAlert

```java
@Entity
@Table(name = "drift_alerts")
public class DriftAlert {
    
    @Id
    private String id;
    
    @Column(name = "project_id", nullable = false)
    private String projectId;
    
    @Column(name = "plugin_id", nullable = false)
    private String pluginId;
    
    @Column(nullable = false)
    private String role;
    
    @Column(nullable = false)
    private String metric;
    
    @Enumerated(EnumType.STRING)
    private Trend trend;
    
    @Column(name = "change_amount", nullable = false)
    private double changeAmount;
    
    @Column(name = "window_days", nullable = false)
    private int windowDays;
    
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
    
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
    
    private String recommendation;
    
    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (detectedAt == null) detectedAt = Instant.now();
    }
    
    public enum Trend {
        IMPROVING, DECLINING
    }
    
    // getters / setters ...
}
```

---

## 4. Repositories

### 4.1 ProjectRepository

```java
package com.teammind.repository;

import com.teammind.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {
    
    List<Project> findAllByOrderByUpdatedAtDesc();
    
    @Query("SELECT p FROM Project p WHERE p.name LIKE %?1%")
    List<Project> searchByName(String keyword);
}
```

### 4.2 TaskExecutionRepository

```java
@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, String> {
    
    List<TaskExecution> findByProjectIdOrderByStartedAtDesc(String projectId);
    
    List<TaskExecution> findByProjectIdAndStatus(String projectId, TaskExecution.TaskStatus status);
    
    long countByProjectId(String projectId);
    
    @Query("SELECT AVG(t.overallScore) FROM TaskExecution t WHERE t.projectId = ?1 AND t.completedAt IS NOT NULL")
    Double averageScoreByProject(String projectId);
}
```

### 4.3 TaskStepRepository

```java
@Repository
public interface TaskStepRepository extends JpaRepository<TaskStep, String> {
    
    List<TaskStep> findByTaskExecutionIdOrderByStepIndex(String taskExecutionId);
    
    List<TaskStep> findByPluginIdAndRole(String pluginId, String role);
    
    long countByPluginIdAndRoleAndStatus(String pluginId, String role, TaskStep.TaskStepStatus status);
}
```

### 4.4 PerformanceRecordRepository

```java
@Repository
public interface PerformanceRecordRepository extends JpaRepository<PerformanceRecord, String> {
    
    Optional<PerformanceRecord> findByProjectIdAndPluginIdAndRoleAndScope(
        String projectId, String pluginId, String role, PerformanceRecord.Scope scope
    );
    
    List<PerformanceRecord> findByProjectIdAndRole(String projectId, String role);
    
    @Query("SELECT p FROM PerformanceRecord p WHERE p.scope = 'GLOBAL' AND p.pluginId = ?1")
    Optional<PerformanceRecord> findGlobalByPluginId(String pluginId);
}
```

### 4.5 RoutingLessonRepository

```java
@Repository
public interface RoutingLessonRepository extends JpaRepository<RoutingLesson, String> {
    
    Optional<RoutingLesson> findByProjectIdAndLessonKey(String projectId, String lessonKey);
    
    List<RoutingLesson> findByProjectIdOrderByConfidenceDesc(String projectId);
}
```

### 4.6 DriftAlertRepository

```java
@Repository
public interface DriftAlertRepository extends JpaRepository<DriftAlert, String> {
    
    List<DriftAlert> findByProjectIdAndAcknowledgedAtIsNullOrderByDetectedAtDesc(String projectId);
    
    long countByProjectIdAndAcknowledgedAtIsNull(String projectId);
}
```

---

## 5. Service 层

### 5.1 ProjectService

**位置**：`com.teammind.service.ProjectService`

```java
package com.teammind.service;

import com.teammind.domain.Project;
import com.teammind.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProjectService {
    
    private final ProjectRepository projectRepository;
    
    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }
    
    public Project create(Project project) {
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        return projectRepository.save(project);
    }
    
    public Project update(String id, Project updated) {
        Project existing = projectRepository.findById(id)
            .orElseThrow(() -> new ProjectNotFoundException(id));
        
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setRootPath(updated.getRootPath());
        existing.setTeamProfile(updated.getTeamProfile());
        existing.setUpdatedAt(Instant.now());
        
        return projectRepository.save(existing);
    }
    
    public void delete(String id) {
        projectRepository.deleteById(id);
    }
    
    public Optional<Project> findById(String id) {
        return projectRepository.findById(id);
    }
    
    public List<Project> listAll() {
        return projectRepository.findAllByOrderByUpdatedAtDesc();
    }
    
    public void recordTaskExecuted(String projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ProjectNotFoundException(projectId));
        project.setTotalTasks(project.getTotalTasks() + 1);
        project.setLastRunAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
    }
}
```

### 5.2 TaskService（任务执行入口）

```java
@Service
@Transactional
public class TaskService {
    
    private final TaskExecutionRepository taskExecRepo;
    private final TaskStepRepository taskStepRepo;
    private final ProjectService projectService;
    private final CapabilityRouter router;
    private final TaskScheduler scheduler;
    private final EventBus eventBus;
    
    // 构造器注入
    
    public TaskExecution submit(String projectId, String objective, List<String> constraints) {
        // 1. 创建 TaskExecution
        TaskExecution task = new TaskExecution();
        task.setProjectId(projectId);
        task.setObjective(objective);
        task.setConstraints(toJson(constraints));
        task.setStatus(TaskExecution.TaskStatus.IN_PROGRESS);
        task = taskExecRepo.save(task);
        
        // 2. 记录项目统计
        projectService.recordTaskExecuted(projectId);
        
        // 3. 触发 Lead Agent
        scheduleLead(task);
        
        // 4. 发布事件
        eventBus.emit(new RuntimeEvent("task.submitted", Map.of(
            "taskId", task.getId(), "projectId", projectId
        )));
        
        return task;
    }
    
    private void scheduleLead(TaskExecution task) {
        AgentTask leadTask = AgentTask.of(task.getId(), "LEAD", task.getObjective());
        
        // 路由
        RoutingContext ctx = new RoutingContext(/* ... */, /* profile */);
        Optional<Plugin> lead = router.route(leadTask, ctx);
        
        if (lead.isEmpty()) {
            task.setStatus(TaskExecution.TaskStatus.FAILED);
            taskExecRepo.save(task);
            return;
        }
        
        // 调度执行
        ScheduledTask scheduled = new ScheduledTask(
            task.getId() + "-lead",
            lead.get().metadata().id(),
            new PluginContext(leadTask, ...),
            List.of(),
            ScheduledTask.RetryPolicy.defaultPolicy(),
            ScheduledTask.FailurePolicy.FAIL,
            null, 300_000
        );
        
        // 异步执行（简化）
        scheduler.run(List.of(scheduled));
    }
    
    // ... 其他方法
}
```

---

## 6. 单元测试

### 6.1 SchemaMigrationTest

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=true"
})
class SchemaMigrationTest {
    
    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    
    @Test
    void shouldApplyV2Migration() throws Exception {
        flyway.migrate();
        
        try (Connection c = dataSource.getConnection()) {
            // 验证关键表存在
            assertTableExists(c, "projects");
            assertTableExists(c, "team_configs");
            assertTableExists(c, "team_roles");
            assertTableExists(c, "shared_states");
            assertTableExists(c, "task_executions");
            assertTableExists(c, "task_steps");
            assertTableExists(c, "artifacts");
            assertTableExists(c, "evidence");
            assertTableExists(c, "decisions");
            assertTableExists(c, "performance_records");
            assertTableExists(c, "drift_alerts");
            assertTableExists(c, "routing_lessons");
            assertTableExists(c, "plugin_health");
        }
    }
    
    private void assertTableExists(Connection c, String table) throws Exception {
        try (var rs = c.getMetaData().getTables(null, null, table, null)) {
            assertThat(rs.next()).isTrue();
        }
    }
}
```

### 6.2 ProjectRepositoryTest

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.ANY)
class ProjectRepositoryTest {
    
    @Autowired ProjectRepository repo;
    
    @Test
    void shouldCreateAndFindProject() {
        Project p = new Project();
        p.setName("Test");
        p.setRootPath("/tmp/test");
        p = repo.save(p);
        
        assertThat(repo.findById(p.getId())).isPresent();
    }
    
    @Test
    void shouldListByUpdatedDesc() {
        Project older = new Project();
        older.setName("older");
        older.setRootPath("/x");
        older.setUpdatedAt(Instant.now().minusSeconds(3600));
        repo.save(older);
        
        Project newer = new Project();
        newer.setName("newer");
        newer.setRootPath("/y");
        newer.setUpdatedAt(Instant.now());
        repo.save(newer);
        
        List<Project> all = repo.findAllByOrderByUpdatedAtDesc();
        assertThat(all.get(0).getName()).isEqualTo("newer");
    }
}
```

---

## 7. 配置

### 7.1 application.yml

```yaml
spring:
  datasource:
    url: jdbc:sqlite:${TEAMMIND_DATA_PATH:file:./data}/teammind.db
    driver-class-name: org.sqlite.JDBC
  
  jpa:
    hibernate:
      ddl-auto: validate  # 重要：不自动创建
    properties:
      hibernate:
        dialect: org.hibernate.community.dialect.SQLiteDialect
        format_sql: true
  
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

### 7.2 旧 Schema 处理

如果旧数据库已经存在，需要：

1. 在 V2 脚本开头备份
2. 处理 NOT NULL 约束（可能需要 DEFAULT）
3. 处理数据冲突（如有）

---

## 8. 验收清单

- [ ] V2 migration 脚本就绪
- [ ] Flyway 自动应用成功
- [ ] 所有新 JPA Entity 通过 Hibernate 校验
- [ ] 现有数据（如果有）成功迁移
- [ ] Repository 单测全过
- [ ] Service 层 CRUD 可用
- [ ] 启动后无 schema 错误

---

## 9. 踩坑记录

> 实施时遇到的 schema / JPA 问题，更新在这里。

---

## 10. 接下来

- 读 [w3-claude-plugin.md](w3-claude-plugin.md)，实现第一个 Agent Plugin

---

**最后更新**：2026-08-14
**版本**：v0.1 Draft