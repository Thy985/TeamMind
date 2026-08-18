package com.teammind.config;

import com.teammind.auth.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 配置（可选）
 *
 * TeamMind 是本地开发者工具，默认不启用认证。
 * 如需启用远程访问认证，设置 TEAMMIND_JWT_SECRET 环境变量。
 */
@Slf4j
@Configuration
public class JwtConfig {

    @Value("${teammind.security.jwt-secret:}")
    private String jwtSecret;

    @Value("${teammind.security.jwt-expiration-seconds:86400}")
    private long expirationSeconds;

    @Bean
    public JwtUtil jwtUtil() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.info("JWT secret not set — authentication disabled (local mode)");
            return new JwtUtil("teammind-local-mode-no-auth-required-32chars!!", expirationSeconds);
        }
        log.info("JWT secret configured (length={} chars).", jwtSecret.length());
        return new JwtUtil(jwtSecret, expirationSeconds);
    }
}
