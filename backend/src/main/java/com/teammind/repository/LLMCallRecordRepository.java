package com.teammind.repository;

import com.teammind.llm.LLMCallRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用记录 Repository
 */
@Repository
public interface LLMCallRecordRepository extends JpaRepository<LLMCallRecord, Long> {

    List<LLMCallRecord> findByProviderOrderByCreatedAtDesc(String provider);

    List<LLMCallRecord> findByAgentIdOrderByCreatedAtDesc(String agentId);

    List<LLMCallRecord> findByMissionIdOrderByCreatedAtDesc(String missionId);

    List<LLMCallRecord> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    @Query("SELECT SUM(r.totalTokens) FROM LLMCallRecord r WHERE r.success = true")
    Long getTotalTokens();

    @Query("SELECT SUM(r.estimatedCost) FROM LLMCallRecord r WHERE r.success = true")
    Double getTotalCost();

    @Query("SELECT COUNT(r) FROM LLMCallRecord r WHERE r.success = true")
    Long getSuccessfulCallCount();

    @Query("SELECT COUNT(r) FROM LLMCallRecord r WHERE r.success = false")
    Long getFailedCallCount();

    @Query("SELECT r.provider, COUNT(r), SUM(r.totalTokens), SUM(r.estimatedCost) " +
           "FROM LLMCallRecord r WHERE r.success = true GROUP BY r.provider")
    List<Object[]> getStatsByProvider();

    @Query("SELECT r.model, COUNT(r), SUM(r.totalTokens), SUM(r.estimatedCost) " +
           "FROM LLMCallRecord r WHERE r.success = true GROUP BY r.model")
    List<Object[]> getStatsByModel();

    @Query("SELECT AVG(r.latencyMs) FROM LLMCallRecord r WHERE r.success = true")
    Double getAverageLatency();

    @Query("SELECT SUM(r.totalTokens) FROM LLMCallRecord r " +
           "WHERE r.success = true AND r.createdAt >= :since")
    Long getTotalTokensSince(LocalDateTime since);

    @Query("SELECT SUM(r.estimatedCost) FROM LLMCallRecord r " +
           "WHERE r.success = true AND r.createdAt >= :since")
    Double getTotalCostSince(LocalDateTime since);
}
