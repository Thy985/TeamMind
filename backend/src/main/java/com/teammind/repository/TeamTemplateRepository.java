package com.teammind.repository;

import com.teammind.entity.TeamTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Template Repository
 */
@Repository
public interface TeamTemplateRepository extends JpaRepository<TeamTemplate, String> {

    List<TeamTemplate> findByCategory(String category);

    List<TeamTemplate> findByIsPublicTrue();

    List<TeamTemplate> findByIsPublicFalse();

    List<TeamTemplate> findByOrderByUsageCountDesc();

    // 使用原生 SQL 查询 JSON 字段
    @Query(value = "SELECT * FROM templates WHERE agents LIKE :agentId", nativeQuery = true)
    List<TeamTemplate> findByAgentIdNative(@Param("agentId") String agentId);
}
