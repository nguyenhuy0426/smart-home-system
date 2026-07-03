package com.android.smarthome.video;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** Pure-Java decoder for the single-class, NMS-free YOLO outputs bundled with the gateway. */
public final class YoloOutputDecoder {
    private YoloOutputDecoder() {
    }

    public static List<DecodedDetection> decode(
            FloatBuffer values,
            long[] shape,
            String className,
            float confidenceThreshold,
            float nmsThreshold,
            float scale,
            int padLeft,
            int padTop,
            int originalWidth,
            int originalHeight) {
        if (values == null || shape == null || shape.length != 3
                || shape[0] != 1L || shape[1] != 5L || shape[2] <= 0L) {
            throw new IllegalArgumentException("Expected single-class YOLO output [1, 5, boxes]");
        }
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className must not be empty");
        }
        if (!Float.isFinite(scale) || scale <= 0f || originalWidth <= 0 || originalHeight <= 0) {
            throw new IllegalArgumentException("Invalid letterbox or source dimensions");
        }

        int boxCount = Math.toIntExact(shape[2]);
        int requiredValues = Math.multiplyExact(5, boxCount);
        if (values.capacity() < requiredValues) {
            throw new IllegalArgumentException(
                    "Output buffer has " + values.capacity() + " values, expected " + requiredValues);
        }

        List<DecodedDetection> candidates = new ArrayList<>();
        for (int boxIndex = 0; boxIndex < boxCount; boxIndex++) {
            float confidence = values.get(4 * boxCount + boxIndex);
            if (!Float.isFinite(confidence) || confidence < confidenceThreshold) {
                continue;
            }

            float centerX = values.get(boxIndex);
            float centerY = values.get(boxCount + boxIndex);
            float width = values.get(2 * boxCount + boxIndex);
            float height = values.get(3 * boxCount + boxIndex);
            if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                    || !Float.isFinite(width) || !Float.isFinite(height)
                    || width <= 0f || height <= 0f) {
                continue;
            }

            float left = clamp((centerX - width / 2f - padLeft) / scale, 0f, originalWidth);
            float top = clamp((centerY - height / 2f - padTop) / scale, 0f, originalHeight);
            float right = clamp((centerX + width / 2f - padLeft) / scale, 0f, originalWidth);
            float bottom = clamp((centerY + height / 2f - padTop) / scale, 0f, originalHeight);
            if (right > left && bottom > top) {
                candidates.add(new DecodedDetection(
                        className, confidence, left, top, right, bottom));
            }
        }
        return applyNms(candidates, nmsThreshold);
    }

    private static List<DecodedDetection> applyNms(
            List<DecodedDetection> detections, float threshold) {
        List<DecodedDetection> remaining = new ArrayList<>(detections);
        remaining.sort(Comparator.comparingDouble(
                (DecodedDetection detection) -> detection.confidence).reversed());
        List<DecodedDetection> selected = new ArrayList<>();
        while (!remaining.isEmpty()) {
            DecodedDetection best = remaining.remove(0);
            selected.add(best);
            Iterator<DecodedDetection> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                if (intersectionOverUnion(best, iterator.next()) > threshold) {
                    iterator.remove();
                }
            }
        }
        return selected;
    }

    private static float intersectionOverUnion(
            DecodedDetection first, DecodedDetection second) {
        float left = Math.max(first.left, second.left);
        float top = Math.max(first.top, second.top);
        float right = Math.min(first.right, second.right);
        float bottom = Math.min(first.bottom, second.bottom);
        float intersection = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float firstArea = (first.right - first.left) * (first.bottom - first.top);
        float secondArea = (second.right - second.left) * (second.bottom - second.top);
        float union = firstArea + secondArea - intersection;
        return union > 0f ? intersection / union : 0f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class DecodedDetection {
        public final String className;
        public final float confidence;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        DecodedDetection(String className, float confidence,
                float left, float top, float right, float bottom) {
            this.className = className;
            this.confidence = confidence;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
