package com.teammind.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户实体
 *
 * 用于登录/JWT 认证。password 为演示用途的 BCrypt/明文，
 * 生产环境应使用安全哈希存储。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /**
     * 密码（演示环境为明文，生产环境应使用 BCrypt 等安全哈希）
     */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 128)
    private String email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<String> roles;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "TEXT")
    private List<String> permissions;

    @Builder.Default
    private Boolean enabled = true;

    private LocalDateTime createdAt;
}
