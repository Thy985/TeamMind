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
 * Mission 任务实体
 * 
 * 表示一个多 Agent 协作任务
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "missions")
public class Mission {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MissionStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    /**
     * 协作节点 - JSON 格式存储
     * 包含 Agent 节点、输入输出节点等
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<Map<String, Object>> nodes;

    /**
     * 节点连线 - JSON 格式存储
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<Map<String, Object>> edges;

    /**
     * 执行日志 - JSON 格式存储
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<Map<String, Object>> logs;

    /**
     * 执行结果 - JSON 格式存储
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> result;

    /**
     * 任务状态枚举
     */
    public enum MissionStatus {
        PENDING,    // 等待中
        RUNNING,    // 运行中
        COMPLETED,  // 已完成
        FAILED,     // 失败
        PAUSED      // 已暂停
    }
}
