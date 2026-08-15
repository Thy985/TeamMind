package com.teammind.repository;

import com.teammind.entity.TaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, String> {
    List<TaskExecution> findByProjectIdOrderByCreatedAtDesc(String projectId);
    List<TaskExecution> findByProjectIdAndStateOrderByCreatedAtDesc(String projectId, String state);
    long countByProjectId(String projectId);
    long countByProjectIdAndState(String projectId, String state);
}
