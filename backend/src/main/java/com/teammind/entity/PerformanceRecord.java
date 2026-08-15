package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * PerformanceRecord 实体 — Agent 表现记录
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "performance_records")
public class PerformanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String pluginId;

    /** 角色 */
    private String role;

    /** 任务类型 */
    private String taskTypeId;

    /** 成功率 0-1 */
    @Column(nullable = false)
    private Double successRate;

    /** 平均迭代次数 */
    private Double avgIterations;

    /** 平均耗时 ms */
    private Long avgDurationMs;

    /** 样本数 */
    @Builder.Default
    private Integer sampleSize = 0;

    /** 误报率（审查类任务） */
    private Double falsePositiveRate;

    /** 漏报率（审查类任务） */
    private Double missRate;

    /** 用户接受率 */
    private Double userAcceptanceRate;

    private LocalDateTime lastUpdated;
    private LocalDateTime createdAt;
}
