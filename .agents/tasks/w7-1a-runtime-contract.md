# W7 Phase 1A: Runtime Contract — Implementation Spec

## Goal

Implement the data model and state transition layer that forms the foundation of TeamMind's Project Runtime.

**What you WILL build:**
- Entity classes: `Task`, `TaskExecution`, `ExecutionStep`, `AgentInvocation`, `Artifact`, `Evidence`, `RuntimeEvent`
- Repository interfaces for all entities
- `TaskStateMachine` — state transition validation and execution
- `EvidenceLifecycleService` — evidence status transitions
- Unit tests for all state transitions
- Integration tests for the entity model

**What you will NOT build:**
- ❌ PipelineOrchestrator
- ❌ WebSocket / EventBridge
- ❌ Frontend
- ❌ Multi-agent handoff logic
- ❌ Recovery service
- ❌ Human control API

---

## 1. Entity Definitions

### 1.1 Task — user's intent (immutable once submitted)

```java
package com.teammind.entity;

@Entity
@Table(name = "tasks")
public class Task {
    @Id private String id;                    // UUID
    @Column(nullable = false) private String projectId;
    @Column(nullable = false, columnDefinition = "TEXT") private String objective;
    private String taskTypeId;                // inferred type (implementation, test, refactor, etc.)
    @Enumerated(EnumType.STRING) private TaskState state;  // SUBMITTED/RUNNING/DONE/FAILED/CANCELLED
    private String pipelineId;                // which pipeline definition to use
    private String assignedAgentId;           // initial agent assignment
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
```

**Key constraint:** Once a Task is created, `objective` and `projectId` never change. Only `state` and timing fields change.

### 1.2 TaskExecution — each attempt at running the task

```java
package com.teammind.entity;

@Entity
@Table(name = "task_executions")
public class TaskExecution {
    @Id private String id;                    // UUID
    @Column(nullable = false) private String taskId;  // FK → Task
    @Enumerated(EnumType.STRING) private TaskExecutionState state;
    private Integer attemptNumber;            // 1, 2, 3... (increments on retry)
    private String pipelineId;
    private String currentStepName;           // which step is currently running
    private String currentAgentId;            // which agent is currently active
    private String summary;                   // final outcome summary
    private Long durationMs;
    private String errorReason;               // why it failed
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
```

**Key constraint:** Each retry creates a NEW TaskExecution row with incremented `attemptNumber`. The original Task row is never modified except for its top-level `state`.

### 1.3 ExecutionStep — one step in the pipeline

```java
package com.teammind.entity;

@Entity
@Table(name = "execution_steps")
public class ExecutionStep {
    @Id private String id;
    @Column(nullable = false) private String executionId;  // FK → TaskExecution
    @Column(nullable = false) private String stepName;      // "implement" / "review" / "verify"
    @Column(nullable = false) private String agentId;       // "codex" / "claude-code" / "git-verifier"
    @Column(nullable = false) private String role;          // "LEAD" / "REVIEWER" / "VERIFIER"
    @Enumerated(EnumType.STRING) private ExecutionStepState state;
    @Column(columnDefinition = "TEXT") private String prompt;  // actual prompt sent to agent
    private String outputSummary;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
```

### 1.4 AgentInvocation — one CLI process call

```java
package com.teammind.entity;

@Entity
@Table(name = "agent_invocations")
public class AgentInvocation {
    @Id private String id;
    @Column(nullable = false) private String stepId;  // FK → ExecutionStep
    @Column(nullable = false) private String pluginId;
    @Column(columnDefinition = "TEXT") private String command;  // full command line
    private Integer exitCode;       // -1 = killed/timeout, 0 = success, >0 = error
    private Long durationMs;
    @Column(columnDefinition = "TEXT") private String stdoutSummary;  // first 500 chars
    @Column(columnDefinition = "TEXT") private String stderrSummary;  // first 500 chars
    
    // Recovery: track the OS process
    private Long pid;               // OS process ID
    private Boolean processAlive;    // checked at recovery time
    
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
```

**Key constraint:** Every time an Agent is invoked, a new AgentInvocation is created. This enables:
- Crash recovery: check if `pid` still exists via `ProcessHandle.of(pid)`
- Timeout detection: compare `startedAt` + timeout vs now
- Performance tracking: durationMs per invocation

### 1.5 Artifact — structured output from an agent

```java
package com.teammind.entity;

@Entity
@Table(name = "artifacts")
public class Artifact {
    @Id private String id;
    @Column(nullable = false) private String invocationId;  // FK → AgentInvocation
    @Column(nullable = false) private String type;  // CODE_DIFF / REVIEW_FINDINGS / IMPLEMENTATION_PLAN
    @Column(columnDefinition = "TEXT") private String summary;
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> data;
    private LocalDateTime createdAt;
}
```

### 1.6 Evidence — independent verification (lifecycle-aware)

```java
package com.teammind.entity;

@Entity
@Table(name = "evidence")
public class Evidence {
    @Id private String id;
    @Column(nullable = false) private String invocationId;  // FK → AgentInvocation
    @Enumerated(EnumType.STRING) private EvidenceType type;  // GIT_DIFF / TEST_EXECUTION / FILE_EXISTENCE
    @Enumerated(EnumType.STRING) private EvidenceStatus status;  // CLAIMED/COLLECTED/VERIFIED/INVALIDATED
    @Column(columnDefinition = "TEXT") private String description;
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> data;
    
    // Tie evidence to a specific commit/commit state
    private String baseCommit;
    private String artifactHash;  // hash of the associated Artifact
    
    private LocalDateTime collectedAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime invalidatedAt;
    private String invalidatedBy;  // reason or invocationId that caused invalidation
}
```

### 1.7 RuntimeEvent — persistent event store

```java
package com.teammind.entity;

@Entity
@Table(name = "runtime_events")
public class RuntimeEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                // auto-increment, ordered
    
    @Enumerated(EnumType.STRING) private EventType type;
    @Column(nullable = false) private String taskId;
    private String executionId;
    private String stepId;
    private String pluginId;
    private String role;
    @Column(columnDefinition = "TEXT") private String payload;  // JSON
    private LocalDateTime createdAt;
}
```

**Key constraint:** Events are written BEFORE they are broadcast. This guarantees that even if the process crashes, no event is lost.

### 1.8 TaskState (existing — do NOT modify)

Already defined in `com.teammind.common.TaskState`:
```
SUBMITTED, ORCHESTRATING, EXECUTING, VERIFYING, REVIEWING,
NEEDS_APPROVAL, APPROVED, DONE, FAILED, RETRYING, ABANDONED, CANCELLED
```

### 1.9 NEW: TaskExecutionState

```java
package com.teammind.common;

public enum TaskExecutionState {
    NEW,           // just created, not yet started
    PENDING,       // waiting for resources
    RUNNING,       // actively executing
    PAUSE_REQUESTED,  // user requested pause, waiting for current tool
    PAUSED,        // safely paused (current tool completed)
    NEEDS_APPROVAL, // critical finding, waiting for human
    APPROVING,     // approval in progress
    DONE,          // successfully completed
    FAILED,        // failed after all retries
    RETRYING,      // retrying after failure
    CANCELLED,     // user cancelled
    ABANDONED,     // denied during approval
    RECOVERING     // service restarted, process state unknown
}
```

### 1.10 NEW: ExecutionStepState

```java
package com.teammind.common;

public enum ExecutionStepState {
    PENDING,
    STARTED,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}
```

### 1.11 NEW: EvidenceStatus

```java
package com.teammind.common;

public enum EvidenceStatus {
    CLAIMED,      // agent claims this evidence exists
    COLLECTED,    // verifier has collected it
    VERIFIED,     // evidence is trusted
    INVALIDATED   // subsequent changes invalidate this evidence
}
```

### 1.12 NEW: EvidenceType

```java
package com.teammind.common;

public enum EvidenceType {
    GIT_DIFF,
    TEST_EXECUTION,
    FILE_EXISTENCE,
    COMMAND_EXIT,
    REVIEW_FINDINGS
}
```

---

## 2. Repository Interfaces

All repos extend `JpaRepository<Entity, String>` (except RuntimeEvent which uses `Long` id).

```java
// TaskRepository.java
public interface TaskRepository extends JpaRepository<Task, String> {
    List<Task> findByProjectIdOrderByCreatedAtDesc(String projectId);
    List<Task> findByState(TaskState state);
}

// TaskExecutionRepository.java — REPLACE existing, add new methods
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, String> {
    List<TaskExecution> findByTaskIdOrderByAttemptNumberDesc(String taskId);
    List<TaskExecution> findByState(TaskExecutionState state);
    List<TaskExecution> findByProjectIdAndState(String projectId, TaskExecutionState state);
}

// ExecutionStepRepository.java
public interface ExecutionStepRepository extends JpaRepository<ExecutionStep, String> {
    List<ExecutionStep> findByExecutionIdOrderByStartedAtAsc(String executionId);
    List<ExecutionStep> findByExecutionIdAndState(String executionId, ExecutionStepState state);
}

// AgentInvocationRepository.java
public interface AgentInvocationRepository extends JpaRepository<AgentInvocation, String> {
    List<AgentInvocation> findByStepId(String stepId);
    List<AgentInvocation> findByPidAndProcessAliveTrue();  // for recovery
}

// ArtifactRepository.java
public interface ArtifactRepository extends JpaRepository<Artifact, String> {
    List<Artifact> findByInvocationId(String invocationId);
}

// EvidenceRepository.java
public interface EvidenceRepository extends JpaRepository<Evidence, String> {
    List<Evidence> findByInvocationId(String invocationId);
    List<Evidence> findByStatus(EvidenceStatus status);
}

// RuntimeEventRepository.java
public interface RuntimeEventRepository extends JpaRepository<RuntimeEvent, Long> {
    List<RuntimeEvent> findByTaskIdOrderByCreatedAtAsc(String taskId);
    List<RuntimeEvent> findByIdAfterOrderByCreatedAtAsc(Long afterId);
    long countByTaskId(String taskId);
}
```

---

## 3. State Transition Rules

### 3.1 TaskExecution State Machine

| From State | Command | To State | Pre-condition | Post-action |
|------------|---------|----------|---------------|-------------|
| NEW | `submit()` | PENDING | taskId references valid Task | Create first ExecutionStep |
| PENDING | `start()` | RUNNING | — | Set startedAt, set currentAgentId |
| RUNNING | `pauseRequested()` | PAUSE_REQUESTED | — | Emit PAUSE_REQUESTED event |
| PAUSE_REQUESTED | `pauseComplete()` | PAUSED | Current tool finished | Emit PAUSED event |
| PAUSED | `resume()` | RUNNING | — | Emit RESUME event |
| RUNNING | `complete()` | DONE | All steps done | Set completedAt, update Task.state=DONE |
| RUNNING | `fail(reason)` | FAILED | — | Set errorReason, completedAt |
| RUNNING | `needsApproval(finding)` | NEEDS_APPROVAL | Critical finding | Emit APPROVAL_REQUIRED event |
| NEEDS_APPROVAL | `approve()` | APPROVING | User granted | Emit APPROVAL_GRANTED |
| APPROVING | `approvalProceed()` | RUNNING | — | Resume execution |
| NEEDS_APPROVAL | `deny()` | ABANDONED | User denied | Emit APPROVAL_DENIED |
| FAILED | `retry()` | RETRYING | retryCount < maxRetries | Increment attemptNumber |
| RETRYING | `startRetry()` | PENDING | — | Create new ExecutionStep |
| ANY | `cancel()` | CANCELLED | — | Kill associated processes |
| RUNNING | `recover()` | RECOVERING | Service restart | Check process liveness |

### 3.2 ExecutionStep State Machine

| From State | Trigger | To State |
|------------|---------|----------|
| PENDING | stepStarted() | STARTED |
| STARTED | stepRunning() | RUNNING |
| RUNNING | stepCompleted() | COMPLETED |
| RUNNING | stepFailed(error) | FAILED |
| PENDING | stepSkipped() | SKIPPED |

### 3.3 Evidence Lifecycle

| From State | Trigger | To State |
|------------|---------|----------|
| CLAIMED | collected() | COLLECTED |
| COLLECTED | verified() | VERIFIED |
| VERIFIED | invalidated(reason) | INVALIDATED |
| CLAIMED | invalidated(reason) | INVALIDATED |
| COLLECTED | invalidated(reason) | INVALIDATED |

---

## 4. Implementation Requirements

### 4.1 Package Structure

```
com.teammind.entity/
  ├── Task.java               (NEW)
  ├── TaskExecution.java      (EXISTING — ADD TaskExecutionState field)
  ├── ExecutionStep.java      (NEW)
  ├── AgentInvocation.java    (NEW)
  ├── Artifact.java           (NEW)
  ├── Evidence.java           (NEW)
  ├── RuntimeEvent.java       (NEW)
  └── ApprovalRequest.java    (EXISTING — keep as-is)

com.teammind.common/
  ├── TaskExecutionState.java (NEW)
  ├── ExecutionStepState.java (NEW)
  ├── EvidenceStatus.java     (NEW)
  ├── EvidenceType.java       (NEW)
  ├── TaskState.java          (EXISTING — keep as-is)
  └── EventType.java          (EXISTING — keep as-is)

com.teammind.repository/
  ├── TaskRepository.java              (NEW)
  ├── TaskExecutionRepository.java     (MODIFY — add new methods)
  ├── ExecutionStepRepository.java     (NEW)
  ├── AgentInvocationRepository.java   (NEW)
  ├── ArtifactRepository.java          (NEW)
  ├── EvidenceRepository.java          (NEW)
  └── RuntimeEventRepository.java      (NEW)

com.teammind.runtime/
  ├── TaskStateMachine.java            (NEW)
  └── EvidenceLifecycleService.java    (NEW)

com.teammind.dto/
  ├── TaskCreateRequest.java           (NEW)
  └── TaskStateSnapshot.java           (NEW)
```

### 4.2 Schema Changes

Add to `schema.sql`:
- `tasks` table
- `execution_steps` table
- `agent_invocations` table
- `artifacts` table
- `evidence` table
- `runtime_events` table
- Add `execution_state` column to `task_executions`
- Add `state` column to `task_executions` (rename existing if needed)

### 4.3 StateTransitionService

Create `TaskStateMachine.java` in `com.teammind.runtime`:

```java
@Component
public class TaskStateMachine {
    
    /**
     * Validate and execute a state transition.
     * @throws IllegalStateException if transition is illegal
     */
    public TaskExecutionState transition(TaskExecution execution, 
                                          TransitionCommand command) {
        // Validate transition is allowed
        validateTransition(execution.getState(), command);
        // Apply transition
        return applyTransition(execution, command);
    }
    
    private void validateTransition(TaskExecutionState from, TransitionCommand cmd) {
        // Check allowed transitions table
    }
    
    private TaskExecutionState applyTransition(TaskExecution execution, TransitionCommand cmd) {
        // Update state, timestamps, etc.
        return execution.getState();
    }
}
```

### 4.4 EvidenceLifecycleService

```java
@Component
public class EvidenceLifecycleService {
    
    public Evidence claim(String invocationId, EvidenceType type, String description) {
        // Create CLAIMED evidence
    }
    
    public Evidence collect(String evidenceId, Map<String, Object> data) {
        // Transition CLAIMED → COLLECTED
    }
    
    public Evidence verify(String evidenceId) {
        // Transition COLLECTED → VERIFIED
    }
    
    public Evidence invalidate(String evidenceId, String reason, String invalidedBy) {
        // Transition to INVALIDATED
    }
}
```

---

## 5. Tests

### 5.1 TaskStateMachineTest (unit tests)

```
shouldTransition_newToPending_whenSubmit()
shouldTransition_pendingToRunning_whenStart()
shouldTransition_runningToPauseRequested_whenPauseRequested()
shouldTransition_pauseRequestedToPaused_whenPauseComplete()
shouldTransition_pausedToRunning_whenResume()
shouldTransition_runningToDone_whenComplete()
shouldTransition_runningToNeedsApproval_whenCriticalFinding()
shouldTransition_needsApprovalToAbandoned_whenDeny()
shouldTransition_failedToRetrying_whenRetry()
shouldTransition_anyToCancelled_whenCancel()
shouldThrow_whenIllegalTransition()
shouldNotAllowRetry_whenMaxRetriesExceeded()
```

### 5.2 EvidenceLifecycleServiceTest (unit tests)

```
shouldClaimEvidence_whenInitialInvocation()
shouldCollect_whenVerifierRuns()
shouldVerify_whenEvidenceIsValid()
shouldInvalidate_whenRelatedArtifactChanges()
shouldThrow_whenInvalidTransition()
```

### 5.3 SchemaIntegrationTest (integration test)

```
shouldCreateAllTables()
shouldInsertAndQueryTask()
shouldInsertAndQueryExecutionWithSteps()
shouldInsertAndQueryEvidence()
```

---

## 6. Constraints for Codex

1. **DO NOT** implement PipelineOrchestrator
2. **DO NOT** implement WebSocket or EventBridge
3. **DO NOT** implement RecoveryService
4. **DO NOT** modify existing entities (TaskExecution already exists — extend it, don't replace)
5. **DO NOT** touch frontend code
6. All new enums go in `com.teammind.common` package
7. All new entities go in `com.teammind.entity` package
8. Use `@Builder`, `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` from Lombok
9. Use `@JdbcTypeCode(SqlTypes.JSON)` for JSON columns (existing pattern)
10. Run `mvn test` after each commit — must pass

---

## 7. Deliverables

When done, commit to `w7-codex-runtime` branch with message:

```
W7 Phase 1A: Runtime Contract entities + state machine

New entities: Task, ExecutionStep, AgentInvocation, Artifact, Evidence, RuntimeEvent
New enums: TaskExecutionState, ExecutionStepState, EvidenceStatus, EvidenceType
New repos: 7 repository interfaces
New services: TaskStateMachine, EvidenceLifecycleService
Tests: TaskStateMachineTest, EvidenceLifecycleServiceTest, SchemaIntegrationTest
Schema: added 6 new tables + 2 columns to task_executions
```
