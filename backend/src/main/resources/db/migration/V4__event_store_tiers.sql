-- V4: Phase 1C-3 Persistent Event Store — Tiered Storage
-- Adds tier tracking to runtime_events for hot/warm/cold archiving

-- Add tier column (default HOT for existing events)
ALTER TABLE runtime_events ADD COLUMN tier VARCHAR(8) NOT NULL DEFAULT 'HOT';
ALTER TABLE runtime_events ADD COLUMN archived_path TEXT;

-- Add indexes for tier-based queries
CREATE INDEX IF NOT EXISTS idx_event_tier ON runtime_events(tier);
CREATE INDEX IF NOT EXISTS idx_event_created ON runtime_events(created_at);

-- Migration note: existing events remain HOT (permanent).
-- New events are auto-tiered by EventStoreService based on EventType.
-- COLD events are archived to filesystem after 30 days.
-- WARM events are marked TRASH after 7 days.
