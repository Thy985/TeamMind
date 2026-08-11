package com.teammind.config;

import com.teammind.auth.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 认证配置
 *
 * 安全要求：必须通过环境变量 TEAMMIND_JWT_SECRET 注入一个足够长的强随机密钥。
 * 源码中不再提供任何默认/占位密钥，弱密钥或缺失时应用将拒绝启动（fail-closed）。
 */
@Slf4j
@Configuration
public class JwtConfig {

    @Value("${teammind.security.jwt-secret:}")
    private String jwtSecret;

    @Value("${teammind.security.jwt-expiration-seconds:86400}")
    private long expirationSeconds;

    @PostConstruct
    public void validateSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "TEAMMIND_JWT_SECRET is not set. Refusing to start with an empty JWT secret.");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "TEAMMIND_JWT_SECRET must be at least 32 characters. Refusing to start.");
        }
        // 拒绝已知的开发占位密钥
        if ("teammind-change-me-secret".equals(jwtSecret)
                || "teammind-dev-secret-change-in-production".equals(jwtSecret)) {
            throw new IllegalStateException(
                    "TEAMMIND_JWT_SECRET is using a known default value. Refusing to start.");
        }
        log.info("JWT secret validated (length={} chars).", jwtSecret.length());
    }

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(jwtSecret, expirationSeconds);
    }
}
