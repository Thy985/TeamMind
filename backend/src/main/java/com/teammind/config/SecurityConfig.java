package com.teammind.config;

import com.teammind.auth.JwtAuthFilter;
import com.teammind.auth.JwtUtil;
import com.teammind.repository.UserRepository;
import com.teammind.websocket.WebSocketAuthChannelInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Security 配置 — Host Profile 控制
 *
 * teammind.security.enabled=true  → 注册 JWT Filter + WS Interceptor
 * teammind.security.enabled=false → 不注册（CLI Host / Test Host）
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "teammind.security.enabled", havingValue = "true")
public class SecurityConfig {

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        log.info("Security enabled: registering JwtAuthFilter");
        return new JwtAuthFilter(jwtUtil, userRepository);
    }

    @Bean
    public WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor(JwtUtil jwtUtil) {
        log.info("Security enabled: registering WebSocketAuthChannelInterceptor");
        return new WebSocketAuthChannelInterceptor(jwtUtil);
    }
}