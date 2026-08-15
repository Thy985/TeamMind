package com.teammind.entity;

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
 * Plugin 实体 — 注册的 Agent / Tool / Verifier / Memory / Integration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "plugins")
public class Plugin {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    private String vendor;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String version;

    /** 插件类型 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PluginType pluginType;

    /** 能力声明 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<String> capabilities;

    /** 设计哲学关键词 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<String> philosophies;

    /** 适合的角色 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<String> preferredRoles;

    /** 不适合的角色 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<String> weakRoles;

    /** 平均延迟 ms */
    private Long avgLatencyMs;

    /** 可靠性分数 0-1 */
    private Double reliabilityScore;

    /** 每次调用成本（美元） */
    private Double costPerInvocation;

    /** 是否启用 */
    @Builder.Default
    private Boolean enabled = true;

    /** 健康状态 */
    @Enumerated(EnumType.STRING)
    private HealthStatus healthStatus;

    private LocalDateTime installedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum PluginType {
        AGENT, TOOL, VERIFIER, MEMORY, INTEGRATION
    }

    public enum HealthStatus {
        HEALTHY, DEGRADED, UNHEALTHY, DOWN
    }
}
