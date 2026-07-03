/*
 * Responsibility: placeholder coordinator for BLE Mesh OTA manifest control
 * and Wi-Fi image transfer orchestration.
 */
package com.android.smarthome.ota;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OtaCoordinator {
    private final BleMeshAnnouncer meshAnnouncer;
    private final WifiImageTransport imageTransport;
    private final SignatureVerifier signatureVerifier;
    private final Map<String, UpdateState> statesByNodeId = new LinkedHashMap<>();

    public OtaCoordinator(BleMeshAnnouncer meshAnnouncer, WifiImageTransport imageTransport,
            SignatureVerifier signatureVerifier) {
        this.meshAnnouncer = meshAnnouncer;
        this.imageTransport = imageTransport;
        this.signatureVerifier = signatureVerifier;
    }

    public UpdateState apply(String nodeId, Manifest manifest, boolean bootHealthConfirmed)
            throws IOException {
        meshAnnouncer.announce(nodeId, manifest);
        byte[] image = imageTransport.download(manifest.imageUrl);
        String actualHash = "sha256:" + sha256Hex(image);

        if (!actualHash.equals(manifest.sha256)) {
            UpdateState state = UpdateState.rejected(nodeId, "hash_mismatch");
            statesByNodeId.put(nodeId, state);
            return state;
        }

        if (!signatureVerifier.verify(manifest, image)) {
            UpdateState state = UpdateState.rejected(nodeId, "signature_rejected");
            statesByNodeId.put(nodeId, state);
            return state;
        }

        UpdateState state = UpdateState.installPending(nodeId, manifest.toVersion);
        statesByNodeId.put(nodeId, state);
        if (bootHealthConfirmed) {
            state.status = "confirmed";
        } else {
            state.status = "rollback_signalled";
            state.rollbackReason = "boot_health_timeout";
        }
        return state;
    }

    public UpdateState stateFor(String nodeId) {
        return statesByNodeId.get(nodeId);
    }

    public interface BleMeshAnnouncer {
        void announce(String nodeId, Manifest manifest) throws IOException;
    }

    public interface WifiImageTransport {
        byte[] download(String imageUrl) throws IOException;
    }

    public interface SignatureVerifier {
        boolean verify(Manifest manifest, byte[] image);
    }

    public static final class Manifest {
        public final String targetNodeType;
        public final String hardwareRevision;
        public final String fromVersion;
        public final String toVersion;
        public final String imageUrl;
        public final String sha256;
        public final String signature;
        public final String rollbackPolicy;
        public final String minBatteryOrPowerState;

        public Manifest(String targetNodeType, String hardwareRevision, String fromVersion,
                String toVersion, String imageUrl, String sha256, String signature,
                String rollbackPolicy, String minBatteryOrPowerState) {
            this.targetNodeType = targetNodeType;
            this.hardwareRevision = hardwareRevision;
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.imageUrl = imageUrl;
            this.sha256 = sha256;
            this.signature = signature;
            this.rollbackPolicy = rollbackPolicy;
            this.minBatteryOrPowerState = minBatteryOrPowerState;
        }
    }

    public static final class UpdateState {
        public final String nodeId;
        public String status;
        public final String targetVersion;
        public String rollbackReason;

        private UpdateState(String nodeId, String status, String targetVersion, String rollbackReason) {
            this.nodeId = nodeId;
            this.status = status;
            this.targetVersion = targetVersion;
            this.rollbackReason = rollbackReason;
        }

        static UpdateState installPending(String nodeId, String targetVersion) {
            return new UpdateState(nodeId, "install_pending", targetVersion, "");
        }

        static UpdateState rejected(String nodeId, String reason) {
            return new UpdateState(nodeId, "rejected", "", reason);
        }
    }

    public static final class MockBleMeshAnnouncer implements BleMeshAnnouncer {
        public int announceCount;

        @Override
        public void announce(String nodeId, Manifest manifest) {
            announceCount++;
        }
    }

    public static final class MockWifiImageTransport implements WifiImageTransport {
        private final Map<String, byte[]> images = new LinkedHashMap<>();

        public void put(String imageUrl, byte[] image) {
            images.put(imageUrl, image.clone());
        }

        @Override
        public byte[] download(String imageUrl) throws IOException {
            byte[] image = images.get(imageUrl);
            if (image == null) {
                throw new IOException("image not found: " + imageUrl);
            }
            return image.clone();
        }
    }

    public static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input);
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256Hex(String input) {
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }
}
