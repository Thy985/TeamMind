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

/**
 * TeamTemplate 团队模板实体
 * 
 * 预定义的 Agent 协作模板
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "templates")
public class TeamTemplate {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String icon;

    private String category;

    /**
     * 模板包含的 Agent ID 列表
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<String> agents;

    /**
     * 模板配置文件路径 (Markdown)
     */
    private String configPath;

    /**
     * 是否公开
     */
    @Builder.Default
    private Boolean isPublic = false;

    /**
     * 使用次数
     */
    @Builder.Default
    private Integer usageCount = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
