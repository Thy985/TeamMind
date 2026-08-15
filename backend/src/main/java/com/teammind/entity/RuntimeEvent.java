package com.teammind.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * RuntimeEvent 实体 — 持久化事件存储
 *
 * 所有事件先写 DB 再广播，保证 crash 后不丢失。
 * 支持 event replay（断线重连补全）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "runtime_events")
public class RuntimeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // auto-increment, ordered

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.teammind.common.EventType type;

    @Column(nullable = false)
    private String taskId;

    private String executionId;
    private String stepId;
    private String pluginId;
    private String role;

    @Column(columnDefinition = "TEXT")
    private String payload;  // JSON

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
