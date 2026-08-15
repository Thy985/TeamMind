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

