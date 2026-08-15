package com.teammind.repository;

import com.teammind.entity.RuntimeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuntimeEventRepository extends JpaRepository<RuntimeEvent, Long> {
    List<RuntimeEvent> findByTaskIdOrderByCreatedAtAsc(String taskId);
    List<RuntimeEvent> findByIdAfterOrderByCreatedAtAsc(Long afterId);
    long countByTaskId(String taskId);
}
