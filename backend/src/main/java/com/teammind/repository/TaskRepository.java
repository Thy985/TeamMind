package com.teammind.repository;

import com.teammind.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {
    List<Task> findByProjectIdOrderByCreatedAtDesc(String projectId);
    List<Task> findByState(com.teammind.common.TaskState state);
}
