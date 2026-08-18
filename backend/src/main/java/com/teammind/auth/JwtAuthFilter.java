package com.teammind.auth;

import com.teammind.entity.User;
import com.teammind.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * JWT 认证过滤器
 *
 * 通过 SecurityConfig 条件注册，不使用 @Component。
 * teammind.security.enabled=true 时注册，否则不加载。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    /**
     * 无需认证即可访问的公开端点（method + path 前缀）。
     * 除登录外，默认全部业务接口均需认证。
     */
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "POST /api/auth/login"
    );

    /**
     * 是否属于公开端点（跳过认证）
     */
    private boolean isPublicEndpoint(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if (PUBLIC_ENDPOINTS.contains(method + " " + uri)) {
            return true;
        }
        // 放行 OPTIONS 预检请求（由 CORS 处理）
        return "OPTIONS".equalsIgnoreCase(method);
    }

    /**
     * 需要认证的路径前缀
     */
    private boolean isProtectedPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 仅保护 /api/** 业务接口
            if (isProtectedPath(request) && !isPublicEndpoint(request)) {
                String authorization = request.getHeader("Authorization");
                String token = null;
                if (authorization != null && authorization.startsWith("Bearer ")) {
                    token = authorization.substring(7);
                }

                Map<String, Object> payload = token == null ? null : jwtUtil.parseToken(token);
                if (payload == null || payload.get("sub") == null) {
                    // 未认证或 token 非法/过期 → 401
                    sendUnauthorized(response, "Missing or invalid token");
                    return;
                }

                String userId = String.valueOf(payload.get("sub"));
                User user = userRepository.findById(userId).orElse(null);
                if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
                    sendUnauthorized(response, "User not found or disabled");
                    return;
                }

                AuthContext.setUser(user);
            }

            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\""
                + message.replace("\"", "\\\"") + "\"}");
    }
}
