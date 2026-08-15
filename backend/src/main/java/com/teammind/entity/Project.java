package com.teammind.entity;

import com.teammind.common.AgentRole;
import com.teammind.common.ControlMode;
import com.teammind.runtime.ProjectPolicy;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Project 实体 — TeamMind 的一等公民
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String rootPath;

    /** 团队配置 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> teamConfig;

    /** 项目治理规则 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> policy;

    /** 控制模式 */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ControlMode controlMode = ControlMode.SUPERVISED;

    /** 共享上下文 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> sharedState;

    /** Agent 表现档案 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> agentProfile;

    /** Team Profile 命名 */
    private String profileName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastRunAt;
}
