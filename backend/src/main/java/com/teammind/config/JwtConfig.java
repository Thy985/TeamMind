package com.teammind.config;

import com.teammind.auth.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 认证配置
 */
@Configuration
public class JwtConfig {

    @Value("${teammind.security.jwt-secret:teammind-change-me-secret}")
    private String jwtSecret;

    @Value("${teammind.security.jwt-expiration-seconds:86400}")
    private long expirationSeconds;

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(jwtSecret, expirationSeconds);
    }
}
