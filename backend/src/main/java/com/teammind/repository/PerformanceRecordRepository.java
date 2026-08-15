package com.teammind.repository;

import com.teammind.entity.PerformanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PerformanceRecordRepository extends JpaRepository<PerformanceRecord, Long> {
    List<PerformanceRecord> findByProjectId(String projectId);
    List<PerformanceRecord> findByProjectIdAndPluginId(String projectId, String pluginId);
    List<PerformanceRecord> findByProjectIdAndRole(String projectId, String role);
    List<PerformanceRecord> findByProjectIdAndPluginIdAndRole(String projectId, String pluginId, String role);
    Optional<PerformanceRecord> findFirstByProjectIdAndPluginIdAndRoleOrderByLastUpdatedDesc(String projectId, String pluginId, String role);
    Optional<PerformanceRecord> findByProjectIdAndPluginIdAndTaskTypeId(String projectId, String pluginId, String taskTypeId);
}
