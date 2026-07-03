/*
 * Responsibility: placeholder coordinator for node onboarding, nodeId
 * assignment, room mapping, and Wi-Fi credential delivery.
 * Per ARCHITECTURE.md §3, nodeId is assigned in step 4, before the
 * descriptor is requested in step 8 — identity and descriptor are separate.
 */
package com.android.smarthome.gateway;

import com.android.smarthome.security.GatewaySecurityPolicy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class NodeProvisioningCoordinator {
    private final DescriptorRegistry descriptorRegistry;
    private final GatewaySecurityPolicy.HomeSecretProvider homeSecretProvider;
    private final NodeIdentityStore identityStore;

    public NodeProvisioningCoordinator(DescriptorRegistry descriptorRegistry,
            GatewaySecurityPolicy.HomeSecretProvider homeSecretProvider) {
        this(descriptorRegistry, homeSecretProvider, new InMemoryNodeIdentityStore());
    }

    public NodeProvisioningCoordinator(DescriptorRegistry descriptorRegistry,
            GatewaySecurityPolicy.HomeSecretProvider homeSecretProvider,
            NodeIdentityStore identityStore) {
        this.descriptorRegistry = descriptorRegistry;
        this.homeSecretProvider = homeSecretProvider;
        this.identityStore = identityStore;
    }

    /**
     * Provisions a node. The descriptorJson contains only capability fields (§4).
     * Node Identity (nodeId, homeId, roomId) is managed separately here.
     */
    public synchronized ProvisionedNode provision(String homeId, String descriptorJson,
            String immutableHardwareId, String roomId, String placementLabel,
            String replacesNodeId) {
        requireIdentifier("homeId", homeId);
        requireIdentifier("roomId", roomId);
        if (immutableHardwareId == null || immutableHardwareId.isBlank()) {
            throw new IllegalArgumentException("immutableHardwareId is required");
        }
        if (placementLabel == null || placementLabel.isBlank()) {
            throw new IllegalArgumentException("placementLabel is required");
        }
        DescriptorRegistry.DescriptorRecord descriptor =
                descriptorRegistry.parseValidateAndStore(descriptorJson);

        String fingerprint = homeSecretProvider.hmacSha256(
                homeId, descriptor.nodeType + ":" + immutableHardwareId);

        ProvisionedNode existing = identityStore.get(homeId, fingerprint);
        String nodeId = existing == null
                ? "node_" + UUID.randomUUID().toString().replace("-", "")
                : existing.nodeId;

        ProvisionedNode node = new ProvisionedNode(
                nodeId,
                homeId,
                descriptor.nodeType,
                fingerprint,
                descriptor.schemaVersion,
                descriptor.descriptorHash,
                roomId,
                placementLabel,
                existing == null ? Instant.now().toString() : existing.installedAt,
                replacesNodeId == null ? "" : replacesNodeId,
                "pending_first_heartbeat");
        identityStore.save(node);
        return node;
    }

    public synchronized ProvisionedNode getByHardwareFingerprint(
            String homeId, String hardwareFingerprint) {
        return identityStore.get(homeId, hardwareFingerprint);
    }

    private static void requireIdentifier(String name, String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    public interface NodeIdentityStore {
        ProvisionedNode get(String homeId, String hardwareFingerprint);
        void save(ProvisionedNode node);
    }

    public static final class InMemoryNodeIdentityStore implements NodeIdentityStore {
        private final Map<String, ProvisionedNode> nodes = new LinkedHashMap<>();

        @Override
        public synchronized ProvisionedNode get(String homeId, String hardwareFingerprint) {
            return nodes.get(key(homeId, hardwareFingerprint));
        }

        @Override
        public synchronized void save(ProvisionedNode node) {
            Objects.requireNonNull(node, "node");
            nodes.put(key(node.homeId, node.hardwareFingerprint), node);
        }

        private static String key(String homeId, String hardwareFingerprint) {
            return homeId + "\n" + hardwareFingerprint;
        }
    }

    public static final class ProvisionedNode {
        public final String nodeId;
        public final String homeId;
        public final String nodeType;
        public final String hardwareFingerprint;
        public final int descriptorSchemaVersion;
        public final String descriptorHash;
        public final String roomId;
        public final String placementLabel;
        public final String installedAt;
        public final String replacesNodeId;
        public final String status;

        ProvisionedNode(String nodeId, String homeId, String nodeType, String hardwareFingerprint,
                int descriptorSchemaVersion, String descriptorHash, String roomId,
                String placementLabel, String installedAt, String replacesNodeId, String status) {
            this.nodeId = nodeId;
            this.homeId = homeId;
            this.nodeType = nodeType;
            this.hardwareFingerprint = hardwareFingerprint;
            this.descriptorSchemaVersion = descriptorSchemaVersion;
            this.descriptorHash = descriptorHash;
            this.roomId = roomId;
            this.placementLabel = placementLabel;
            this.installedAt = installedAt;
            this.replacesNodeId = replacesNodeId;
            this.status = status;
        }

        public Map<String, Object> toFirestoreDocument() {
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("nodeId", nodeId);
            document.put("homeId", homeId);
            document.put("nodeType", nodeType);
            document.put("hardwareFingerprint", hardwareFingerprint);
            document.put("descriptorSchemaVersion", descriptorSchemaVersion);
            document.put("descriptorHash", descriptorHash);
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("roomId", roomId);
            location.put("label", placementLabel);
            location.put("installedAt", installedAt);
            document.put("location", location);
            Map<String, Object> firmware = new LinkedHashMap<>();
            firmware.put("version", "0.0.0-stub");
            firmware.put("buildId", "stub");
            firmware.put("otaChannel", "stable");
            document.put("firmware", firmware);
            document.put("replacesNodeId", replacesNodeId);
            document.put("status", status);
            return document;
        }
    }
}
