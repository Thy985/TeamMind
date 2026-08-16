package com.teammind.repository;

import com.teammind.entity.RuntimeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RuntimeEventRepository extends JpaRepository<RuntimeEvent, Long> {
    List<RuntimeEvent> findByTaskIdOrderByCreatedAtAsc(String taskId);
    List<RuntimeEvent> findByIdAfterOrderByCreatedAtAsc(Long afterId);
    long countByTaskId(String taskId);

    /** 按 tier 查询 */
    List<RuntimeEvent> findByTierOrderByCreatedAtAsc(RuntimeEvent.EventTier tier);

    /** 查询指定时间前的 COLD 事件（用于归档） */
    @Query("SELECT e FROM RuntimeEvent e WHERE e.tier = 'COLD' AND e.createdAt < :before")
    List<RuntimeEvent> findColdEventsBefore(@Param("before") LocalDateTime before);

    /** 查询 WARM 事件中超过保留期的（用于清理） */
    @Query("SELECT e FROM RuntimeEvent e WHERE e.tier = 'WARM' AND e.createdAt < :before")
    List<RuntimeEvent> findWarmEventsBefore(@Param("before") LocalDateTime before);

    /** 按 taskId + tier 查询 */
    List<RuntimeEvent> findByTaskIdAndTierOrderByCreatedAtAsc(String taskId, RuntimeEvent.EventTier tier);

    /** 按 type 查询 */
    List<RuntimeEvent> findByTypeOrderByCreatedAtAsc(com.teammind.common.EventType type);
}
