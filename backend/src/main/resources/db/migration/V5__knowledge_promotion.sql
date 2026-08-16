-- V5: Phase 4 Sprint 4 — Knowledge Promotion
-- Stores user-promoted ADRs and Lessons from Execution Ledger

CREATE TABLE IF NOT EXISTS knowledge_entries (
    id              TEXT PRIMARY KEY,
    task_id         TEXT,
    project_id      TEXT,
    type            TEXT NOT NULL,          -- ADR / LESSON
    title           TEXT NOT NULL,
    description     TEXT,
    source          TEXT,                   -- INCIDENT / DEPENDENCY / DECISION / VERIFICATION
    confidence      REAL DEFAULT 0.5,
    dismissed       INTEGER DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_task ON knowledge_entries(task_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_project ON knowledge_entries(project_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_type ON knowledge_entries(type);
CREATE INDEX IF NOT EXISTS idx_knowledge_dismissed ON knowledge_entries(dismissed);
