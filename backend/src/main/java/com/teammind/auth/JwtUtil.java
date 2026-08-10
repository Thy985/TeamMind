package com.teammind.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量 JWT 工具（无外部依赖）
 *
 * 使用 JDK 内置 HMAC-SHA256 生成/解析 JWT（HS256 签名）。
 * Payload 中承载 userId、username、roles、permissions 与过期时间。
 */
public final class JwtUtil {

    private static final String HEADER_ALG = "HS256";
    private static final String HEADER_TYP = "JWT";

    private final String secret;
    private final long expirationSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtUtil(String secret, long expirationSeconds) {
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
    }

    /**
     * 生成 JWT
     */
    public String generateToken(String userId, String username,
                                java.util.List<String> roles,
                                java.util.List<String> permissions) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", HEADER_ALG);
        header.put("typ", HEADER_TYP);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId);
        payload.put("username", username);
        payload.put("roles", roles);
        payload.put("permissions", permissions);
        long now = Instant.now().getEpochSecond();
        payload.put("iat", now);
        payload.put("exp", now + expirationSeconds);

        try {
            String encodedHeader = base64Url(objectMapper.writeValueAsBytes(header));
            String encodedPayload = base64Url(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = sign(signingInput);
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT", e);
        }
    }

    /**
     * 解析并校验 JWT，返回 payload；非法/过期返回 null
     */
    public Map<String, Object> parseToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = sign(signingInput);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            return null;
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(
                    payloadBytes, new TypeReference<LinkedHashMap<String, Object>>() {});
            Object exp = payload.get("exp");
            if (exp instanceof Number && ((Number) exp).longValue() < Instant.now().getEpochSecond()) {
                return null;
            }
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * HMAC-SHA256 签名
     */
    private String sign(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] raw = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return base64Url(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    private String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
