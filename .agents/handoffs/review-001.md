# Review: Phase 1A Runtime Contract

## Summary

Phase 1A delivers a clean-enough backbone: a 12-state `TaskStateMachine`, an in-memory `EventBus`, a set of JPA entities (`TaskExecution`, `Project`, `Plugin`, `PerformanceRecord`, `RoutingLesson`, `ApprovalRequest`), a `PolicyEngine` for hard constraints, and a `PluginBootstrap` that wires DB-backed plugins into the runtime. The interface surface reads well and the unit tests cover the documented happy paths. **But the contract is not production-ready** — the state machine is the system of record, the database is the cache, and on crash they fall out of sync with no recovery path. The `Evidence` lifecycle promised by the spec is not actually modeled; the file contains only an `EvidenceType` enum and a JSON blob on `TaskExecution`. Phase 1B/1C pressure points (parallel pipelines, multi-agent handoff, WebSocket projection) are largely invisible in the schema, which means a redesign will be required once those phases land. **Recommendation: REQUEST_REVISION** — high-severity issues around persistence consistency, evidence lifecycle, and crash recovery must be addressed before this is a stable contract.

The review below applies the six dimensions specified in the prompt.

## Findings

### CRITICAL

**C-1. State machine is in-memory only; DB is a write-behind cache, not a source of truth.**
`TaskStateMachine.stateStore` is a `ConcurrentHashMap<String, TaskState>` (`backend/src/main/java/com/teammind/runtime/TaskStateMachine.java:35`). On JVM restart, the map is empty. Non-terminal tasks in the DB (`SUBMITTED`, `ORCHESTRATING`, `EXECUTING`, `VERIFYING`, `REVIEWING`, `NEEDS_APPROVAL`, `APPROVED`, `RETRYING`) will be orphaned — no recovery sweep exists; `TaskExecutionRepository` has no `findByStateNotIn(...)` method. `TaskExecutionService.advance` (line 53) writes the new state to the DB **after** the in-memory map is updated and the event is emitted. **Failure scenario:** JVM crashes between `stateStore.put(taskId, nextState)` and `taskExecRepository.save(exec)`. The event has already been broadcast to subscribers (WebSocket / verifiers); the DB still reflects the old state. On restart, the state machine reports the task in the old state, but consumers may have already reacted to the new state. This is the textbook source-of-truth anti-pattern.

**C-2. `Evidence` lifecycle is not modeled; the contract does not exist.**
The prompt specifies `CLAIMED → COLLECTED → VERIFIED → INVALIDATED`. The only artifact in the codebase is `EvidenceType` (an enum of 4 verification kinds, `common/EvidenceType.java`) and `TaskExecution.evidence` (a `Map<String, Object>` JSON blob on the entity, line 73). There is no `Evidence` entity, no state field, no `INVALIDATED` event in `EventType`, and no verifier that re-checks an artifact after it has been verified. `EVIDENCE_VERIFIED` is terminal-on-success in `TaskStateMachine.handleVerifying` (line 148) — once `DONE`, a later modification to the artifact goes undetected. **Failure scenario:** A code-review agent verifies a git diff at T=10, the task transitions to `DONE`. At T=20 a human edits the verified file. The system still reports `DONE` with no invalidation cascade. This violates the spec's contract.

**C-3. `RETRYING` uses caller-provided `retryCount`/`maxRetries` from metadata, not the persisted entity.**
`TaskStateMachine.handleRetrying` (line 192) reads `Integer retryCount` and `Integer maxRetries` from the event's `metadata` map. The persisted `TaskExecution.maxRetries` (line 53, default 3) and `TaskExecution.retryCount` (line 49, default 0) are never consulted. **Failure scenario:** Two callers emit `TASK_FAILED` — one correctly passes `retryCount=3, maxRetries=3` (transitions to `ABANDONED`), the other omits the metadata (uses default `maxRetries=3` and `retryCount=null`, line 195, so the condition `retryCount >= max` is false → transitions to `EXECUTING`). The branch depends entirely on caller discipline. The DB has the authoritative values but the state machine doesn't read them.

### HIGH

**H-1. `REVIEWING` state does not handle `REVIEW_COMPLETED` event.**
`EventType.REVIEW_COMPLETED` is defined (`common/EventType.java:62`) but `handleReviewing` (line 157) only accepts `REVIEW_APPROVED`, `REVIEW_REJECTED`, `FINDING_CREATED`, `TASK_CANCELLED`. A reviewer that emits `REVIEW_COMPLETED` (the natural completion event) without one of the verdicts will cause the state machine to return `Optional.empty()`. The task is then stuck in `REVIEWING` forever (no event matches). Phase 1B will almost certainly spawn `REVIEW_COMPLETED` events from a parallel review subsystem — this needs explicit handling.

**H-2. `cancel()` races with `eventBus.emit()`.**
`TaskStateMachine.cancel` (line 222) writes the state then emits the event. The event is `TASK_CANCELLED` carrying `from_state` / `to_state` both set to `CANCELLED` (line 232). Subscribers that read state in response to the event see the correct final state, but subscribers that act asynchronously (the `WSEventPublisher` for 1B) will see `CANCELLED` events that don't carry the pre-cancel state. There's no `PRE_CANCEL_STATE` snapshot in the event payload. Operators debugging "why was this cancelled" lose context.

**H-3. `TaskExecution` schema cannot model Phase 1B's parallel pipeline.**
The entity has a single `state` field (`common/TaskState` enum) and a single `currentAgentId` / `currentRole`. A pipeline orchestrator with N parallel branches will want either (a) a sibling `PipelineStep` entity with its own state, or (b) a `steps` JSON array on `TaskExecution`. Neither is present. Migration to a relational `PipelineStep` table will require a non-trivial schema change because the existing `state` column is the source of truth for the linear state machine today. **Forward-compatibility hazard:** the single-state-on-task model is baked into the index `idx_task_exec_state` and into the controller's `active = {"ORCHESTRATING", "EXECUTING", "VERIFYING", "NEEDS_APPROVAL"}` array (`MissionControlController.java:90`).

**H-4. `RealE2EIntegrationTest` test isolation is fragile.**
`RealE2EIntegrationTest.java:83` hardcodes `TASK_ID = "e2e-task-001"` and relies on `@TestMethodOrder(OrderAnnotation.class)` to avoid collision. Reordering, parallelism, or rerun against a non-clean DB will produce `DataIntegrityViolationException` on the primary key. The right pattern is UUID per test (used by `TaskExecutionService.create`, line 32) — the test should use that helper.

**H-5. No event persistence; `getHistory()` is a stub.**
`TaskStateMachine.getHistory` (line 242) returns `List.of()` with a `// TODO: 持久化存储`. The `lastEventStore` map keeps only the last event per task. Replay, audit, and post-mortem analysis are impossible. Once WebSocket projection lands in 1B, the front-end will have no way to recover missed events after a disconnect.

**H-6. `EventBus.emit` is synchronous in-process only — no durability, no cross-process delivery.**
The bus works inside one JVM. Phase 1B's WebSocket projection will likely run on the same instance, but the docs (`EventBus.java:14-27`) describe it as "the core of event dispatch" — if a future evolution puts the orchestrator in a sidecar, the contract breaks silently. There is no `EventChannel` interface that could be swapped for a Kafka topic or similar.

### MEDIUM

**M-1. `RoutingLesson.key` is a free-form string but the SQL uses quoted reserved-word syntax.**
`entity/RoutingLesson.java:23` declares `@Column(name = "\"key\"", length = 64)` and the migration uses `key VARCHAR(64) PRIMARY KEY`. The contract for *how* a key is derived (composite of project/role/task-type? hash of condition?) is not documented. Two evolution passes that produce different keys for the same lesson will silently duplicate it.

**M-2. `PerformanceRecord` uniqueness constraint allows duplicate NULL rows.**
`V2__core_runtime.sql:102` defines `UNIQUE(project_id, plugin_id, role, task_type_id)`, but in SQLite (and most SQL engines), NULL is treated as distinct in unique constraints. Two records with `(project_id="p1", plugin_id="codex", role="IMPLEMENTER", task_type_id=NULL)` will both insert. The application code should either enforce non-null `task_type_id` or use a partial index.

**M-3. `PolicyEngine.matchesCondition` is a substring matcher, not a real expression evaluator.**
`PolicyEngine.java:131-152` implements `condition` as a hardcoded list of `if (condition.contains("CRITICAL")) { ... }`. A `condition` like `"taskType:database"` or `"plugin:codex and severity:critical"` is silently treated as `false`. Phase 1B may need real boolean composition; consider adopting a small expression language (Spring SpEL, JEXL, or a hand-rolled grammar).

**M-4. Manual `setState` is unguarded.**
`TaskStateMachine.setState` (line 214) bypasses `decideNextState`. An operator can move a `DONE` task back to `EXECUTING` without going through the canonical retry path. This is documented as "for emergency operations" but emits no event, so the audit trail is broken. At minimum, emit a state-override event carrying `from → to` and the operator id.

**M-5. `MissionControlController.setControlMode` (line 172) does not persist.**
The handler validates the mode string and returns success, but the `Project.controlMode` field is never updated and no `ProjectRepository` is injected. The endpoint is decorative — it claims to change the mode but the database is unaffected. This is a correctness bug for the front-end's "switch to Manual" UX.

**M-6. `TaskExecution.teamSnapshot` and `routingHistory` are JSON blobs with no schema version.**
These fields will need migrations when the team/routing schema evolves. There is no `schemaVersion` field, no migration helper, and no documented contract for what keys may appear. Once 1B's WebSocket subscribes to these, version skew between back-end and front-end will be painful.

**M-7. `TaskExecution.startedAt` is set at creation time.**
`TaskExecutionService.create` (line 42) sets `startedAt = LocalDateTime.now()` before the routing layer picks the agent. The real "start" is `routing.decided` going to `EXECUTING`. The current semantic conflates "created" with "started", which will distort metrics (avg time-to-first-byte, time-to-route).

### LOW

**L-1. `cancel()` does not record `cancelledBy` or `reason`.**
For audit purposes, who/why cancelled is needed. `ApprovalRequest` has `approvedBy` and `respondedAt`; the symmetric fields on `TaskExecution` are missing.

**L-2. `EventType` enum has 43 values; no version stamp on the event itself.**
`TeamMindEvent` has `TEAMMIND_EVENT_PROTOCOL_VERSION = 1` in a static field, but the field is not serialized into the JSON payload (`@JsonInclude(NON_NULL)` + no `@JsonProperty` on the field). WebSocket clients that survive a back-end redeploy will not see the bump.

**L-3. `TaskExecutionRepository` has no pagination.**
`findByProjectIdOrderByCreatedAtDesc` returns the full list. A project with 10K tasks will OOM the controller.

**L-4. `PluginAdapter.invoke()` (PluginBootstrap.java:119) returns a placeholder.**
The adapter wraps a DB-backed `Plugin` but its `invoke()` returns `PluginResult.success(entity.getId(), Map.of("note", "Invoke requires actual CLI integration"))`. This is honest but it means the `EventBus` will never see real plugin events from DB-loaded plugins; only built-in-registered plugins drive the integration test path. The integration test (`RealE2EIntegrationTest`) exercises the built-in registry, not the DB adapter, so the bootstrap path is untested end-to-end.

**L-5. `getProjectOverview` computes `avgDur` with `orElse(0)` which conflates "no data" with "0 ms avg".**
The DTO should distinguish the two cases (e.g., null vs 0) so the front-end can render "no data" instead of "0 ms".

**L-6. `MissionControlController.recalculate` (line 155) calls `tracker.recalculateAll()` — global, not per-project.**
The endpoint takes `projectId` but does a system-wide recalculation. Naming says "recalculate this project", implementation is "recalculate everything". Either rename to `recalculate-all` or scope to the project.

**L-7. The Javadoc on `TaskStateMachine` (line 14-26) declares a clean state diagram but the implementation has implicit branches.**
For example, `REVIEWING → NEEDS_APPROVAL` on `FINDING_CREATED (CRITICAL)` is not in the Javadoc diagram. The diagram is the spec; the code is the implementation. They diverge.

**L-8. `EvidenceType` enum is referenced nowhere in the runtime contract.**
Search for `EvidenceType` against `runtime/`, `event/`, and `controller/` returns no imports. The type exists for future use but is dead code today.

## Questions for Author

1. **Source of truth:** Is the intent that `TaskStateMachine` is the source of truth and the DB is a write-behind cache, or vice versa? The current code mixes both. The persistence model (`TaskExecution.state`) and the runtime model (`stateStore`) must agree on which is canonical, with a defined recovery procedure on restart.

2. **Evidence schema:** Where does the `Evidence` entity live? The prompt advertises a 4-state lifecycle but the only reference is `TaskExecution.evidence` (JSON blob). Will Phase 1B introduce a proper `Evidence` entity, or will the JSON blob grow until it has to be migrated?

3. **`RETRYING` retry count:** Should the state machine read `TaskExecution.retryCount`/`maxRetries` from the DB, or is the caller's metadata the contract? Right now it depends on the caller, which is fragile.

4. **`REVIEW_COMPLETED` handling:** Is this event meant to be a synonym for `REVIEW_APPROVED`, or does it carry its own meaning that needs a separate transition? The current state machine silently ignores it.

5. **Forward compatibility:** When `PipelineOrchestrator` lands in 1B, will the existing `TaskExecution.state` column be repurposed (e.g., "pipeline status") while sub-steps carry their own state, or will the linear FSM be extended to a hierarchical one? The answer drives whether `TaskExecution` needs a `pipelineId` FK now.

6. **Plugin adapter invoke:** `PluginBootstrap.PluginAdapter.invoke()` returns a placeholder. Does the DB-loaded plugin path need to dispatch to a real CLI binary in 1B, or is the DB row a configuration record only? The integration test confirms only the built-in registry path is real.

7. **Manual state override:** Is `setState()` for SRE use only, or will the front-end ever call it? If the latter, the audit story needs to be designed now.

8. **Race window: state-store vs DB write:** Is there a transactional outbox pattern planned? Right now, a `stateStore.put` + `eventBus.emit` + `taskExecRepository.save` sequence has at least two windows where a crash leaves the system inconsistent.

## Recommendation

**REQUEST_REVISION.**

The contract is well-structured at the type/interface level (`TaskState`, `EventType`, `TeamMindEvent`, `ProjectPolicy`) and the unit tests demonstrate the intended happy paths. But three CRITICAL issues make the runtime unsuitable for production:

- **C-1 (state = in-memory, DB = cache)** — needs a clear source-of-truth decision and a recovery sweep.
- **C-2 (Evidence lifecycle absent)** — the spec's defining feature is not implemented.
- **C-3 (retryCount ownership)** — entity vs metadata contract is undefined.

Phase 1B/1C forward-compatibility issues (H-3, H-5, H-6) need to be addressed before the next phase begins, otherwise the WebSocket projection and PipelineOrchestrator will land on a contract that has to be re-shaped.

The state machine, event protocol, and policy engine are sound foundations. The blockers are around persistence durability, the missing evidence subsystem, and the forward-compatibility seams. None of these are large surgical fixes — they are design decisions that need to be made before more code is built on top of this contract.

---

**Reviewer:** w7-claude-review
**Branch reviewed:** `w7-codex-runtime` (commit 8e3b9367)
**Files in scope:** `backend/src/main/java/com/teammind/{runtime,entity,common,event,controller,repository}/`, `backend/src/main/resources/db/migration/V2__core_runtime.sql`, corresponding unit tests.
**Test suite referenced:** `TaskStateMachineTest`, `PolicyEngineTest`, `EventBusTest`, `RealE2EIntegrationTest` (excerpt).
