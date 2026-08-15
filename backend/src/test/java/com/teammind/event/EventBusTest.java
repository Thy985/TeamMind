package com.teammind.event;

import com.teammind.common.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    @Test
    @DisplayName("emit 事件后订阅者应收到事件")
    void shouldDeliverEventToSubscriber() {
        EventBus bus = new EventBus(new com.fasterxml.jackson.databind.ObjectMapper());
        java.util.concurrent.atomic.AtomicBoolean received = new java.util.concurrent.atomic.AtomicBoolean(false);

        String subId = bus.subscribe(EventType.AGENT_CHUNK, event -> {
            received.set(true);
            assertEquals("t-1", event.taskId());
            assertEquals("codex", event.pluginId());
        });

        bus.emit(TeamMindEvent.of(EventType.AGENT_CHUNK, "t-1", "codex", "LEAD",
                Map.of("content", "hello")));

        assertTrue(received.get(), "Subscriber should have received the event");
        bus.unsubscribe(subId);
    }

    @Test
    @DisplayName("未订阅的事件类型不应触发任何回调")
    void shouldNotTriggerUnsubscribedEventType() {
        EventBus bus = new EventBus(new com.fasterxml.jackson.databind.ObjectMapper());
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);

        bus.subscribe(EventType.TASK_STARTED, event -> callCount.incrementAndGet());
        bus.emit(TeamMindEvent.of(EventType.TASK_COMPLETED, "t-1", "system", "SYSTEM"));

        assertEquals(0, callCount.get(), "Handler for TASK_STARTED should not be called for TASK_COMPLETED");
    }

    @Test
    @DisplayName("消费者抛异常不应影响其他消费者")
    void shouldIsolateConsumerExceptions() {
        EventBus bus = new EventBus(new com.fasterxml.jackson.databind.ObjectMapper());
        java.util.concurrent.atomic.AtomicInteger goodCount = new java.util.concurrent.atomic.AtomicInteger(0);

        bus.subscribe(EventType.TOOL_CALLED, event -> {
            throw new RuntimeException("intentional failure");
        });
        bus.subscribe(EventType.TOOL_CALLED, event -> goodCount.incrementAndGet());

        assertDoesNotThrow(() ->
                bus.emit(TeamMindEvent.of(EventType.TOOL_CALLED, "t-1", "codex", "LEAD")));

        assertEquals(1, goodCount.get(), "Good subscriber should still be called");
    }

    @Test
    @DisplayName(" unsubscribe 后不应再收到事件")
    void shouldStopReceivingAfterUnsubscribe() {
        EventBus bus = new EventBus(new com.fasterxml.jackson.databind.ObjectMapper());
        java.util.concurrent.atomic.AtomicBoolean received = new java.util.concurrent.atomic.AtomicBoolean(false);

        String subId = bus.subscribe(EventType.TEST_RESULT, event -> received.set(true));
        bus.unsubscribe(subId);

        bus.emit(TeamMindEvent.of(EventType.TEST_RESULT, "t-1", "codex", "TESTER"));

        assertFalse(received.get(), "Should not receive events after unsubscribe");
    }

    @Test
    @DisplayName("toJson 应生成合法 JSON 字符串")
    void shouldSerializeToJson() throws Exception {
        EventBus bus = new EventBus(new com.fasterxml.jackson.databind.ObjectMapper());
        TeamMindEvent event = TeamMindEvent.of(EventType.AGENT_CHUNK, "t-1", "codex", "LEAD",
                Map.of("content", "test"));

        String json = bus.toJson(event);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"AGENT_CHUNK\""));
        assertTrue(json.contains("\"task_id\":\"t-1\""));
        assertTrue(json.contains("\"plugin_id\":\"codex\""));
    }

    @Test
    @DisplayName("of() 工厂方法应自动生成时间戳")
    void shouldAutoGenerateTimestamp() {
        TeamMindEvent event = TeamMindEvent.of(EventType.TASK_STARTED, "t-1", "codex", "LEAD");
        long now = System.currentTimeMillis();

        assertTrue(event.timestamp() >= now - 1000, "Timestamp should be recent");
        assertTrue(event.timestamp() <= now, "Timestamp should not be in the future");
        assertEquals(EventType.TASK_STARTED, event.type());
        assertNull(event.stepId());
    }
}
