/*
 * Responsibility: placeholder policy boundary for gateway authorization,
 * Firebase write ownership, credential privacy, and audit requirements.
 */
package com.android.smarthome.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class GatewaySecurityPolicy {
    public static final String ROLE_HOME_MEMBER = "home_member";
    public static final String ROLE_GATEWAY_SERVICE = "gateway_service";
    public static final String ROLE_ACCESS_ADMIN = "access_admin";
    public static final String ROLE_DEVICE_ADMIN = "device_admin";

    private GatewaySecurityPolicy() {
    }

    public static String hmacSha256Hex(String secret, String value) {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(value, "value");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("sha256:");
            for (byte b : bytes) {
                builder.append(String.format("%02x", b & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /**
     * Computes a hardware fingerprint using HMAC-SHA256.
     * The homeSecret is a per-home 256-bit random value stored in Android Keystore,
     * never synced to Firestore in plaintext. See ARCHITECTURE.md §9 Fixed Decisions.
     */
    public static String hardwareFingerprint(String homeSecret, String nodeType, String immutableHardwareId) {
        return hmacSha256Hex(homeSecret, nodeType + ":" + immutableHardwareId);
    }

    public static boolean canReadHome(String role) {
        return ROLE_HOME_MEMBER.equals(role) ||
                ROLE_GATEWAY_SERVICE.equals(role) ||
                ROLE_ACCESS_ADMIN.equals(role) ||
                ROLE_DEVICE_ADMIN.equals(role);
    }

    public static boolean canGatewayWrite(String role, String collection) {
        if (!ROLE_GATEWAY_SERVICE.equals(role)) {
            return false;
        }
        return "readings".equals(collection) ||
                "events".equals(collection) ||
                "descriptors".equals(collection) ||
                "videoSnapshots".equals(collection) ||
                "nodes".equals(collection) ||
                "otaStatus".equals(collection);
    }

    public static boolean canMobileWrite(String role, String collection) {
        return canReadHome(role) && "commandRequests".equals(collection);
    }

    public static boolean canControlAccess(String role) {
        return ROLE_ACCESS_ADMIN.equals(role) || ROLE_GATEWAY_SERVICE.equals(role);
    }

    /**
     * Provider interface for the per-home secret used in hardware fingerprinting.
     * Production: Android Keystore-backed. Test: deterministic mock.
     */
    public interface HomeSecretProvider {
        String hmacSha256(String homeId, String value);
    }

    /**
     * Test-only mock that returns a deterministic secret for test repeatability.
     */
    public static final class MockHomeSecretProvider implements HomeSecretProvider {
        private final String secret;

        public MockHomeSecretProvider(String secret) {
            this.secret = Objects.requireNonNull(secret);
        }

        @Override
        public String hmacSha256(String homeId, String value) {
            return GatewaySecurityPolicy.hmacSha256Hex(secret, value);
        }
    }
}
