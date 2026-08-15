package com.teammind.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.common.EventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 事件总线 — 事件分发的核心
 *
 * 职责：
 * 1. 接收来自各 Adapter / StateMachine 的标准事件
 * 2. 根据订阅关系分发到对应消费者
 * 3. 支持按 eventType / pluginId / taskId 过滤
 *
 * 设计原则：
 * - 同步调用消费者（不丢失事件）
 * - 异常隔离（一个消费者抛异常不影响其他）
 * - 支持批量订阅（按事件类型）
 */
@Slf4j
@Component
public class EventBus {

    private final ObjectMapper objectMapper;

    /** eventType → 消费者列表 */
    private final Map<EventType, List<Consumer<TeamMindEvent>>> eventSubscribers = new ConcurrentHashMap<>();

    /** subscriptionId → (eventType, consumer) 用于取消订阅 */
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private int subscriptionCounter = 0;

    public EventBus(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 订阅指定事件类型的消费者
     * @return subscriptionId，用于取消订阅
     */
    public String subscribe(EventType eventType, Consumer<TeamMindEvent> consumer) {
        eventSubscribers.computeIfAbsent(eventType, k -> new java.util.ArrayList<>()).add(consumer);
        String id = "sub-" + (++subscriptionCounter);
        subscriptions.put(id, new Subscription(eventType, consumer));
        log.debug("Subscribed to {}: subscriber={}", eventType, id);
        return id;
    }

    /**
     * 订阅多种事件类型
     */
    public String subscribe(Set<EventType> eventTypes, Consumer<TeamMindEvent> consumer) {
        String id = null;
        for (EventType type : eventTypes) {
            String subId = subscribe(type, consumer);
            if (id == null) id = subId;
        }
        return id;
    }

    /**
     * 取消订阅
     */
    public void unsubscribe(String subscriptionId) {
        Subscription sub = subscriptions.remove(subscriptionId);
        if (sub != null && eventSubscribers.containsKey(sub.eventType())) {
            eventSubscribers.get(sub.eventType()).remove(sub.consumer());
        }
    }

    /**
     * 发布事件 — 同步调用所有订阅者
     */
    public void emit(TeamMindEvent event) {
        log.debug("Emitting event: type={} taskId={} pluginId={}",
                event.type(), event.taskId(), event.pluginId());

        List<Consumer<TeamMindEvent>> subscribers = eventSubscribers.get(event.type());
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No subscribers for event type: {}", event.type());
            return;
        }

        for (Consumer<TeamMindEvent> consumer : subscribers) {
            try {
                consumer.accept(event);
            } catch (Exception e) {
                log.error("Event consumer threw exception for type={}: {}",
                        event.type(), e.getMessage(), e);
            }
        }
    }

    /**
     * 批量发布 — 同一事件广播给多个 channel
     */
    public void emitBatch(List<TeamMindEvent> events) {
        for (TeamMindEvent event : events) {
            emit(event);
        }
    }

    /**
     * 检查是否有订阅者
     */
    public boolean hasSubscribers(EventType eventType) {
        List<Consumer<TeamMindEvent>> subs = eventSubscribers.get(eventType);
        return subs != null && !subs.isEmpty();
    }

    /**
     * 将事件序列化为 JSON 字符串（供 WebSocket 传输）
     */
    public String toJson(TeamMindEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", e.getMessage(), e);
            return "{\"error\":\"serialization_failed\",\"type\":\"" + event.type() + "\"}";
        }
    }

    /**
     * 获取订阅数统计（调试用）
     */
    public Map<EventType, Integer> getSubscriberCounts() {
        Map<EventType, int[]> counts = new ConcurrentHashMap<>();
        eventSubscribers.forEach((type, subs) -> counts.put(type, new int[]{subs.size()}));
        return counts.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue()[0]
                ));
    }

    private record Subscription(EventType eventType, Consumer<TeamMindEvent> consumer) {}
}
