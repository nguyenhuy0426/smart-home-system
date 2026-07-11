package com.example.smart_home_mobile_app.data;

public final class AccessEvent {
    public final String eventId;
    public final String nodeId;
    public final String roomId;
    public final String result;
    public final String credentialType;
    public final long timestampEpochMs;

    public AccessEvent(String eventId, String nodeId, String roomId, String result,
                       String credentialType, long timestampEpochMs) {
        this.eventId = eventId;
        this.nodeId = nodeId;
        this.roomId = roomId;
        this.result = result;
        this.credentialType = credentialType;
        this.timestampEpochMs = timestampEpochMs;
    }
}
