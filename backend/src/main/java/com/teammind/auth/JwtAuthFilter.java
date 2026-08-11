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
import java.util.Set;

/**
 * JWT 认证过滤器
 *
 * 拦截所有请求，解析 Authorization Bearer token，
 * 将当前用户写入 AuthContext（ThreadLocal），请求结束时清除。
 *
 * 采用"强制认证"策略：
 *  - 除白名单（如登录）外，所有 /api/** 业务接口都要求携带有效 Bearer token，
 *    否则返回 401。
 *  - 携带有效 token → 设置认证用户并放行。
 *  - 未携带 token / token 非法 / 用户被禁用 → 拒绝（401）。
 *
 * 这样后端不再"形同虚设"，前端路由守卫无法被 curl 绕过。
 */
@Slf4j
@Component
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
