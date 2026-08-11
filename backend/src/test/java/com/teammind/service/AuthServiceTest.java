package com.teammind.service;

import com.teammind.auth.JwtUtil;
import com.teammind.dto.AuthResponse;
import com.teammind.entity.User;
import com.teammind.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试
 *
 * 覆盖登录成功、密码错误、用户禁用、用户不存在、
 * token 解析（含 Bearer 截断语义）等认证安全场景。
 */
class AuthServiceTest {

    private UserRepository userRepository;
    private JwtUtil jwtUtil;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtUtil = mock(JwtUtil.class);
        authService = new AuthService(userRepository, jwtUtil);
    }

    private User buildUser() {
        return User.builder()
                .id("u-1")
                .username("admin")
                .password("admin123")
                .email("admin@teammind.dev")
                .roles(List.of("ADMIN"))
                .permissions(List.of("read:code", "write:text"))
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("登录成功应签发包含用户信息的 token")
    void login_success_issuesToken() {
        User user = buildUser();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(
                eq("u-1"), eq("admin"), eq(List.of("ADMIN")), eq(List.of("read:code", "write:text"))))
                .thenReturn("jwt-token");

        AuthResponse response = authService.login("admin", "admin123");

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("u-1", response.getUserId());
        assertEquals("admin", response.getUsername());
        assertEquals("admin@teammind.dev", response.getEmail());
        assertEquals(List.of("ADMIN"), response.getRoles());
        assertEquals(List.of("read:code", "write:text"), response.getPermissions());
        assertEquals(86400L, response.getExpiresIn());

        verify(jwtUtil).generateToken(eq("u-1"), eq("admin"),
                eq(List.of("ADMIN")), eq(List.of("read:code", "write:text")));
    }

    @Test
    @DisplayName("密码错误应抛出异常且不签发 token")
    void login_wrongPassword_throws() {
        User user = buildUser();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.login("admin", "wrong-password"));
        assertTrue(ex.getMessage().toLowerCase().contains("invalid"));
        verify(jwtUtil, never()).generateToken(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("用户不存在应抛出异常")
    void login_unknownUser_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.login("ghost", "whatever"));
        assertTrue(ex.getMessage().toLowerCase().contains("invalid"));
        verify(jwtUtil, never()).generateToken(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("被禁用的用户应拒绝登录")
    void login_disabledUser_throws() {
        User user = buildUser();
        user.setEnabled(false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.login("admin", "admin123"));
        assertTrue(ex.getMessage().toLowerCase().contains("disabled"));
        verify(jwtUtil, never()).generateToken(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("roles/permissions 为 null 时登录应签发空列表的 token")
    void login_nullRolesPermissions_fallsBackToEmptyList() {
        User user = buildUser();
        user.setRoles(null);
        user.setPermissions(null);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(eq("u-1"), eq("admin"), eq(List.of()), eq(List.of())))
                .thenReturn("jwt-token");

        AuthResponse response = authService.login("admin", "admin123");

        assertEquals(List.of(), response.getRoles());
        assertEquals(List.of(), response.getPermissions());
        verify(jwtUtil).generateToken(eq("u-1"), eq("admin"), eq(List.of()), eq(List.of()));
    }

    @Test
    @DisplayName("resolveToken 应透传 JwtUtil 的解析结果")
    void resolveToken_passesThroughJwtUtil() {
        when(jwtUtil.parseToken("valid-token")).thenReturn(Map.of("sub", "u-1"));

        Map<String, Object> result = authService.resolveToken("valid-token");
        assertNotNull(result);
        assertEquals("u-1", result.get("sub"));
    }

    @Test
    @DisplayName("非法 token 解析返回 null")
    void resolveToken_invalidToken_returnsNull() {
        when(jwtUtil.parseToken("bad-token")).thenReturn(null);

        assertNull(authService.resolveToken("bad-token"));
    }
}
