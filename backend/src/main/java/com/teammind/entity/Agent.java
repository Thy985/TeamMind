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
 * Agent 智能体实体
 * 
 * 支持自主进化的智能体定义
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "agents")
public class Agent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String icon;

    private String version;

    private String author;

    private Integer downloadCount;

    private Double rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStatus status;

    /**
     * 权限列表 - JSON 格式
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<String> permissions;

    /**
     * Agent 配置文件路径 (Markdown)
     */
    private String configPath;

    /**
     * 当前使用的 Prompt
     */
    @Column(columnDefinition = "TEXT")
    private String currentPrompt;

    /**
     * 原始 Prompt (用于对比进化效果)
     */
    @Column(columnDefinition = "TEXT")
    private String originalPrompt;

    /**
     * Agent 工具列表 - JSON 格式
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<Map<String, Object>> tools;

    /**
     * 进化版本号
     */
    @Builder.Default
    private Integer evolutionVersion = 1;

    /**
     * 进化分数 (用于评估进化效果)
     */
    private Double evolutionScore;

    /**
     * 是否安装
     */
    @Builder.Default
    private Boolean installed = false;

    /**
     * 是否启用
     */
    @Builder.Default
    private Boolean enabled = true;

    private LocalDateTime installedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 测试报告 - JSON 格式
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> testReport;

    /**
     * Agent 状态枚举
     */
    public enum AgentStatus {
        IDLE,       // 空闲
        RUNNING,    // 运行中
        SUCCESS,    // 成功
        ERROR,      // 错误
        WAITING     // 等待中
    }
}
