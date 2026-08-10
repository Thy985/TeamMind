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
 * EvolutionRecord 进化记录实体
 * 
 * 记录智能体的进化历程，支持回滚和版本管理
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "evolution_records")
public class EvolutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的 Agent ID
     */
    @Column(nullable = false, length = 36)
    private String agentId;

    /**
     * 进化类型
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvolutionType type;

    /**
     * 进化前的版本号
     */
    private Integer fromVersion;

    /**
     * 进化后的版本号
     */
    private Integer toVersion;

    /**
     * 进化前的状态 (JSON)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> beforeState;

    /**
     * 进化后的状态 (JSON)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> afterState;

    /**
     * 进化描述 (Markdown)
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 进化原因
     */
    @Column(columnDefinition = "TEXT")
    private String reason;

    /**
     * 进化效果分数变化
     */
    private Double scoreChange;

    /**
     * 是否自动进化
     */
    @Builder.Default
    private Boolean isAutomatic = false;

    /**
     * 是否已回滚
     */
    @Builder.Default
    private Boolean isRolledBack = false;

    private LocalDateTime createdAt;

    /**
     * 进化类型枚举
     */
    public enum EvolutionType {
        PROMPT_OPTIMIZATION,    // Prompt 优化
        TOOL_GENERATION,        // 工具生成
        TOPOLOGY_EVOLUTION,     // 协作拓扑进化
        PARAMETER_TUNING,       // 参数调优
        KNOWLEDGE_UPDATE        // 知识更新
    }
}
