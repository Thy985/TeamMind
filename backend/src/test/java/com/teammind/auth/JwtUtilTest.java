package com.teammind.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 *
 * 覆盖 JWT 的签发、解析、过期、篡改、非法输入等认证安全场景。
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // 演示用固定 secret，短过期便于测试过期逻辑
        jwtUtil = new JwtUtil("test-secret-key-for-jwt-unit-test", 3600);
    }

    private String buildToken() {
        return jwtUtil.generateToken("u-1", "admin", List.of("ADMIN"), List.of("read:code", "write:text"));
    }

    @Test
    @DisplayName("签发的 token 应能被正确解析并还原 payload")
    void generateToken_roundTripsPayload() {
        String token = buildToken();

        Map<String, Object> payload = jwtUtil.parseToken(token);
        assertNotNull(payload);
        assertEquals("u-1", payload.get("sub"));
        assertEquals("admin", payload.get("username"));

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) payload.get("roles");
        assertTrue(roles.contains("ADMIN"));

        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) payload.get("permissions");
        assertTrue(permissions.contains("read:code"));
        assertTrue(permissions.contains("write:text"));

        // iat/exp 应为数字且 exp > iat
        assertTrue(payload.get("iat") instanceof Number);
        assertTrue(payload.get("exp") instanceof Number);
        long iat = ((Number) payload.get("iat")).longValue();
        long exp = ((Number) payload.get("exp")).longValue();
        assertEquals(3600, exp - iat);
    }

    @Test
    @DisplayName("token 应包含三段且格式正确")
    void generateToken_hasThreeSegments() {
        String token = buildToken();
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        // 每段均为 base64url，非空
        assertFalse(parts[0].isEmpty());
        assertFalse(parts[1].isEmpty());
        assertFalse(parts[2].isEmpty());
    }

    @Test
    @DisplayName("解析篡改后的 token 应返回 null（签名校验失败）")
    void parseToken_tamperedPayload_rejected() {
        String token = buildToken();
        // 篡改 payload 中的 sub，保留原始签名
        String[] parts = token.split("\\.");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"attacker\"}".getBytes());
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertNull(jwtUtil.parseToken(tampered));
    }

    @Test
    @DisplayName("解析篡改签名的 token 应返回 null")
    void parseToken_tamperedSignature_rejected() {
        String token = buildToken();
        String tampered = token.substring(0, token.length() - 1) + "x";
        assertNull(jwtUtil.parseToken(tampered));
    }

    @Test
    @DisplayName("使用不同 secret 签发的 token 无法被当前 secret 解析")
    void parseToken_wrongSecret_rejected() {
        JwtUtil other = new JwtUtil("another-secret-entirely", 3600);
        String token = other.generateToken("u-2", "bob", List.of(), List.of());
        // 用不同 secret 的 jwtUtil 解析
        assertNull(jwtUtil.parseToken(token));
    }

    @Test
    @DisplayName("过期的 token 应解析为 null")
    void parseToken_expired_rejected() throws InterruptedException {
        // 1 秒过期的 token
        JwtUtil shortLived = new JwtUtil("test-secret-key-for-jwt-unit-test", 1);
        String token = shortLived.generateToken("u-1", "admin", List.of(), List.of());

        Thread.sleep(1100);
        assertNull(shortLived.parseToken(token));
    }

    @Test
    @DisplayName("同一用户不同时刻签发的 token 应不同（含 iat）")
    void generateToken_isNotDeterministic() throws InterruptedException {
        String t1 = jwtUtil.generateToken("u-1", "admin", List.of(), List.of());
        Thread.sleep(20);
        String t2 = jwtUtil.generateToken("u-1", "admin", List.of(), List.of());
        assertNotEquals(t1, t2);
    }

    @Test
    @DisplayName("空或 null token 应返回 null")
    void parseToken_emptyOrNull_rejected() {
        assertNull(jwtUtil.parseToken(null));
        assertNull(jwtUtil.parseToken(""));
        assertNull(jwtUtil.parseToken("   "));
    }

    @Test
    @DisplayName("非三段结构的 token 应返回 null")
    void parseToken_malformed_segmentCount_rejected() {
        assertNull(jwtUtil.parseToken("abc.def"));
        assertNull(jwtUtil.parseToken("abc.def.ghi.jkl"));
    }

    @Test
    @DisplayName("非法 base64 的 payload 应返回 null")
    void parseToken_invalidBase64_rejected() {
        String token = buildToken();
        String[] parts = token.split("\\.");
        // 替换 payload 段为非法字符（保持签名不变，签名校验会先失败或解析失败）
        String malformed = parts[0] + ".%%%invalid%%%." + parts[2];
        assertNull(jwtUtil.parseToken(malformed));
    }

    @Test
    @DisplayName("roles/permissions 为 null 时应容忍并生成可解析 token")
    void generateToken_nullRolesPermissions_ok() {
        String token = jwtUtil.generateToken("u-9", "user", null, null);
        Map<String, Object> payload = jwtUtil.parseToken(token);
        assertNotNull(payload);
        assertEquals("u-9", payload.get("sub"));
    }
}
