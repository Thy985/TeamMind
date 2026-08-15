package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ApprovalRequest 实体 — 用户审批请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String taskId;

    @Column(nullable = false)
    private String pluginId;

    private String role;

    @Column(columnDefinition = "TEXT")
    private String question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> context;

    /** 审批结果 */
    @Enumerated(EnumType.STRING)
    private ApprovalResult result;

    /** 审批人 */
    private String approvedBy;

    private Long timeoutMs;
    private LocalDateTime expiresAt;
    private LocalDateTime respondedAt;
    private LocalDateTime createdAt;

    public enum ApprovalResult {
        PENDING, GRANTED, DENIED, AUTO_APPROVED, AUTO_DENIED, TIMEOUT
    }
}
