package com.android.smarthome.video;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.FloatBuffer;
import java.util.List;

public final class YoloOutputDecoderTest {
    private static final float[] DEFAULT_THRESHOLDS = {0.25f, 0.25f};

    @Test
    public void decode_unletterboxesEnd2EndRowsAndDropsZeroPadding() {
        // End2end layout is row-major [x1, y1, x2, y2, confidence, classId] per detection.
        // Source frame 1280x640 letterboxed into 640x640: scale 0.5, padLeft 0, padTop 160.
        FloatBuffer output = FloatBuffer.wrap(new float[] {
                256f, 288f, 384f, 352f, 0.9f, 1f,
                0f, 0f, 0f, 0f, 0f, 0f, // zero padding row emitted by the embedded NMS
        });

        List<YoloOutputDecoder.DecodedDetection> detections = YoloOutputDecoder.decode(
                output,
                new long[] {1, 2, 6},
                DEFAULT_THRESHOLDS,
                0.5f,
                0,
                160,
                1280,
                640);

        assertEquals(1, detections.size());
        YoloOutputDecoder.DecodedDetection detection = detections.get(0);
        assertEquals(YoloOutputDecoder.CLASS_ID_PERSON, detection.classId);
        assertEquals(YoloOutputDecoder.CLASS_NAME_PERSON, detection.className);
        assertEquals(512f, detection.left, 0.001f);
        assertEquals(256f, detection.top, 0.001f);
        assertEquals(768f, detection.right, 0.001f);
        assertEquals(384f, detection.bottom, 0.001f);
        assertEquals(0.9f, detection.confidence, 0.001f);
    }

    @Test
    public void decode_mapsFallenClassAndAppliesPerClassThresholds() {
        FloatBuffer output = FloatBuffer.wrap(new float[] {
                10f, 10f, 50f, 50f, 0.3f, 0f, // fallen, above its 0.25 threshold
                60f, 60f, 90f, 90f, 0.3f, 1f, // person, below its 0.5 threshold
        });

        List<YoloOutputDecoder.DecodedDetection> detections = YoloOutputDecoder.decode(
                output,
                new long[] {1, 2, 6},
                new float[] {0.25f, 0.5f},
                1f,
                0,
                0,
                640,
                640);

        assertEquals(1, detections.size());
        assertEquals(YoloOutputDecoder.CLASS_ID_FALLEN, detections.get(0).classId);
        assertEquals(YoloOutputDecoder.CLASS_NAME_FALLEN, detections.get(0).className);
    }

    @Test
    public void decode_skipsUnknownNonIntegerAndNonFiniteRows() {
        FloatBuffer output = FloatBuffer.wrap(new float[] {
                10f, 10f, 50f, 50f, 0.9f, 2f, // class id outside the trained set
                10f, 10f, 50f, 50f, 0.9f, 0.5f, // non-integer class id
                10f, 10f, 50f, 50f, Float.NaN, 1f, // non-finite confidence
                10f, 10f, Float.POSITIVE_INFINITY, 50f, 0.9f, 1f, // non-finite coordinate
                10f, 10f, 50f, 50f, 0.9f, 1f, // valid person row
        });

        List<YoloOutputDecoder.DecodedDetection> detections = YoloOutputDecoder.decode(
                output,
                new long[] {1, 5, 6},
                DEFAULT_THRESHOLDS,
                1f,
                0,
                0,
                640,
                640);

        assertEquals(1, detections.size());
        assertEquals(YoloOutputDecoder.CLASS_ID_PERSON, detections.get(0).classId);
    }

    @Test
    public void decode_clampsToFrameAndDropsDegenerateBoxes() {
        FloatBuffer output = FloatBuffer.wrap(new float[] {
                -50f, -20f, 150f, 120f, 0.9f, 1f, // spills over every edge
                -50f, -50f, -10f, -10f, 0.9f, 0f, // entirely outside -> degenerate after clamp
        });

        List<YoloOutputDecoder.DecodedDetection> detections = YoloOutputDecoder.decode(
                output,
                new long[] {1, 2, 6},
                DEFAULT_THRESHOLDS,
                1f,
                0,
                0,
                100,
                100);

        assertEquals(1, detections.size());
        YoloOutputDecoder.DecodedDetection detection = detections.get(0);
        assertEquals(YoloOutputDecoder.CLASS_ID_PERSON, detection.classId);
        assertEquals(0f, detection.left, 0.001f);
        assertEquals(0f, detection.top, 0.001f);
        assertEquals(100f, detection.right, 0.001f);
        assertEquals(100f, detection.bottom, 0.001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decode_rejectsLegacyRawHeadShape() {
        // The pre-end2end [1, 5, boxes] head must be refused instead of misdecoded.
        YoloOutputDecoder.decode(
                FloatBuffer.allocate(10),
                new long[] {1, 5, 2},
                DEFAULT_THRESHOLDS,
                1f,
                0,
                0,
                640,
                640);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decode_rejectsZeroThresholdThatWouldPassPaddingRows() {
        YoloOutputDecoder.decode(
                FloatBuffer.allocate(6),
                new long[] {1, 1, 6},
                new float[] {0f, 0.25f},
                1f,
                0,
                0,
                640,
                640);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decode_rejectsUndersizedBuffer() {
        YoloOutputDecoder.decode(
                FloatBuffer.allocate(6),
                new long[] {1, 2, 6},
                DEFAULT_THRESHOLDS,
                1f,
                0,
                0,
                640,
                640);
    }
}
