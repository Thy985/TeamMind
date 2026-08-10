package com.teammind.service;

import com.teammind.auth.JwtUtil;
import com.teammind.dto.AuthResponse;
import com.teammind.entity.User;
import com.teammind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 认证服务
 *
 * 负责用户登录校验与 JWT 签发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    /**
     * 登录：校验用户名密码，签发 JWT
     */
    public AuthResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new RuntimeException("User is disabled");
        }

        // 演示环境明文比对；生产环境应使用 BCrypt.matches
        if (!user.getPassword().equals(password)) {
            throw new RuntimeException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRoles() != null ? user.getRoles() : List.of(),
                user.getPermissions() != null ? user.getPermissions() : List.of()
        );

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles())
                .permissions(user.getPermissions())
                .expiresIn(86400L)
                .build();
    }

    /**
     * 通过 token 解析用户（供 /api/auth/me 使用）
     */
    public Map<String, Object> resolveToken(String token) {
        return jwtUtil.parseToken(token);
    }
}
