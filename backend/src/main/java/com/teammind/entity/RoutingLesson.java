package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * RoutingLesson 实体 — 自动提炼的路由经验
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "routing_lessons")
public class RoutingLesson {

    @Id
    @Column(name = "\"key\"", length = 64)
    private String key;

    @Column(nullable = false)
    private String projectId;

    /** 条件描述 */
    @Column(columnDefinition = "TEXT")
    private String condition;

    private String taskTypeId;
    private String role;
    private String pluginId;

    /** 置信度 0-1 */
    @Builder.Default
    private Double confidence = 0.5;

    /** 证据数量 */
    @Builder.Default
    private Integer evidenceCount = 0;

    private LocalDateTime learnedAt;
    private LocalDateTime lastUpdated;
}
