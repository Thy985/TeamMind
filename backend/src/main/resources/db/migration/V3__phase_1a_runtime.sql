-- V3: Phase 1A Runtime Contract — 扩展 task_executions + 新表
-- 新增列（幂等：SQLite 不支持 IF NOT EXISTS on ALTER，靠 DDL-auto=update 兜底）

-- task_executions 扩展列
ALTER TABLE task_executions ADD COLUMN task_id TEXT;
ALTER TABLE task_executions ADD COLUMN execution_state TEXT DEFAULT 'NEW';
ALTER TABLE task_executions ADD COLUMN current_step_name TEXT;
ALTER TABLE task_executions ADD COLUMN agent_id TEXT;
ALTER TABLE task_executions ADD COLUMN error_reason TEXT;
ALTER TABLE task_executions ADD COLUMN attempt_number INTEGER DEFAULT 1;

-- 新表
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

CREATE TABLE IF NOT EXISTS artifacts (
    id              TEXT PRIMARY KEY,
    invocation_id   TEXT NOT NULL,
    type            TEXT NOT NULL,
    summary         TEXT,
    data            TEXT,
    created_at      TIMESTAMP,
    FOREIGN KEY (invocation_id) REFERENCES agent_invocations(id) ON DELETE CASCADE
);

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

-- Phase 1A 索引
CREATE INDEX IF NOT EXISTS idx_tasks_project ON tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_tasks_state ON tasks(state);
CREATE INDEX IF NOT EXISTS idx_executions_task ON task_executions(task_id);
CREATE INDEX IF NOT EXISTS idx_executions_exec_state ON task_executions(execution_state);
CREATE INDEX IF NOT EXISTS idx_execution_steps_execution ON execution_steps(execution_id);
CREATE INDEX IF NOT EXISTS idx_agent_invocations_step ON agent_invocations(step_id);
CREATE INDEX IF NOT EXISTS idx_artifacts_invocation ON artifacts(invocation_id);
CREATE INDEX IF NOT EXISTS idx_evidence_invocation ON evidence(invocation_id);
CREATE INDEX IF NOT EXISTS idx_evidence_status ON evidence(status);
CREATE INDEX IF NOT EXISTS idx_runtime_events_task ON runtime_events(task_id);
CREATE INDEX IF NOT EXISTS idx_runtime_events_id ON runtime_events(id);
