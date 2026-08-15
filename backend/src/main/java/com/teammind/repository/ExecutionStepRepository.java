package com.teammind.repository;

import com.teammind.entity.ExecutionStep;
import com.teammind.common.ExecutionStepState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionStepRepository extends JpaRepository<ExecutionStep, String> {
    List<ExecutionStep> findByExecutionIdOrderByStartedAtAsc(String executionId);
    List<ExecutionStep> findByExecutionIdAndState(String executionId, ExecutionStepState state);
}
