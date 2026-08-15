package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Evidence 实体 — 独立验证的证据（有生命周期）
 *
 * 生命周期：CLAIMED → COLLECTED → VERIFIED
 *                                 ↘ INVALIDATED
 *
 * 与 AgentInvocation 绑定，支持 commit-level 时效性追踪。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "evidence")
public class Evidence {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String invocationId;  // FK → AgentInvocation.id

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.teammind.common.EvidenceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.teammind.common.EvidenceStatus status;

    @Column(length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private java.util.Map<String, Object> data;

    /** diff 基准 commit（用于失效检测） */
    private String baseCommit;

    /** 关联的 Artifact hash（artifact 变化时证据失效） */
    private String artifactHash;

    private LocalDateTime collectedAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime invalidatedAt;

    /** 导致失效的原因或 invocationId */
    private String invalidatedBy;
}
