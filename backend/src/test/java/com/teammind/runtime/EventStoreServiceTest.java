package com.teammind.runtime;

import com.teammind.common.EventType;
import com.teammind.entity.RuntimeEvent;
import com.teammind.repository.RuntimeEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventStoreServiceTest {

    private RuntimeEventRepository eventRepo;
    private EventStoreService service;
    private Path tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        this.tempDir = tempDir;
        eventRepo = mock(RuntimeEventRepository.class);
        service = new EventStoreService(eventRepo, tempDir);
    }

    @Nested
    @DisplayName("write")
    class WriteTests {

        @Test
        void shouldInferHOTTierForLifecycleEvents() {
            when(eventRepo.save(any())).thenAnswer(invocation -> {
                RuntimeEvent e = invocation.getArgument(0);
                e.setId(1L);
                return e;
            });

            RuntimeEvent event = service.write(EventType.TASK_STARTED, "task-1", "{\"key\":\"val\"}");
            assertThat(event.getTier()).isEqualTo(RuntimeEvent.EventTier.HOT);
            assertThat(event.getTaskId()).isEqualTo("task-1");
            verify(eventRepo).save(any());
        }

        @Test
        void shouldInferWARMTierForArtifactEvents() {
            when(eventRepo.save(any())).thenAnswer(invocation -> {
                RuntimeEvent e = invocation.getArgument(0);
                e.setId(2L);
                return e;
            });

            RuntimeEvent event = service.write(EventType.ARTIFACT_CREATED, "task-1", "{}");
            assertThat(event.getTier()).isEqualTo(RuntimeEvent.EventTier.WARM);
        }

        @Test
        void shouldInferCOLDTierForChunkEvents() {
            when(eventRepo.save(any())).thenAnswer(invocation -> {
                RuntimeEvent e = invocation.getArgument(0);
                e.setId(3L);
                return e;
            });

            RuntimeEvent event = service.write(EventType.AGENT_CHUNK, "task-1", "token");
            assertThat(event.getTier()).isEqualTo(RuntimeEvent.EventTier.COLD);
        }
    }

    @Nested
    @DisplayName("getEventChain")
    class QueryTests {

        @Test
        void shouldReturnEventsInOrder() {
            List<RuntimeEvent> events = List.of(
                    buildEvent(1L, EventType.TASK_STARTED),
                    buildEvent(2L, EventType.AGENT_STARTED),
                    buildEvent(3L, EventType.TASK_COMPLETED)
            );
            when(eventRepo.findByTaskIdOrderByCreatedAtAsc("task-1")).thenReturn(events);

            List<RuntimeEvent> result = service.getEventChain("task-1");
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(2).getId()).isEqualTo(3L);
        }

        @Test
        void shouldFilterAfterId() {
            List<RuntimeEvent> events = List.of(
                    buildEvent(1L, EventType.TASK_STARTED),
                    buildEvent(2L, EventType.AGENT_STARTED),
                    buildEvent(3L, EventType.TASK_COMPLETED)
            );
            when(eventRepo.findByIdAfterOrderByCreatedAtAsc(1L)).thenReturn(events.subList(1, 3));

            List<RuntimeEvent> result = service.getEventsAfter(1L);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("archiveColdEvents")
    class ArchiveTests {

        @Test
        void shouldArchiveOldColdEvents() {
            LocalDateTime oldDate = LocalDateTime.now().minus(31, ChronoUnit.DAYS);
            RuntimeEvent coldEvent = RuntimeEvent.builder()
                    .id(10L).type(EventType.AGENT_CHUNK)
                    .taskId("task-1").tier(RuntimeEvent.EventTier.COLD)
                    .payload("{\"chunk\":\"test\"}")
                    .createdAt(oldDate).build();

            when(eventRepo.findColdEventsBefore(any())).thenReturn(List.of(coldEvent));
            doAnswer(inv -> { RuntimeEvent e = inv.getArgument(0); e.setArchivedPath("archived/10-AGENT_CHUNK.json"); return null; }).when(eventRepo).save(any());

            int archived = service.archiveColdEvents();
            assertThat(archived).isEqualTo(1);
            assertThat(Files.exists(tempDir.resolve("10-AGENT_CHUNK.json"))).isTrue();
        }

        @Test
        void shouldMarkOldWarmEventsAsTrash() {
            LocalDateTime oldDate = LocalDateTime.now().minus(8, ChronoUnit.DAYS);
            RuntimeEvent warmEvent = RuntimeEvent.builder()
                    .id(20L).type(EventType.ARTIFACT_CREATED)
                    .taskId("task-1").tier(RuntimeEvent.EventTier.WARM)
                    .createdAt(oldDate).build();

            when(eventRepo.findWarmEventsBefore(any())).thenReturn(List.of(warmEvent));
            doAnswer(inv -> { RuntimeEvent e = inv.getArgument(0); return e; }).when(eventRepo).save(any());

            service.archiveColdEvents();
            assertThat(warmEvent.getTier()).isEqualTo(RuntimeEvent.EventTier.TRASH);
        }
    }

    @Nested
    @DisplayName("inferTier")
    class TierInferenceTests {

        @Test
        void shouldAssignCorrectTierForEachEventType() {
            // HOT events
            assertThat(RuntimeEvent.inferTier(EventType.TASK_STARTED)).isEqualTo(RuntimeEvent.EventTier.HOT);
            assertThat(RuntimeEvent.inferTier(EventType.TASK_COMPLETED)).isEqualTo(RuntimeEvent.EventTier.HOT);
            assertThat(RuntimeEvent.inferTier(EventType.EVIDENCE_VERIFIED)).isEqualTo(RuntimeEvent.EventTier.HOT);
            assertThat(RuntimeEvent.inferTier(EventType.APPROVAL_GRANTED)).isEqualTo(RuntimeEvent.EventTier.HOT);
            assertThat(RuntimeEvent.inferTier(EventType.ERROR_CRITICAL)).isEqualTo(RuntimeEvent.EventTier.HOT);

            // WARM events
            assertThat(RuntimeEvent.inferTier(EventType.ARTIFACT_CREATED)).isEqualTo(RuntimeEvent.EventTier.WARM);
            assertThat(RuntimeEvent.inferTier(EventType.FINDING_CREATED)).isEqualTo(RuntimeEvent.EventTier.WARM);
            assertThat(RuntimeEvent.inferTier(EventType.AGENT_STARTED)).isEqualTo(RuntimeEvent.EventTier.WARM);
            assertThat(RuntimeEvent.inferTier(EventType.TEST_PASSED)).isEqualTo(RuntimeEvent.EventTier.WARM);
            assertThat(RuntimeEvent.inferTier(EventType.HANDOFF_REQUESTED)).isEqualTo(RuntimeEvent.EventTier.WARM);

            // COLD events
            assertThat(RuntimeEvent.inferTier(EventType.AGENT_CHUNK)).isEqualTo(RuntimeEvent.EventTier.COLD);
            assertThat(RuntimeEvent.inferTier(EventType.TOOL_CALLED)).isEqualTo(RuntimeEvent.EventTier.COLD);
        }
    }

    private RuntimeEvent buildEvent(long id, EventType type) {
        return RuntimeEvent.builder()
                .id(id).type(type).taskId("task-1")
                .createdAt(LocalDateTime.now()).tier(RuntimeEvent.EventTier.HOT)
                .build();
    }
}
