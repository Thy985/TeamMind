package com.teammind.repository;

import com.teammind.entity.Evidence;
import com.teammind.common.EvidenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvidenceRepository extends JpaRepository<Evidence, String> {
    List<Evidence> findByInvocationId(String invocationId);
    List<Evidence> findByStatus(EvidenceStatus status);
}
