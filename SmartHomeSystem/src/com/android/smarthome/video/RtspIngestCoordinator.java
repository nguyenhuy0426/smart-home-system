/*
 * Responsibility: placeholder coordinator for camera RTSP ingest and stream
 * status tracking on the gateway.
 */
package com.android.smarthome.video;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RtspIngestCoordinator {
    private final RtspFrameSource frameSource;
    private final MotionSnapshotPipeline pipeline;
    private final Deque<MotionSnapshotPipeline.Frame> frameRing = new ArrayDeque<>();
    private final int ringSize;
    private boolean running;

    public RtspIngestCoordinator(RtspFrameSource frameSource,
            MotionSnapshotPipeline pipeline, int ringSize) {
        this.frameSource = frameSource;
        this.pipeline = pipeline;
        this.ringSize = ringSize > 0 ? ringSize : 5;
    }

    public List<MotionSnapshotPipeline.SnapshotDecision> runOnce() throws IOException {
        running = true;
        List<MotionSnapshotPipeline.SnapshotDecision> decisions = new ArrayList<>();
        MotionSnapshotPipeline.Frame frame;
        while ((frame = frameSource.nextFrame()) != null) {
            frameRing.addLast(frame);
            while (frameRing.size() > ringSize) {
                frameRing.removeFirst();
            }
            decisions.add(pipeline.accept(frame));
        }
        running = false;
        return decisions;
    }

    public boolean isRunning() {
        return running;
    }

    public int ringCount() {
        return frameRing.size();
    }

    public interface RtspFrameSource {
        MotionSnapshotPipeline.Frame nextFrame() throws IOException;
    }

    public static final class ScriptedRtspFrameSource implements RtspFrameSource {
        private final List<MotionSnapshotPipeline.Frame> frames;
        private int index;

        public ScriptedRtspFrameSource(List<MotionSnapshotPipeline.Frame> frames) {
            this.frames = frames;
        }

        @Override
        public MotionSnapshotPipeline.Frame nextFrame() {
            if (index >= frames.size()) {
                return null;
            }
            return frames.get(index++);
        }
    }
}
