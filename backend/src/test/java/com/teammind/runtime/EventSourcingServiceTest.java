package com.teammind.runtime;

import com.teammind.common.EventType;
import com.teammind.entity.RuntimeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventSourcingServiceTest {

    private EventStoreService eventStore;
    private EventSourcingService sourcing;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStoreService.class);
        sourcing = new EventSourcingService(eventStore);
    }

    @Test
    @DisplayName("replay from beginning when fromEventId is null")
    void shouldReplayAllEvents() {
        List<RuntimeEvent> events = List.of(
                buildEvent(1L, EventType.TASK_STARTED),
                buildEvent(2L, EventType.AGENT_STARTED),
                buildEvent(3L, EventType.TASK_COMPLETED)
        );
        when(eventStore.getEventsAfter(eq("task-1"), isNull())).thenReturn(events);

        List<RuntimeEvent> result = sourcing.replay("task-1", null);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("replay only events after fromEventId")
    void shouldReplayFromEventId() {
        List<RuntimeEvent> events = List.of(
                buildEvent(2L, EventType.AGENT_STARTED),
                buildEvent(3L, EventType.TASK_COMPLETED)
        );
        when(eventStore.getEventsAfter(eq("task-1"), eq(1L))).thenReturn(events);

        List<RuntimeEvent> result = sourcing.replay("task-1", 1L);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getLastEventId returns max ID")
    void shouldGetLastEventId() {
        List<RuntimeEvent> events = List.of(
                buildEvent(1L, EventType.TASK_STARTED),
                buildEvent(5L, EventType.AGENT_STARTED),
                buildEvent(3L, EventType.TASK_COMPLETED)
        );
        when(eventStore.getEventChain("task-1")).thenReturn(events);

        long lastId = sourcing.getLastEventId("task-1");
        assertThat(lastId).isEqualTo(5L);
    }

    @Test
    @DisplayName("validateChainIntegrity returns true for continuous IDs")
    void shouldValidateContinuousChain() {
        List<RuntimeEvent> events = List.of(
                buildEvent(1L, EventType.TASK_STARTED),
                buildEvent(2L, EventType.AGENT_STARTED),
                buildEvent(3L, EventType.TASK_COMPLETED)
        );
        when(eventStore.getEventChain("task-1")).thenReturn(events);

        assertThat(sourcing.validateChainIntegrity("task-1")).isTrue();
    }

    @Test
    @DisplayName("validateChainIntegrity returns false when gaps detected")
    void shouldDetectGapInChain() {
        List<RuntimeEvent> events = List.of(
                buildEvent(1L, EventType.TASK_STARTED),
                buildEvent(3L, EventType.AGENT_STARTED),  // gap: 2 is missing
                buildEvent(4L, EventType.TASK_COMPLETED)
        );
        when(eventStore.getEventChain("task-1")).thenReturn(events);

        assertThat(sourcing.validateChainIntegrity("task-1")).isFalse();
    }

    @Test
    @DisplayName("countByType returns correct counts")
    void shouldCountByType() {
        List<RuntimeEvent> events = List.of(
                buildEvent(1L, EventType.TASK_STARTED),
                buildEvent(2L, EventType.TASK_COMPLETED),
                buildEvent(3L, EventType.TASK_STARTED)
        );
        when(eventStore.getEventChain("task-1")).thenReturn(events);

        var counts = sourcing.countByType("task-1");
        assertThat(counts.get("TASK_STARTED")).isEqualTo(2);
        assertThat(counts.get("TASK_COMPLETED")).isEqualTo(1);
    }

    @Test
    @DisplayName("getEventsInTimeRange filters correctly")
    void shouldFilterByTimeRange() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 0, 0);

        List<RuntimeEvent> events = List.of(
                buildEventWithDate(1L, EventType.TASK_STARTED, LocalDateTime.of(2025, 12, 15, 0, 0)),
                buildEventWithDate(2L, EventType.AGENT_STARTED, LocalDateTime.of(2026, 1, 15, 0, 0)),
                buildEventWithDate(3L, EventType.TASK_COMPLETED, LocalDateTime.of(2026, 2, 15, 0, 0))
        );
        when(eventStore.getEventChain("task-1")).thenReturn(events);

        List<RuntimeEvent> result = sourcing.getEventsInTimeRange("task-1", start, end);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(2L);
    }

    private RuntimeEvent buildEvent(long id, EventType type) {
        return RuntimeEvent.builder()
                .id(id).type(type).taskId("task-1")
                .createdAt(LocalDateTime.now()).build();
    }

    private RuntimeEvent buildEventWithDate(long id, EventType type, LocalDateTime date) {
        return RuntimeEvent.builder()
                .id(id).type(type).taskId("task-1")
                .createdAt(date).build();
    }
}
