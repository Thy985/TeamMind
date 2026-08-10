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
