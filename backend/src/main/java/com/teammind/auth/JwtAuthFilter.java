package com.teammind.auth;

import com.teammind.entity.User;
import com.teammind.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * JWT 认证过滤器
 *
 * 拦截所有请求，解析 Authorization Bearer token，
 * 将当前用户写入 AuthContext（ThreadLocal），请求结束时清除。
 *
 * 采用"可选认证"策略：
 *  - 携带有效 token → 设置认证用户
 *  - 未携带 token   → 保持匿名（不强制拒绝）
 * 具体端点/服务通过 AuthContext 决定是否需要强制认证或权限校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                String token = authorization.substring(7);
                Map<String, Object> payload = jwtUtil.parseToken(token);
                if (payload != null && payload.get("sub") != null) {
                    String userId = String.valueOf(payload.get("sub"));
                    userRepository.findById(userId).ifPresent(user -> {
                        if (Boolean.TRUE.equals(user.getEnabled())) {
                            AuthContext.setUser(user);
                        }
                    });
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }
}
