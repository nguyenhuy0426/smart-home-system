/*
 * Responsibility: placeholder boundary for gateway-side motion detection,
 * snapshot generation, upload, and retention coordination.
 */
package com.android.smarthome.video;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MotionSnapshotPipeline {
    private final double threshold;
    private final int cooldownFrames;
    private final StorageWriter storageWriter;
    private final SnapshotMetadataWriter metadataWriter;
    private int framesSinceSnapshot;
    private boolean motionActive;

    public MotionSnapshotPipeline(double threshold, int cooldownFrames,
            StorageWriter storageWriter, SnapshotMetadataWriter metadataWriter) {
        this.threshold = threshold > 0.0 ? threshold : 0.55;
        this.cooldownFrames = cooldownFrames > 0 ? cooldownFrames : 3;
        this.framesSinceSnapshot = this.cooldownFrames;
        this.storageWriter = storageWriter;
        this.metadataWriter = metadataWriter;
    }

    public SnapshotDecision accept(Frame frame) throws IOException {
        boolean aboveThreshold = frame.diffScore >= threshold;
        if (!aboveThreshold) {
            motionActive = false;
            framesSinceSnapshot++;
            return SnapshotDecision.discard();
        }

        if (!motionActive || framesSinceSnapshot >= cooldownFrames) {
            motionActive = true;
            framesSinceSnapshot = 0;
            String snapshotId = "snap_" + frame.nodeId + "_" + frame.frameSequence;
            String storagePath = "homes/" + frame.homeId + "/videoSnapshots/" + snapshotId + ".jpg";
            storageWriter.upload(storagePath, frame.jpegBytes);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("snapshotId", snapshotId);
            metadata.put("nodeId", frame.nodeId);
            metadata.put("roomId", frame.roomId);
            metadata.put("eventType", "video.motion_snapshot");
            metadata.put("observedAt", frame.observedAt);
            metadata.put("storagePath", storagePath);
            metadata.put("contentType", "image/jpeg");
            metadata.put("widthPx", frame.widthPx);
            metadata.put("heightPx", frame.heightPx);
            Map<String, Object> motion = new LinkedHashMap<>();
            motion.put("algorithm", "frame_difference_stub");
            motion.put("score", frame.diffScore);
            motion.put("threshold", threshold);
            metadata.put("motion", motion);
            Map<String, Object> retention = new LinkedHashMap<>();
            retention.put("snapshotRetentionDays", 30);
            retention.put("metadataRetentionDays", 180);
            retention.put("configurable", true);
            metadata.put("retention", retention);
            metadataWriter.write(frame.homeId, snapshotId, metadata);
            return SnapshotDecision.snapshot(snapshotId, storagePath);
        }

        framesSinceSnapshot++;
        return SnapshotDecision.discard();
    }

    public interface StorageWriter {
        void upload(String storagePath, byte[] jpegBytes) throws IOException;
    }

    public interface SnapshotMetadataWriter {
        void write(String homeId, String snapshotId, Map<String, Object> metadata) throws IOException;
    }

    public static final class Frame {
        public final String homeId;
        public final String nodeId;
        public final String roomId;
        public final long frameSequence;
        public final double diffScore;
        public final int widthPx;
        public final int heightPx;
        public final String observedAt;
        public final byte[] jpegBytes;

        public Frame(String homeId, String nodeId, String roomId, long frameSequence,
                double diffScore, int widthPx, int heightPx, String observedAt, byte[] jpegBytes) {
            this.homeId = homeId;
            this.nodeId = nodeId;
            this.roomId = roomId;
            this.frameSequence = frameSequence;
            this.diffScore = diffScore;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.observedAt = observedAt;
            this.jpegBytes = jpegBytes == null ? new byte[0] : jpegBytes.clone();
        }
    }

    public static final class SnapshotDecision {
        public final boolean snapshot;
        public final String snapshotId;
        public final String storagePath;

        private SnapshotDecision(boolean snapshot, String snapshotId, String storagePath) {
            this.snapshot = snapshot;
            this.snapshotId = snapshotId;
            this.storagePath = storagePath;
        }

        static SnapshotDecision snapshot(String snapshotId, String storagePath) {
            return new SnapshotDecision(true, snapshotId, storagePath);
        }

        static SnapshotDecision discard() {
            return new SnapshotDecision(false, "", "");
        }
    }

    public static final class MockStorage implements StorageWriter {
        public final Map<String, byte[]> objects = new LinkedHashMap<>();

        @Override
        public void upload(String storagePath, byte[] jpegBytes) {
            objects.put(storagePath, jpegBytes.clone());
        }
    }

    public static final class MockSnapshotMetadataWriter implements SnapshotMetadataWriter {
        public final Map<String, Map<String, Object>> documents = new LinkedHashMap<>();

        @Override
        public void write(String homeId, String snapshotId, Map<String, Object> metadata) {
            documents.put("homes/" + homeId + "/videoSnapshots/" + snapshotId, metadata);
        }
    }
}
