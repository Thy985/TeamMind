package com.teammind.repository;

import com.teammind.entity.Agent;
import com.teammind.entity.Agent.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Agent Repository
 */
@Repository
public interface AgentRepository extends JpaRepository<Agent, String> {

    List<Agent> findByStatus(AgentStatus status);

    List<Agent> findByInstalledTrue();

    List<Agent> findByInstalledTrueAndEnabledTrue();

    List<Agent> findByAuthor(String author);

    List<Agent> findByOrderByDownloadCountDesc();

    List<Agent> findByOrderByEvolutionScoreDesc();
}
