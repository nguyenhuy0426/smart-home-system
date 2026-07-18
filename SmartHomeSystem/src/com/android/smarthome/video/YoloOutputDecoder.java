package com.android.smarthome.video;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-Java decoder for the bundled end2end YOLO26n person/fall model
 * ({@code smarthome_person_fall_yolo26n_end2end_640_cls0_fallen.onnx}).
 *
 * <p>The export embeds NMS (Ultralytics {@code end2end=True}), so the output is a fixed-size
 * tensor {@code [1, maxDetections, 6]} where each row is
 * {@code x1, y1, x2, y2, confidence, classId} in letterboxed 640x640 input pixels. Rows beyond
 * the number of real detections are zero padding and are dropped by the confidence threshold.
 * No host-side NMS or xywh conversion is applied.
 */
public final class YoloOutputDecoder {
    /** Class ids and names fixed by the training run (data.yaml: 0=person_fallen, 1=person). */
    public static final int CLASS_ID_FALLEN = 0;
    public static final int CLASS_ID_PERSON = 1;
    public static final String CLASS_NAME_FALLEN = "person_fallen";
    public static final String CLASS_NAME_PERSON = "person";
    public static final String[] CLASS_NAMES = {CLASS_NAME_FALLEN, CLASS_NAME_PERSON};

    private static final int VALUES_PER_DETECTION = 6;
    private static final float CLASS_ID_TOLERANCE = 0.001f;

    private YoloOutputDecoder() {
    }

    /**
     * @param values row-major output buffer of the end2end model
     * @param shape reported output shape, must be {@code [1, N, 6]} with {@code N > 0}
     * @param classConfidenceThresholds per-class minimum confidence, indexed by class id; every
     *     entry must be within (0, 1] so zero-padding rows can never pass
     * @param scale letterbox scale that mapped the source frame into the 640x640 input
     * @param padLeft letterbox left padding in input pixels
     * @param padTop letterbox top padding in input pixels
     * @param originalWidth source frame width in pixels
     * @param originalHeight source frame height in pixels
     */
    public static List<DecodedDetection> decode(
            FloatBuffer values,
            long[] shape,
            float[] classConfidenceThresholds,
            float scale,
            int padLeft,
            int padTop,
            int originalWidth,
            int originalHeight) {
        if (values == null || shape == null || shape.length != 3
                || shape[0] != 1L || shape[1] <= 0L || shape[2] != VALUES_PER_DETECTION) {
            throw new IllegalArgumentException(
                    "Expected end2end YOLO output [1, detections, 6], got "
                            + (shape == null ? "null" : Arrays.toString(shape)));
        }
        if (classConfidenceThresholds == null
                || classConfidenceThresholds.length != CLASS_NAMES.length) {
            throw new IllegalArgumentException(
                    "Expected one confidence threshold per class (" + CLASS_NAMES.length + ")");
        }
        for (float threshold : classConfidenceThresholds) {
            if (!Float.isFinite(threshold) || threshold <= 0f || threshold > 1f) {
                throw new IllegalArgumentException(
                        "Confidence thresholds must be within (0, 1]");
            }
        }
        if (!Float.isFinite(scale) || scale <= 0f || originalWidth <= 0 || originalHeight <= 0) {
            throw new IllegalArgumentException("Invalid letterbox or source dimensions");
        }

        int detectionCount = Math.toIntExact(shape[1]);
        int requiredValues = Math.multiplyExact(VALUES_PER_DETECTION, detectionCount);
        if (values.capacity() < requiredValues) {
            throw new IllegalArgumentException(
                    "Output buffer has " + values.capacity() + " values, expected " + requiredValues);
        }

        List<DecodedDetection> detections = new ArrayList<>();
        for (int row = 0; row < detectionCount; row++) {
            int base = row * VALUES_PER_DETECTION;
            float confidence = values.get(base + 4);
            float rawClassId = values.get(base + 5);
            if (!Float.isFinite(confidence) || !Float.isFinite(rawClassId)) {
                continue;
            }
            int classId = Math.round(rawClassId);
            if (Math.abs(rawClassId - classId) > CLASS_ID_TOLERANCE
                    || classId < 0 || classId >= CLASS_NAMES.length) {
                continue;
            }
            if (confidence < classConfidenceThresholds[classId]) {
                continue;
            }

            float x1 = values.get(base);
            float y1 = values.get(base + 1);
            float x2 = values.get(base + 2);
            float y2 = values.get(base + 3);
            if (!Float.isFinite(x1) || !Float.isFinite(y1)
                    || !Float.isFinite(x2) || !Float.isFinite(y2)) {
                continue;
            }

            float left = clamp((x1 - padLeft) / scale, 0f, originalWidth);
            float top = clamp((y1 - padTop) / scale, 0f, originalHeight);
            float right = clamp((x2 - padLeft) / scale, 0f, originalWidth);
            float bottom = clamp((y2 - padTop) / scale, 0f, originalHeight);
            if (right > left && bottom > top) {
                detections.add(new DecodedDetection(
                        classId, CLASS_NAMES[classId], confidence, left, top, right, bottom));
            }
        }
        return detections;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class DecodedDetection {
        public final int classId;
        public final String className;
        public final float confidence;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        DecodedDetection(int classId, String className, float confidence,
                float left, float top, float right, float bottom) {
            this.classId = classId;
            this.className = className;
            this.confidence = confidence;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
