/*
 * Responsibility: models a provisioned node summary with identity, location,
 * firmware, descriptor hash, and online status.
 */
package com.example.smart_home_mobile_app.model;

public final class NodeSummary {
    public final String nodeId;
    public final String homeId;
    public final String nodeType;
    public final String roomId;
    public final String label;
    public final String descriptorHash;
    public final String status;

    public NodeSummary(String nodeId, String homeId, String nodeType, String roomId,
            String label, String descriptorHash, String status) {
        this.nodeId = nodeId;
        this.homeId = homeId;
        this.nodeType = nodeType;
        this.roomId = roomId;
        this.label = label;
        this.descriptorHash = descriptorHash;
        this.status = status;
    }
}
