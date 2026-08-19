package com.teammind.repository;

import com.teammind.entity.AgentPerformanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentPerformanceRecordRepository extends JpaRepository<AgentPerformanceRecord, Long> {
    List<AgentPerformanceRecord> findByProjectIdAndAgentId(String projectId, String agentId);
    List<AgentPerformanceRecord> findByProjectId(String projectId);
}
