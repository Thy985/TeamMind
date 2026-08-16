package com.teammind.runtime;

import com.teammind.common.*;
import com.teammind.entity.RuntimeEvent;
import com.teammind.repository.RuntimeEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivityExtractorTest {

    private RuntimeEventRepository eventRepo;
    private ActivityExtractor extractor;

    @BeforeEach
    void setUp() {
        eventRepo = mock(RuntimeEventRepository.class);
        extractor = new ActivityExtractor(eventRepo);
    }

    @Test
    @DisplayName("extract() 返回空摘要当没有事件时")
    void shouldReturnEmptyWhenNoEvents() {
        when(eventRepo.findByTaskIdOrderByCreatedAtAsc("task-1")).thenReturn(List.of());

        TaskActivity activity = extractor.extract("task-1");

        assertNotNull(activity);
        assertEquals("task-1", activity.taskId());
        assertTrue(activity.commandsExecuted().isEmpty());
        assertTrue(activity.filesChanged().isEmpty());
        assertTrue(activity.dependenciesChanged().isEmpty());
        assertTrue(activity.incidents().isEmpty());
        assertTrue(activity.verifications().isEmpty());
        assertTrue(activity.agentDecisions().isEmpty());
    }

    @Test
    @DisplayName("extract() 正确聚合 COMMAND_RUNNING 为命令列表")
    void shouldExtractCommands() {
        LocalDateTime now = LocalDateTime.now();
        RuntimeEvent cmd1 = RuntimeEvent.builder()
                .type(EventType.COMMAND_RUNNING)
                .taskId("task-1")
                .payload("{\"command\": \"npm install jsonwebtoken\", \"exit_code\": \"0\", \"duration_ms\": \"5000\"}")
                .createdAt(now)
                .build();
        RuntimeEvent cmd2 = RuntimeEvent.builder()
                .type(EventType.COMMAND_RUNNING)
                .taskId("task-1")
                .payload("{\"command\": \"npm test\", \"exit_code\": \"0\", \"duration_ms\": \"3000\"}")
                .createdAt(now.plusSeconds(10))
                .build();
        when(eventRepo.findByTaskIdOrderByCreatedAtAsc("task-1")).thenReturn(List.of(cmd1, cmd2));

        TaskActivity activity = extractor.extract("task-1");

        assertEquals(2, activity.commandsExecuted().size());
        assertTrue(activity.commandsExecuted().get(0).command().contains("npm install"));
        assertEquals(5000L, activity.commandsExecuted().get(0).durationMs());
        assertEquals(0, activity.commandsExecuted().get(0).exitCode());
    }

    @Test
    @DisplayName("extract() 正确提取文件变更")
    void shouldExtractFilesChanged() {
        LocalDateTime now = LocalDateTime.now();
        RuntimeEvent f1 = RuntimeEvent.builder()
                .type(EventType.FILE_CHANGED)
                .taskId("task-1")
                .payload("{\"file\": \"src/auth/jwt.ts\"}")
                .createdAt(now)
                .build();
        RuntimeEvent f2 = RuntimeEvent.builder()
                .type(EventType.FILE_CHANGED)
                .taskId("task-1")
                .payload("{\"file\": \"package.json\"}")
                .createdAt(now.plusSeconds(1))
                .build();
        RuntimeEvent f3 = RuntimeEvent.builder()
                .type(EventType.FILE_CHANGED)
                .taskId("task-1")
                .payload("{\"file\": \"src/auth/jwt.ts\"}")  // duplicate
                .createdAt(now.plusSeconds(2))
                .build();
        when(eventRepo.findByTaskIdOrderByCreatedAtAsc("task-1")).thenReturn(List.of(f1, f2, f3));

        TaskActivity activity = extractor.extract("task-1");

        assertEquals(2, activity.filesChanged().size());
        assertTrue(activity.filesChanged().contains("src/auth/jwt.ts"));
        assertTrue(activity.filesChanged().contains("package.json"));
    }

    @Test
    @DisplayName("extract() 正确提取依赖变更")
    void shouldExtractDependencyChanges() {
        LocalDateTime now = LocalDateTime.now();
        RuntimeEvent depAdd = RuntimeEvent.builder()
                .type(EventType.DEPENDENCY_CHANGED)
                .taskId("task-1")
                .payload("{\"action\": \"ADDED\", \"name\": \"jsonwebtoken\", \"version\": \"9.0.2\"}")
                .createdAt(now)
                .build();
        when(eventRepo.findByTaskIdOrderByCreatedAtAsc("task-1")).thenReturn(List.of(depAdd));

        TaskActivity activity = extractor.extract("task-1");

        assertEquals(1, activity.dependenciesChanged().size());
        TaskActivity.DependencyChange dep = activity.dependenciesChanged().get(0);
        assertEquals(TaskActivity.DependencyChange.Action.ADDED, dep.action());
        assertEquals("jsonwebtoken", dep.name());
        assertEquals("9.0.2", dep.version());
    }

    @Test
    @DisplayName("extract() Incident/Resolution 配对检测")
    void shouldDetectIncidentResolutionPair() {
        LocalDateTime now = LocalDateTime.now();
        RuntimeEvent error = RuntimeEvent.builder()
                .type(EventType.ERROR_CRITICAL)
                .taskId("task-1")
                .payload("{\"type\": \"Compilation Error\", \"message\": \"JWT payload type mismatch\"}")
                .pluginId("codex")
                .createdAt(now)
                .build();
        RuntimeEvent resolved = RuntimeEvent.builder()
                .type(EventType.ERROR_RECOVERABLE)
                .taskId("task-1")
                .payload("{}")
                .pluginId("claude-code")
                .createdAt(now.plusSeconds(5))
                .build();
        when(eventRepo.findByTaskIdOrderByCreatedAtAsc("task-1")).thenReturn(List.of(error, resolved));

        TaskActivity activity = extractor.extract("task-1");

        assertEquals(1, activity.incidents().size());
        TaskActivity.IncidentActivity incident = activity.incidents().get(0);
        assertTrue(incident.resolved());
        assertEquals("claude-code", incident.resolvedBy());
        assertNotNull(incident.type());
    }

    @Test
    @DisplayName("extract() 聚合测试通过/失败数量")
    void shouldAggregateVerificationResults() {
        LocalDateTime now = LocalDateTime.now();
        List<RuntimeEvent> events = List.of(
                buildEvent(EventType.TEST_PASSED, now),
                buildEvent(EventType.TEST_PASSED, now.plusSeconds(1)),
                buildEvent(EventType.TEST_FAILED, now.plusSeconds(2)),
                buildEvent(EventType.EVIDENCE_VERIFIED, now.plusSeconds(3))
        );
        when(eventRepo.findByTaskIdOrderByCreatedAtAsc("task-1")).thenReturn(events);

        TaskActivity activity = extractor.extract("task-1");

        assertEquals(2, activity.verifications().size());
        // Find the TEST_* verification
        var testVerif = activity.verifications().stream()
                .filter(v -> v.type().startsWith("TEST"))
                .findFirst().orElseThrow();
        assertEquals(2, testVerif.passed());
        assertEquals(1, testVerif.failed());
        // Find the EVIDENCE verification
        var evidenceVerif = activity.verifications().stream()
                .filter(v -> v.type().equals("EVIDENCE_VERIFIED"))
                .findFirst().orElseThrow();
        assertEquals(1, evidenceVerif.passed());
    }

    @Test
    @DisplayName("extract() 提取 Agent 决策")
    void shouldExtractAgentDecisions() {
        LocalDateTime now = LocalDateTime.now();
        RuntimeEvent decision = RuntimeEvent.builder()
                .type(EventType.DECISION_MADE)
                .taskId("task-1")
                .payload("{\"decision\": \"Switch to JWT auth\"}")
                .createdAt(now)
                .build();
        RuntimeEvent approval = RuntimeEvent.builder()
                .type(EventType.APPROVAL_GRANTED)
                .taskId("task-1")
                .payload("{\"reason\": \"Approved by human\"}")
                .createdAt(now.plusSeconds(1))
                .build();
        when(eventRepo.findByTaskIdOrderByCreatedAtAsc("task-1")).thenReturn(List.of(decision, approval));

        TaskActivity activity = extractor.extract("task-1");

        assertEquals(2, activity.agentDecisions().size());
        assertEquals("DECISION_MADE", activity.agentDecisions().get(0).type());
        assertEquals("Switch to JWT auth", activity.agentDecisions().get(0).content());
        assertEquals("APPROVAL_GRANTED", activity.agentDecisions().get(1).type());
    }

    @Test
    @DisplayName("extract() 混合事件 — 多 Category 同时提取")
    void shouldExtractMultipleCategories() {
        LocalDateTime now = LocalDateTime.now();
        List<RuntimeEvent> events = List.of(
                RuntimeEvent.builder().type(EventType.COMMAND_RUNNING).taskId("task-1")
                        .payload("{\"command\": \"npm install foo\"}").createdAt(now).build(),
                RuntimeEvent.builder().type(EventType.FILE_CHANGED).taskId("task-1")
                        .payload("{\"file\": \"src/main.ts\"}").createdAt(now.plusSeconds(1)).build(),
                RuntimeEvent.builder().type(EventType.DEPENDENCY_CHANGED).taskId("task-1")
                        .payload("{\"action\": \"ADDED\", \"name\": \"foo\", \"version\": \"1.0.0\"}")
                        .createdAt(now.plusSeconds(2)).build(),
                RuntimeEvent.builder().type(EventType.ERROR_CRITICAL).taskId("task-1")
                        .payload("{\"message\": \"Build failed\"}").createdAt(now.plusSeconds(3)).build(),
                RuntimeEvent.builder().type(EventType.TEST_PASSED).taskId("task-1")
                        .createdAt(now.plusSeconds(4)).build(),
                RuntimeEvent.builder().type(EventType.DECISION_MADE).taskId("task-1")
                        .payload("{\"decision\": \"Retry build\"}").createdAt(now.plusSeconds(5)).build()
        );
        when(eventRepo.findByTaskIdOrderByCreatedAtAsc("task-1")).thenReturn(events);

        TaskActivity activity = extractor.extract("task-1");

        assertEquals(1, activity.commandsExecuted().size());
        assertEquals(1, activity.filesChanged().size());
        assertEquals(1, activity.dependenciesChanged().size());
        assertEquals(1, activity.incidents().size());
        assertEquals(1, activity.verifications().size());
        assertEquals(1, activity.agentDecisions().size());
    }

    private RuntimeEvent buildEvent(EventType type, LocalDateTime time) {
        return RuntimeEvent.builder()
                .type(type)
                .taskId("task-1")
                .createdAt(time)
                .build();
    }
}
