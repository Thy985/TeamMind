package com.teammind.websocket;

import com.teammind.auth.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 鉴权拦截器
 *
 * 通过 SecurityConfig 条件注册，不使用 @Component。
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = extractBearerToken(accessor);
        if (token == null || jwtUtil.parseToken(token) == null) {
            log.warn("WebSocket CONNECT rejected: missing or invalid token");
            throw new RuntimeException("Missing or invalid token");
        }

        Map<String, Object> payload = jwtUtil.parseToken(token);
        String username = payload != null ? String.valueOf(payload.get("username")) : null;
        log.info("WebSocket authenticated: user={}", username);
        return message;
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            return null;
        }
        String auth = authHeaders.get(0);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return auth.substring(7);
    }
}
