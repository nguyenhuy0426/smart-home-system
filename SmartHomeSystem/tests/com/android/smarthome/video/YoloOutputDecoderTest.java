package com.android.smarthome.video;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.FloatBuffer;
import java.util.List;

public final class YoloOutputDecoderTest {
    @Test
    public void decode_unletterboxesAndSuppressesOverlappingLowerScoreBox() {
        // Layout is channel-major [cx...][cy...][w...][h...][score...].
        FloatBuffer output = FloatBuffer.wrap(new float[] {
                320f, 322f,
                320f, 322f,
                128f, 128f,
                64f, 64f,
                0.9f, 0.7f,
        });

        List<YoloOutputDecoder.DecodedDetection> detections = YoloOutputDecoder.decode(
                output,
                new long[] {1, 5, 2},
                "Person",
                0.55f,
                0.45f,
                0.5f,
                0,
                160,
                1280,
                640);

        assertEquals(1, detections.size());
        YoloOutputDecoder.DecodedDetection detection = detections.get(0);
        assertEquals("Person", detection.className);
        assertEquals(512f, detection.left, 0.001f);
        assertEquals(256f, detection.top, 0.001f);
        assertEquals(768f, detection.right, 0.001f);
        assertEquals(384f, detection.bottom, 0.001f);
        assertEquals(0.9f, detection.confidence, 0.001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decode_rejectsUnexpectedModelShape() {
        YoloOutputDecoder.decode(
                FloatBuffer.allocate(6),
                new long[] {1, 6, 1},
                "Fall-Detected",
                0.55f,
                0.45f,
                1f,
                0,
                0,
                640,
                640);
    }
}
