package com.teammind.repository;

import com.teammind.entity.EvolutionRecord;
import com.teammind.entity.EvolutionRecord.EvolutionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Evolution Record Repository
 */
@Repository
public interface EvolutionRecordRepository extends JpaRepository<EvolutionRecord, Long> {

    List<EvolutionRecord> findByAgentIdOrderByCreatedAtDesc(String agentId);

    Page<EvolutionRecord> findByAgentIdOrderByCreatedAtDesc(String agentId, Pageable pageable);

    Optional<EvolutionRecord> findFirstByAgentIdAndIsRolledBackFalseOrderByCreatedAtDesc(String agentId);

    Optional<EvolutionRecord> findFirstByAgentIdAndTypeAndIsRolledBackFalseOrderByCreatedAtDesc(String agentId, EvolutionType type);

    List<EvolutionRecord> findByType(EvolutionType type);

    List<EvolutionRecord> findByAgentIdAndTypeOrderByCreatedAtDesc(String agentId, EvolutionType type);

    List<EvolutionRecord> findByIsRolledBackFalseOrderByCreatedAtDesc();

    @Query("SELECT e FROM EvolutionRecord e WHERE e.agentId = ?1 AND e.toVersion = ?2")
    EvolutionRecord findByAgentIdAndVersion(String agentId, Integer version);

    @Query("SELECT MAX(e.toVersion) FROM EvolutionRecord e WHERE e.agentId = ?1")
    Integer findLatestVersion(String agentId);

    @Query("SELECT COUNT(e) FROM EvolutionRecord e WHERE e.agentId = ?1 AND e.type = ?2")
    long countByAgentIdAndType(String agentId, EvolutionType type);
}
