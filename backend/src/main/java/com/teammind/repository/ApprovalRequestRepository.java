package com.teammind.repository;

import com.teammind.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, String> {
    List<ApprovalRequest> findByTaskId(String taskId);
    List<ApprovalRequest> findByResult(ApprovalRequest.ApprovalResult result);
    Optional<ApprovalRequest> findByIdAndResult(String id, ApprovalRequest.ApprovalResult result);
}
