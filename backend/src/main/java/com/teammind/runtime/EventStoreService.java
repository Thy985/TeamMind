package com.teammind.runtime;

import com.teammind.common.EventType;
import com.teammind.entity.RuntimeEvent;
import com.teammind.repository.RuntimeEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EventStoreService — Phase 1C-3: Persistent Event Store with Tiering
 *
 * 职责：
 *   1. 写入事件（自动推断 tier）
 *   2. 按 taskId / afterId 查询事件链
 *   3. COLD 事件定期归档到文件系统
 *   4. WARM 事件超过保留期后标记为 TRASH
 *   5. Event Replay 支持（WebSocket 断线重连）
 *
 * 事件分级策略：
 *   HOT  (永久): 生命周期事件、审批事件
 *   WARM (7天): 产物、验证、审查事件
 *   COLD (30天): 执行细节事件 → 归档到文件系统
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventStoreService {

    private final RuntimeEventRepository eventRepo;
    private final Path coldArchiveDir;

    public EventStoreService(RuntimeEventRepository eventRepo) {
        this.eventRepo = eventRepo;
        // Default: archive COLD events to ./data/events/cold/
        this.coldArchiveDir = Paths.get(System.getProperty("java.io.tmpdir"), "teammind-events", "cold");
        try {
            Files.createDirectories(coldArchiveDir);
        } catch (IOException e) {
            log.warn("Failed to create cold archive dir: {}", e.getMessage());
        }
    }

    /**
     * 写入事件（自动推断 tier）
     */
    @Transactional
    public RuntimeEvent write(EventType type, String taskId, String payload) {
        return write(type, taskId, null, null, null, null, payload);
    }

    /**
     * 写入事件（完整参数）
     */
    @Transactional
    public RuntimeEvent write(EventType type, String taskId, String executionId,
                               String stepId, String pluginId, String role, String payload) {
        RuntimeEvent.EventTier tier = RuntimeEvent.inferTier(type);
        RuntimeEvent event = RuntimeEvent.builder()
                .type(type)
                .taskId(taskId)
                .executionId(executionId)
                .stepId(stepId)
                .pluginId(pluginId)
                .role(role)
                .payload(payload)
                .createdAt(LocalDateTime.now())
                .tier(tier)
                .build();
        event = eventRepo.save(event);
        log.debug("Event written: id={} type={} tier={} task={}", event.getId(), type, tier, taskId);
        return event;
    }

    /**
     * 批量写入（事务内）
     */
    @Transactional
    public List<RuntimeEvent> writeBatch(List<RuntimeEvent> events) {
        return eventRepo.saveAll(events);
    }

    /**
     * 按 taskId 获取完整事件链（用于 replay）
     */
    public List<RuntimeEvent> getEventChain(String taskId) {
        return eventRepo.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    /**
     * 从指定 ID 之后获取事件（WebSocket reconnect 用）
     */
    public List<RuntimeEvent> getEventsAfter(Long afterId) {
        return eventRepo.findByIdAfterOrderByCreatedAtAsc(afterId);
    }

    /**
     * 按 taskId + afterId 获取事件
     */
    public List<RuntimeEvent> getEventsAfter(String taskId, Long afterId) {
        List<RuntimeEvent> all = eventRepo.findByTaskIdOrderByCreatedAtAsc(taskId);
        if (afterId == null || afterId <= 0) return all;
        return all.stream()
                .filter(e -> e.getId() > afterId)
                .toList();
    }

    /**
     * 归档 COLD 事件到文件系统（定时任务调用）
     * 保留 30 天，超过的标记为 TRASH
     */
    @Transactional
    public int archiveColdEvents() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minus(30, ChronoUnit.DAYS);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minus(7, ChronoUnit.DAYS);

        // 1. 标记超过 7 天的 WARM 事件为 TRASH
        List<RuntimeEvent> warmToTrash = eventRepo.findWarmEventsBefore(sevenDaysAgo);
        warmToTrash.forEach(e -> {
            e.setTier(RuntimeEvent.EventTier.TRASH);
            eventRepo.save(e);
        });
        log.info("Marked {} WARM events as TRASH", warmToTrash.size());

        // 2. 归档 COLD 事件
        List<RuntimeEvent> coldEvents = eventRepo.findColdEventsBefore(thirtyDaysAgo);
        int archived = 0;
        for (RuntimeEvent event : coldEvents) {
            try {
                String fileName = event.getId() + "-" + event.getType().name() + ".json";
                Path filePath = coldArchiveDir.resolve(fileName);
                Files.writeString(filePath, event.getPayload() != null ? event.getPayload() : "{}");
                event.setArchivedPath(filePath.toString());
                eventRepo.save(event);
                archived++;
            } catch (IOException e) {
                log.error("Failed to archive event {}: {}", event.getId(), e.getMessage());
            }
        }
        log.info("Archived {} COLD events to {}", archived, coldArchiveDir);
        return archived;
    }

    /**
     * 删除已归档的 COLD 事件（释放 DB 空间）
     */
    @Transactional
    public int deleteArchivedColdEvents() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minus(30, ChronoUnit.DAYS);
        List<RuntimeEvent> archived = eventRepo.findColdEventsBefore(thirtyDaysAgo);
        List<Long> ids = archived.stream().map(RuntimeEvent::getId).toList();
        if (!ids.isEmpty()) {
            eventRepo.deleteAllById(ids);
            log.info("Deleted {} archived COLD events", ids.size());
        }
        return ids.size();
    }

    /**
     * 获取事件统计
     */
    public Map<String, Long> getTierStats(String taskId) {
        List<RuntimeEvent> events = eventRepo.findByTaskIdOrderByCreatedAtAsc(taskId);
        return events.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        e -> e.getTier().name(),
                        java.util.stream.Collectors.counting()));
    }

    /**
     * 检查事件是否存在（用于 replay 校验）
     */
    public boolean eventExists(Long eventId) {
        return eventRepo.existsById(eventId);
    }
}
