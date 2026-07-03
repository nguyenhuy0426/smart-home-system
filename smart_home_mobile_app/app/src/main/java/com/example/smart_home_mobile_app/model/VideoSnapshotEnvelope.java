/*
 * Responsibility: models snapshot metadata and Firebase Storage references for
 * motion-triggered camera events.
 */
package com.example.smart_home_mobile_app.model;

public final class VideoSnapshotEnvelope {
    public final String snapshotId;
    public final String nodeId;
    public final String roomId;
    public final String storagePath;
    public final double motionScore;

    public VideoSnapshotEnvelope(String snapshotId, String nodeId, String roomId,
            String storagePath, double motionScore) {
        this.snapshotId = snapshotId;
        this.nodeId = nodeId;
        this.roomId = roomId;
        this.storagePath = storagePath;
        this.motionScore = motionScore;
    }
}
