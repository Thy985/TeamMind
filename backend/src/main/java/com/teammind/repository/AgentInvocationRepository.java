package com.teammind.repository;

import com.teammind.entity.AgentInvocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentInvocationRepository extends JpaRepository<AgentInvocation, String> {
    List<AgentInvocation> findByStepId(String stepId);

    /** Recovery: 找出所有标记为 alive 的进程 */
    List<AgentInvocation> findByPidNotNullAndProcessAliveTrue();
}
