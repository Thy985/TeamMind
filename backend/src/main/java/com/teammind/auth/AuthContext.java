package com.teammind.auth;

import com.teammind.entity.User;

/**
 * 当前认证用户上下文
 *
 * 由 JwtAuthFilter 在请求进入时写入，请求结束时清除。
 * 用于在 Controller/Service 层获取当前登录用户及其权限。
 */
public final class AuthContext {

    private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUser(User user) {
        CURRENT_USER.set(user);
    }

    public static User getUser() {
        return CURRENT_USER.get();
    }

    public static String getUsername() {
        User user = CURRENT_USER.get();
        return user != null ? user.getUsername() : null;
    }

    public static boolean isAuthenticated() {
        return CURRENT_USER.get() != null;
    }

    public static boolean hasPermission(String permission) {
        User user = CURRENT_USER.get();
        return user != null && user.getPermissions() != null
                && user.getPermissions().contains(permission);
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
