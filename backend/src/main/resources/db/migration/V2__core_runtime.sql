-- TeamMind Schema Migration V2
-- 新增：Plugin / Project / TaskExecution / PerformanceRecord / RoutingLesson / ApprovalRequest
-- 旧表（agents / missions / team_templates）保留兼容

-- ============================================================
-- 1. Plugin 表 — 注册的 Agent / Tool / Verifier
-- ============================================================
CREATE TABLE IF NOT EXISTS plugins (
    id              VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    vendor          VARCHAR(255),
    description     TEXT,
    version         VARCHAR(32),
    plugin_type     VARCHAR(32) NOT NULL,  -- AGENT / TOOL / VERIFIER / MEMORY / INTEGRATION
    capabilities    TEXT,                  -- JSON array of strings
    philosophies    TEXT,                  -- JSON array of strings
    preferred_roles TEXT,                  -- JSON array of strings
    weak_roles      TEXT,                  -- JSON array of strings
    avg_latency_ms  BIGINT,
    reliability_score REAL,
    cost_per_invocation REAL,
    enabled         INTEGER DEFAULT 1,
    health_status   VARCHAR(32) DEFAULT 'HEALTHY',
    installed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plugins_type ON plugins(plugin_type);
CREATE INDEX IF NOT EXISTS idx_plugins_enabled ON plugins(enabled);

-- ============================================================
-- 2. Project 表
-- ============================================================
CREATE TABLE IF NOT EXISTS projects (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    root_path       VARCHAR(512) NOT NULL,
    team_config     TEXT,                  -- JSON: roles, profileName
    policy          TEXT,                  -- JSON: ProjectPolicy
    control_mode    VARCHAR(32) DEFAULT 'SUPERVISED',  -- AUTOMATED / SUPERVISED / MANUAL
    shared_state    TEXT,                  -- JSON: context, decisions, artifacts
    agent_profile   TEXT,                  -- JSON: 4-layer performance profile
    profile_name    VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_run_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_projects_name ON projects(name);
CREATE INDEX IF NOT EXISTS idx_projects_root_path ON projects(root_path);

-- ============================================================
-- 3. TaskExecution 表（新任务执行记录）
-- ============================================================
CREATE TABLE IF NOT EXISTS task_executions (
    id              VARCHAR(36) PRIMARY KEY,
    project_id      VARCHAR(36) NOT NULL,
    objective       TEXT NOT NULL,
    task_type_id    VARCHAR(64),
    state           VARCHAR(32) NOT NULL,  -- TaskState enum
    current_agent_id VARCHAR(64),
    current_role    VARCHAR(32),
    retry_count     INTEGER DEFAULT 0,
    max_retries     INTEGER DEFAULT 3,
    team_snapshot   TEXT,                  -- JSON
    routing_history TEXT,                  -- JSON
    artifacts       TEXT,                  -- JSON
    evidence        TEXT,                  -- JSON
    final_score     REAL,
    summary         TEXT,
    duration_ms     BIGINT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_task_exec_project ON task_executions(project_id);
CREATE INDEX IF NOT EXISTS idx_task_exec_state ON task_executions(state);
CREATE INDEX IF NOT EXISTS idx_task_exec_created ON task_executions(created_at DESC);

-- ============================================================
-- 4. PerformanceRecord 表（四层 Profile 的核心数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS performance_records (
    id                      BIGINT PRIMARY KEY AUTOINCREMENT,
    project_id              VARCHAR(36) NOT NULL,
    plugin_id               VARCHAR(64) NOT NULL,
    role                    VARCHAR(32),
    task_type_id            VARCHAR(64),
    success_rate            REAL NOT NULL,
    avg_iterations          REAL,
    avg_duration_ms         BIGINT,
    sample_size             INTEGER DEFAULT 0,
    false_positive_rate     REAL,
    miss_rate               REAL,
    user_acceptance_rate    REAL,
    last_updated            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id, plugin_id, role, task_type_id)
);

CREATE INDEX IF NOT EXISTS idx_perf_project ON performance_records(project_id);
CREATE INDEX IF NOT EXISTS idx_perf_plugin ON performance_records(plugin_id);
CREATE INDEX IF NOT EXISTS idx_perf_role ON performance_records(role);
CREATE INDEX IF NOT EXISTS idx_perf_task_type ON performance_records(task_type_id);

-- ============================================================
-- 5. RoutingLesson 表
-- ============================================================
CREATE TABLE IF NOT EXISTS routing_lessons (
    key             VARCHAR(64) PRIMARY KEY,
    project_id      VARCHAR(36) NOT NULL,
    condition       TEXT,
    task_type_id    VARCHAR(64),
    role            VARCHAR(32),
    plugin_id       VARCHAR(64),
    confidence      REAL DEFAULT 0.5,
    evidence_count  INTEGER DEFAULT 0,
    learned_at      TIMESTAMP,
    last_updated    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_lessons_project ON routing_lessons(project_id);

-- ============================================================
-- 6. ApprovalRequest 表
-- ============================================================
CREATE TABLE IF NOT EXISTS approval_requests (
    id              VARCHAR(64) PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    plugin_id       VARCHAR(64) NOT NULL,
    role            VARCHAR(32),
    question        TEXT NOT NULL,
    context         TEXT,                  -- JSON
    result          VARCHAR(32) DEFAULT 'PENDING',  -- PENDING / GRANTED / DENIED / ...
    approved_by     VARCHAR(255),
    timeout_ms      BIGINT,
    expires_at      TIMESTAMP,
    responded_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES task_executions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_approval_task ON approval_requests(task_id);
CREATE INDEX IF NOT EXISTS idx_approval_result ON approval_requests(result);
CREATE INDEX IF NOT EXISTS idx_approval_expires ON approval_requests(expires_at) WHERE expires_at IS NOT NULL;

-- ============================================================
-- 7. 初始化默认 Plugin
-- ============================================================
INSERT OR IGNORE INTO plugins (id, name, vendor, description, version, plugin_type,
    capabilities, philosophies, preferred_roles, weak_roles,
    avg_latency_ms, reliability_score, cost_per_invocation, enabled, health_status, installed_at)
VALUES
    ('claude-code', 'Claude Code', 'Anthropic',
     '安全导向的 AI 编程助手，强调权限边界和显式审批',
     '2.1.215', 'AGENT',
     '["implementation","code_review","security_review","architecture_design","documentation"]',
     '["safety","controlled_action","explicit_permission","cautious_execution"]',
     '["security_review","code_review","architecture_review"]',
     '["bulk_refactor","rapid_iteration"]',
     45000, 0.92, 0.05, 1, 'HEALTHY', CURRENT_TIMESTAMP),

    ('codex', 'Codex CLI', 'OpenAI',
     '执行导向的 AI 编程助手，强调迭代构建和测试闭环',
     '0.144.5', 'AGENT',
     '["implementation","test_generation","refactoring","api_design"]',
     '["execution","iterative_build","test_driven","rapid_iteration"]',
     '["implementation","test_generation","refactoring"]',
     '["security_review","architecture_review"]',
     30000, 0.90, 0.03, 1, 'HEALTHY', CURRENT_TIMESTAMP);

-- ============================================================
-- 8. WAL 模式 + busy_timeout（已在 application.yml 配置）
-- ============================================================
