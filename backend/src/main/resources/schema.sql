-- TeamMind Database Initialization Script
-- SQLite

-- Missions 表
CREATE TABLE IF NOT EXISTS missions (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    nodes TEXT,
    edges TEXT,
    logs TEXT,
    result TEXT
);

-- Agents 表
CREATE TABLE IF NOT EXISTS agents (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    icon TEXT,
    version TEXT,
    author TEXT,
    download_count INTEGER DEFAULT 0,
    rating REAL,
    status TEXT NOT NULL DEFAULT 'IDLE',
    permissions TEXT,
    config_path TEXT,
    current_prompt TEXT,
    original_prompt TEXT,
    tools TEXT,
    evolution_version INTEGER DEFAULT 1,
    evolution_score REAL,
    total_missions INTEGER DEFAULT 0,
    successful_missions INTEGER DEFAULT 0,
    total_tokens_used INTEGER DEFAULT 0,
    user_rating REAL,
    rating_count INTEGER DEFAULT 0,
    installed BOOLEAN DEFAULT FALSE,
    enabled BOOLEAN DEFAULT TRUE,
    installed_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    test_report TEXT
);

-- Templates 表
CREATE TABLE IF NOT EXISTS templates (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    icon TEXT,
    category TEXT,
    agents TEXT,
    config_path TEXT,
    is_public BOOLEAN DEFAULT FALSE,
    usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Evolution Records 表
CREATE TABLE IF NOT EXISTS evolution_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_id TEXT NOT NULL,
    type TEXT NOT NULL,
    from_version INTEGER,
    to_version INTEGER,
    before_state TEXT,
    after_state TEXT,
    description TEXT,
    reason TEXT,
    score_change REAL,
    is_automatic BOOLEAN DEFAULT FALSE,
    is_rolled_back BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP
);

-- LLM Calls 表（调用追踪）
CREATE TABLE IF NOT EXISTS llm_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    call_id TEXT,
    provider TEXT,
    model TEXT,
    call_type TEXT,
    agent_id TEXT,
    mission_id TEXT,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    estimated_cost REAL,
    latency_ms INTEGER,
    success BOOLEAN,
    error_message TEXT,
    request_summary TEXT,
    response_summary TEXT,
    metadata TEXT,
    created_at TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_missions_status ON missions(status);
CREATE INDEX IF NOT EXISTS idx_missions_created_at ON missions(created_at);
CREATE INDEX IF NOT EXISTS idx_agents_status ON agents(status);
CREATE INDEX IF NOT EXISTS idx_agents_installed ON agents(installed);
CREATE INDEX IF NOT EXISTS idx_templates_category ON templates(category);
CREATE INDEX IF NOT EXISTS idx_templates_is_public ON templates(is_public);
CREATE INDEX IF NOT EXISTS idx_evolution_agent_id ON evolution_records(agent_id);
CREATE INDEX IF NOT EXISTS idx_evolution_type ON evolution_records(type);
CREATE INDEX IF NOT EXISTS idx_llm_calls_provider ON llm_calls(provider);
CREATE INDEX IF NOT EXISTS idx_llm_calls_agent_id ON llm_calls(agent_id);
CREATE INDEX IF NOT EXISTS idx_llm_calls_mission_id ON llm_calls(mission_id);
CREATE INDEX IF NOT EXISTS idx_llm_calls_created_at ON llm_calls(created_at);

-- Users 表（登录/JWT 认证）
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    email TEXT NOT NULL,
    roles TEXT,
    permissions TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP
);

-- ============================================================
-- V2: Plugin Runtime 新表
-- ============================================================

-- Plugin 表（Agent / Tool / Verifier / Memory / Integration）
CREATE TABLE IF NOT EXISTS plugins (
    id              TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    vendor          TEXT,
    description     TEXT,
    version         TEXT,
    plugin_type     TEXT NOT NULL DEFAULT 'AGENT',
    capabilities    TEXT,
    philosophies    TEXT,
    preferred_roles TEXT,
    weak_roles      TEXT,
    avg_latency_ms  INTEGER,
    reliability_score REAL,
    cost_per_invocation REAL,
    enabled         BOOLEAN DEFAULT TRUE,
    health_status   TEXT DEFAULT 'HEALTHY',
    installed_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Project 表
CREATE TABLE IF NOT EXISTS projects (
    id              TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    description     TEXT,
    root_path       TEXT NOT NULL,
    team_config     TEXT,
    policy          TEXT,
    control_mode    TEXT DEFAULT 'SUPERVISED',
    shared_state    TEXT,
    agent_profile   TEXT,
    profile_name    TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_run_at     TIMESTAMP
);

-- TaskExecution 表
CREATE TABLE IF NOT EXISTS task_executions (
    id              TEXT PRIMARY KEY,
    project_id      TEXT NOT NULL,
    objective       TEXT NOT NULL,
    task_type_id    TEXT,
    state           TEXT NOT NULL DEFAULT 'SUBMITTED',
    current_agent_id TEXT,
    current_role    TEXT,
    retry_count     INTEGER DEFAULT 0,
    max_retries     INTEGER DEFAULT 3,
    team_snapshot   TEXT,
    routing_history TEXT,
    artifacts       TEXT,
    evidence        TEXT,
    final_score     REAL,
    summary         TEXT,
    duration_ms     INTEGER,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- PerformanceRecord 表（四层 Profile 核心数据）
CREATE TABLE IF NOT EXISTS performance_records (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id              TEXT NOT NULL,
    plugin_id               TEXT NOT NULL,
    role                    TEXT,
    task_type_id            TEXT,
    success_rate            REAL NOT NULL,
    avg_iterations          REAL,
    avg_duration_ms         INTEGER,
    sample_size             INTEGER DEFAULT 0,
    false_positive_rate     REAL,
    miss_rate               REAL,
    user_acceptance_rate    REAL,
    last_updated            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id, plugin_id, role, task_type_id)
);

-- RoutingLesson 表
CREATE TABLE IF NOT EXISTS routing_lessons (
    key             TEXT PRIMARY KEY,
    project_id      TEXT NOT NULL,
    condition       TEXT,
    task_type_id    TEXT,
    role            TEXT,
    plugin_id       TEXT,
    confidence      REAL DEFAULT 0.5,
    evidence_count  INTEGER DEFAULT 0,
    learned_at      TIMESTAMP,
    last_updated    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- ApprovalRequest 表
CREATE TABLE IF NOT EXISTS approval_requests (
    id              TEXT PRIMARY KEY,
    task_id         TEXT NOT NULL,
    plugin_id       TEXT NOT NULL,
    role            TEXT,
    question        TEXT NOT NULL,
    context         TEXT,
    result          TEXT DEFAULT 'PENDING',
    approved_by     TEXT,
    timeout_ms      INTEGER,
    expires_at      TIMESTAMP,
    responded_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES task_executions(id) ON DELETE CASCADE
);

-- V2 索引
CREATE INDEX IF NOT EXISTS idx_plugins_type ON plugins(plugin_type);
CREATE INDEX IF NOT EXISTS idx_plugins_enabled ON plugins(enabled);
CREATE INDEX IF NOT EXISTS idx_projects_name ON projects(name);
CREATE INDEX IF NOT EXISTS idx_task_exec_project ON task_executions(project_id);
CREATE INDEX IF NOT EXISTS idx_task_exec_state ON task_executions(state);
CREATE INDEX IF NOT EXISTS idx_perf_project ON performance_records(project_id);
CREATE INDEX IF NOT EXISTS idx_perf_plugin ON performance_records(plugin_id);
CREATE INDEX IF NOT EXISTS idx_lessons_project ON routing_lessons(project_id);
CREATE INDEX IF NOT EXISTS idx_approval_task ON approval_requests(task_id);
CREATE INDEX IF NOT EXISTS idx_approval_result ON approval_requests(result);

-- ─── Phase 1A Runtime Contract Tables ──────────────────────

-- tasks 表 — 用户任务意图（一次创建，核心字段不可变）
CREATE TABLE IF NOT EXISTS tasks (
    id              TEXT PRIMARY KEY,
    project_id      TEXT NOT NULL,
    objective       TEXT NOT NULL,
    task_type_id    TEXT,
    state           TEXT NOT NULL DEFAULT 'SUBMITTED',
    pipeline_id     TEXT,
    assigned_agent_id TEXT,
    retry_count     INTEGER DEFAULT 0,
    max_retries     INTEGER DEFAULT 3,
    created_at      TIMESTAMP NOT NULL,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- execution_steps 表 — Pipeline 的每个步骤
CREATE TABLE IF NOT EXISTS execution_steps (
    id              TEXT PRIMARY KEY,
    execution_id    TEXT NOT NULL,
    step_name       TEXT NOT NULL,
    agent_id        TEXT NOT NULL,
    role            TEXT NOT NULL,
    state           TEXT NOT NULL DEFAULT 'PENDING',
    prompt          TEXT,
    output_summary  TEXT,
    duration_ms     INTEGER,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    FOREIGN KEY (execution_id) REFERENCES task_executions(id) ON DELETE CASCADE
);

-- agent_invocations 表 — 一次 CLI 进程调用
CREATE TABLE IF NOT EXISTS agent_invocations (
    id              TEXT PRIMARY KEY,
    step_id         TEXT NOT NULL,
    plugin_id       TEXT NOT NULL,
    command         TEXT,
    exit_code       INTEGER DEFAULT -1,
    duration_ms     INTEGER,
    stdout_summary  TEXT,
    stderr_summary  TEXT,
    pid             INTEGER,
    process_alive   INTEGER,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    FOREIGN KEY (step_id) REFERENCES execution_steps(id) ON DELETE CASCADE
);

-- artifacts 表 — Agent 产出的结构化产物
CREATE TABLE IF NOT EXISTS artifacts (
    id              TEXT PRIMARY KEY,
    invocation_id   TEXT NOT NULL,
    type            TEXT NOT NULL,
    summary         TEXT,
    data            TEXT,
    created_at      TIMESTAMP,
    FOREIGN KEY (invocation_id) REFERENCES agent_invocations(id) ON DELETE CASCADE
);

-- evidence 表 — 独立验证的证据（有生命周期）
CREATE TABLE IF NOT EXISTS evidence (
    id              TEXT PRIMARY KEY,
    invocation_id   TEXT NOT NULL,
    type            TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'CLAIMED',
    description     TEXT,
    data            TEXT,
    base_commit     TEXT,
    artifact_hash   TEXT,
    collected_at    TIMESTAMP,
    verified_at     TIMESTAMP,
    invalidated_at  TIMESTAMP,
    invalidated_by  TEXT,
    FOREIGN KEY (invocation_id) REFERENCES agent_invocations(id) ON DELETE CASCADE
);

-- runtime_events 表 — 持久化事件存储（用于 replay）
CREATE TABLE IF NOT EXISTS runtime_events (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    type            TEXT NOT NULL,
    task_id         TEXT NOT NULL,
    execution_id    TEXT,
    step_id         TEXT,
    plugin_id       TEXT,
    role            TEXT,
    payload         TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Phase 1A 索引（仅 CREATE INDEX，ALTER TABLE 在 V3 迁移中）
CREATE INDEX IF NOT EXISTS idx_tasks_project ON tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_tasks_state ON tasks(state);
CREATE INDEX IF NOT EXISTS idx_execution_steps_execution ON execution_steps(execution_id);
CREATE INDEX IF NOT EXISTS idx_agent_invocations_step ON agent_invocations(step_id);
CREATE INDEX IF NOT EXISTS idx_artifacts_invocation ON artifacts(invocation_id);
CREATE INDEX IF NOT EXISTS idx_evidence_invocation ON evidence(invocation_id);
CREATE INDEX IF NOT EXISTS idx_evidence_status ON evidence(status);
CREATE INDEX IF NOT EXISTS idx_runtime_events_task ON runtime_events(task_id);
CREATE INDEX IF NOT EXISTS idx_runtime_events_id ON runtime_events(id);

