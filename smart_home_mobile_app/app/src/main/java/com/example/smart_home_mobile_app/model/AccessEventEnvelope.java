/*
 * Responsibility: models access-control events without raw fingerprint
 * templates or raw RFID UIDs.
 */
package com.example.smart_home_mobile_app.model;

public final class AccessEventEnvelope {
    public final String eventId;
    public final String nodeId;
    public final String roomId;
    public final String result;
    public final String credentialKind;
    public final String hashedEnrollmentId;

    public AccessEventEnvelope(String eventId, String nodeId, String roomId,
            String result, String credentialKind, String hashedEnrollmentId) {
        if (hashedEnrollmentId == null || !hashedEnrollmentId.startsWith("sha256:")) {
            throw new IllegalArgumentException("access events must use salted hashes");
        }
        this.eventId = eventId;
        this.nodeId = nodeId;
        this.roomId = roomId;
        this.result = result;
        this.credentialKind = credentialKind;
        this.hashedEnrollmentId = hashedEnrollmentId;
    }
}
