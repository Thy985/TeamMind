package com.teammind.runtime;

import com.teammind.entity.RuntimeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * EventSourcingService — Phase 1C-3: Event Replay & State Reconstruction
 *
 * 职责：
 *   1. 从事件链重建聚合根状态
 *   2. 支持断线重连（fromEventId 之后补发）
 *   3. 验证事件完整性（no gaps）
 *
 * 使用场景：
 *   - WebSocket reconnect: client 发送 snapshotVersion=N，服务端返回 id>N 的所有事件
 *   - Crash recovery: 重放最近 N 个事件恢复内存状态
 *   - Audit trail: 完整事件历史不可篡改
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventSourcingService {

    private final EventStoreService eventStore;

    /**
     * Replay events for a task, starting after the given event ID.
     *
     * @param taskId    任务 ID
     * @param fromEventId 起始事件 ID（null 表示从头开始）
     * @return 事件列表（按时间顺序）
     */
    public List<RuntimeEvent> replay(String taskId, Long fromEventId) {
        List<RuntimeEvent> events = eventStore.getEventsAfter(taskId, fromEventId);
        log.debug("Replayed {} events for task {} (from={})", events.size(), taskId, fromEventId);
        return events;
    }

    /**
     * Get the last event ID for a task (for snapshot versioning)
     */
    public Long getLastEventId(String taskId) {
        return eventStore.getEventChain(taskId).stream()
                .map(RuntimeEvent::getId)
                .max(Long::compareTo)
                .orElse(0L);
    }

    /**
     * Validate event chain integrity (no gaps in IDs)
     *
     * @return true if chain is continuous
     */
    public boolean validateChainIntegrity(String taskId) {
        List<RuntimeEvent> events = eventStore.getEventChain(taskId);
        if (events.isEmpty()) return true;

        // Check for gaps
        for (int i = 1; i < events.size(); i++) {
            long expected = events.get(i - 1).getId() + 1;
            long actual = events.get(i).getId();
            if (actual != expected && actual > expected) {
                log.warn("Gap detected in event chain for task {}: expected {}, got {}",
                        taskId, expected, actual);
                return false;
            }
        }
        return true;
    }

    /**
     * Count events by type for a task
     */
    public java.util.Map<String, Long> countByType(String taskId) {
        return eventStore.getEventChain(taskId).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        e -> e.getType().name(),
                        java.util.stream.Collectors.counting()));
    }

    /**
     * Get events in a time range
     */
    public List<RuntimeEvent> getEventsInTimeRange(String taskId,
                                                    java.time.LocalDateTime start,
                                                    java.time.LocalDateTime end) {
        return eventStore.getEventChain(taskId).stream()
                .filter(e -> !e.getCreatedAt().isBefore(start) && !e.getCreatedAt().isAfter(end))
                .toList();
    }
}
