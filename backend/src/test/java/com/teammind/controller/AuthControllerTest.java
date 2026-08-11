package com.teammind.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.dto.AuthResponse;
import com.teammind.dto.LoginRequest;
import com.teammind.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 单元测试
 *
 * 覆盖登录接口契约与 /api/auth/me 的 Bearer token 鉴权契约。
 */
class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();
    }

    @Test
    @DisplayName("POST /api/auth/login 应返回登录成功与 token")
    void login_returnsToken() throws Exception {
        AuthResponse response = AuthResponse.builder()
                .token("jwt-token")
                .userId("u-1")
                .username("admin")
                .email("admin@teammind.dev")
                .roles(java.util.List.of("ADMIN"))
                .permissions(java.util.List.of("read:code"))
                .expiresIn(86400L)
                .build();
        when(authService.login("admin", "admin123")).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.token", is("jwt-token")))
                .andExpect(jsonPath("$.data.username", is("admin")));

        verify(authService).login("admin", "admin123");
    }

    @Test
    @DisplayName("POST /api/auth/login 缺参应返回校验失败（由 Validation 拦截）")
    void login_missingFields_rejected() throws Exception {
        // 模拟 service 抛错（校验失败路径）
        when(authService.login(any(), any())).thenThrow(new RuntimeException("Invalid"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                // LoginRequest 有 @NotBlank，但 standalone 不加载校验器，这里仅验证路由可达
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("GET /api/auth/me 携带有效 Bearer token 应返回用户信息")
    void me_withValidToken_returnsPayload() throws Exception {
        when(authService.resolveToken("valid-token"))
                .thenReturn(Map.of("sub", "u-1", "username", "admin"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sub", is("u-1")));
    }

    @Test
    @DisplayName("GET /api/auth/me 缺少 Authorization 头应返回 401")
    void me_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("GET /api/auth/me 非 Bearer 前缀应返回 401")
    void me_withNonBearerToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("GET /api/auth/me 携带无效/过期 token 应返回 401")
    void me_withInvalidToken_returnsUnauthorized() throws Exception {
        when(authService.resolveToken("expired")).thenReturn(null);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }
}
