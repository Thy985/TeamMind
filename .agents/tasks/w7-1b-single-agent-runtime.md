# W7 Phase 1B: Single-Agent Runtime — Implementation Record

**时间**: 2026-08-16
**Commit**: 7db54c0f
**分支**: main

## 交付物

### 新增服务 (3)
- **PipelineOrchestrator** — YAML pipeline 加载 → Execution/Step/Invocation 创建 → Artifact/Evidence 产出追踪
- **HumanControlService** — pause/resume/cancel/retry/approve/deny，全部经 TaskExecutionStateMachine 校验
- **RecoveryService** — CommandLineRunner，启动时扫描 in-flight execution，标记 RECOVERING/FAILED

### 新增 Pipeline YAML
- `resources/pipelines/single-agent.yaml` — implement → verify 单 Agent 链

### 新增测试 (23)
- **HumanControlServiceTest** — 9 个 pause/resume/cancel/retry/approve/deny 状态转移测试
- **PipelineOrchestratorTest** — 8 个 submit/start/complete/fail/retry 命令检查测试
- **RecoveryServiceTest** — 5 个 recovery 检测和转移测试

### 测试总数
- Phase 1A: 39 tests
- Phase 1B: 23 tests
- **总计: 62 单元测试，全部通过**

## 实现说明

### PipelineOrchestrator 设计要点
- 不直接调用 Agent Plugin（Phase 1C 引入）
- 跟踪 pid/duration/exitCode 通过 AgentInvocation
- 完成时创建 Artifact + claim Evidence
- 失败时记录 errorReason

### HumanControlService 设计要点
- 所有操作通过 stateMachine.canTransition() 预验证
- cancel() 会尝试 kill 运行中的进程
- retry() 不创建新 Execution（由 PipelineOrchestrator 负责）

### RecoveryService 设计要点
- 实现 CommandLineRunner，Spring Boot 启动时执行
- 扫描 RUNNING/PAUSE_REQUESTED/RETRYING 状态
- 简化版：直接标记 RECOVERING，等待用户决策
- 完整版需要 ProcessHandle.of(pid) 检查

## Claude Review

Phase 1B 实现完成后提交 Claude Code review。
Review 重点：
1. State transition 是否完整覆盖所有场景
2. RecoveryService 的进程检测逻辑是否健壮
3. HumanControlService 的 kill 逻辑是否正确

## Codex 运行时问题记录

**问题**: Codex CLI 启动时连接 `http://127.0.0.1:57321/v1/responses` 返回 502
**根因**: Codex CLI 是纯客户端，模型推理由独立 GUI 程序 Codex++ 提供
**修复**: 
1. 将 Codex++ 加入 Windows 开机自启（HKCU Run）
2. 记录此依赖链，作为 Agent Readiness 的前置知识

---
**关联 Phase**: [w7-1a-runtime-contract.md](./w7-1a-runtime-contract.md) → [w7-1c-multi-agent-handoff.md](./w7-1c-multi-agent-handoff.md)
