-- TeamMind Initial Data
-- Default Agents and Templates

-- Insert default agents
INSERT INTO agents (id, name, description, icon, version, author, download_count, rating, status, permissions, installed, enabled, created_at, updated_at, evolution_version, evolution_score)
VALUES 
    ('agent-1', 'Code Reviewer', 'Automatically reviews code for best practices and potential issues', '🔍', '1.0.0', 'TeamMind', 1234, 4.8, 'IDLE', '["read:files", "write:comments", "read:repository"]', 0, 1, datetime('now'), datetime('now'), 1, 0.0),
    ('agent-2', 'Task Planner', 'Breaks down complex tasks into actionable steps', '📋', '1.2.0', 'TeamMind', 892, 4.5, 'IDLE', '["read:tasks", "write:tasks", "read:context"]', 0, 1, datetime('now'), datetime('now'), 1, 0.0),
    ('agent-3', 'Data Analyst', 'Analyzes data and generates insights', '📊', '2.0.0', 'TeamMind', 567, 4.6, 'IDLE', '["read:data", "write:reports", "read:visualizations"]', 0, 1, datetime('now'), datetime('now'), 1, 0.0),
    ('agent-4', 'Test Engineer', 'Automated testing and quality assurance', '🧪', '1.1.0', 'TeamMind', 423, 4.4, 'IDLE', '["read:code", "write:tests", "execute:tests"]', 0, 1, datetime('now'), datetime('now'), 1, 0.0),
    ('agent-5', 'Documentation Writer', 'Generates and maintains documentation', '📝', '1.0.0', 'TeamMind', 356, 4.3, 'IDLE', '["read:code", "write:docs"]', 0, 1, datetime('now'), datetime('now'), 1, 0.0);

-- Insert default templates
INSERT INTO templates (id, name, description, icon, category, agents, is_public, usage_count, created_at, updated_at)
VALUES 
    ('template-1', 'Code Review Workflow', 'Automated code review with multiple agents', '🔍', 'Development', '["agent-1", "agent-2"]', 1, 0, datetime('now'), datetime('now')),
    ('template-2', 'Data Pipeline', 'Extract, transform, and load data', '🔄', 'Data', '["agent-3"]', 1, 0, datetime('now'), datetime('now')),
    ('template-3', 'Documentation Generator', 'Generate docs from code comments', '📝', 'Documentation', '["agent-5"]', 1, 0, datetime('now'), datetime('now')),
    ('template-4', 'Full Dev Cycle', 'Complete development workflow', '🚀', 'Development', '["agent-1", "agent-2", "agent-4", "agent-5"]', 1, 0, datetime('now'), datetime('now'));

-- Insert sample missions
INSERT INTO missions (id, title, description, status, created_at, updated_at, nodes, edges, logs)
VALUES 
    ('mission-1', 'Code review for auth module', 'Review authentication module for security issues', 'COMPLETED', datetime('now', '-2 hours'), datetime('now', '-1 hour'), '[]', '[]', '[]'),
    ('mission-2', 'Analyze user engagement data', 'Process and analyze user engagement metrics', 'RUNNING', datetime('now', '-5 hours'), datetime('now'), '[]', '[]', '[]'),
    ('mission-3', 'Generate API documentation', 'Create comprehensive API documentation', 'COMPLETED', datetime('now', '-1 day'), datetime('now', '-1 day'), '[]', '[]', '[]');

-- ============================================================
-- V2: Default Plugins
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
     45000, 0.92, 0.05, 1, 'HEALTHY', datetime('now')),

    ('codex', 'Codex CLI', 'OpenAI',
     '执行导向的 AI 编程助手，强调迭代构建和测试闭环',
     '0.144.5', 'AGENT',
     '["implementation","test_generation","refactoring","api_design"]',
     '["execution","iterative_build","test_driven","rapid_iteration"]',
     '["implementation","test_generation","refactoring"]',
     '["security_review","architecture_review"]',
     30000, 0.90, 0.03, 1, 'HEALTHY', datetime('now'));
