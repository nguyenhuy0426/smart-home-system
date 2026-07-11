package com.example.smart_home_mobile_app.data;

public final class DetectionEvent {
    public final String eventId;
    public final String cameraNodeId;
    public final String roomId;
    public final long timestampEpochMs;
    public final String className;
    public final double confidence;
    /** Nullable. */
    public final BoundingBox boundingBox;

    public DetectionEvent(String eventId, String cameraNodeId, String roomId, long timestampEpochMs,
                          String className, double confidence, BoundingBox boundingBox) {
        this.eventId = eventId;
        this.cameraNodeId = cameraNodeId;
        this.roomId = roomId;
        this.timestampEpochMs = timestampEpochMs;
        this.className = className;
        this.confidence = confidence;
        this.boundingBox = boundingBox;
    }
}
