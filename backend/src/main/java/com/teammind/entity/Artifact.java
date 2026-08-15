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
 * Artifact 实体 — Agent 产出的结构化产物
 *
 * 类型：CODE_DIFF / REVIEW_FINDINGS / IMPLEMENTATION_PLAN / EVIDENCE
 * 每个 Artifact 绑定到一个 AgentInvocation。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "artifacts")
public class Artifact {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String invocationId;  // FK → AgentInvocation.id

    @Column(nullable = false)
    private String type;          // "CODE_DIFF" / "REVIEW_FINDINGS" / "IMPLEMENTATION_PLAN"

    @Column(length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private java.util.Map<String, Object> data;

    private LocalDateTime createdAt;
}
