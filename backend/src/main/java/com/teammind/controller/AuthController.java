package com.teammind.controller;

import com.teammind.dto.ApiResponse;
import com.teammind.dto.AuthResponse;
import com.teammind.dto.LoginRequest;
import com.teammind.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 *
 * 提供登录与当前用户信息查询。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    /**
     * 当前用户信息（需携带 Bearer token）
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("UNAUTHORIZED", "Missing or invalid token"));
        }
        String token = authorization.substring(7);
        Map<String, Object> payload = authService.resolveToken(token);
        if (payload == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("UNAUTHORIZED", "Invalid or expired token"));
        }
        return ResponseEntity.ok(ApiResponse.success(payload));
    }
}
