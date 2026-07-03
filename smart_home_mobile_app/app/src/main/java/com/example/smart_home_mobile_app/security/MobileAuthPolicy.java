/*
 * Responsibility: placeholder policy boundary for Firebase Auth roles, command
 * authorization, and sensitive event visibility.
 */
package com.example.smart_home_mobile_app.security;

public final class MobileAuthPolicy {
    public static final String ROLE_HOME_MEMBER = "home_member";
    public static final String ROLE_ACCESS_ADMIN = "access_admin";
    public static final String ROLE_DEVICE_ADMIN = "device_admin";

    private MobileAuthPolicy() {
    }

    public static boolean canReadHome(String role) {
        return ROLE_HOME_MEMBER.equals(role) ||
                ROLE_ACCESS_ADMIN.equals(role) ||
                ROLE_DEVICE_ADMIN.equals(role);
    }

    public static boolean canRequestCommand(String role) {
        return canReadHome(role);
    }

    public static boolean canViewSensitiveAccessEvents(String role) {
        return ROLE_ACCESS_ADMIN.equals(role);
    }
}
