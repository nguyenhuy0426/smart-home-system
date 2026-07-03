/*
 * Responsibility: models the generic Firestore reading envelope produced by
 * the gateway normalizer.
 */
package com.example.smart_home_mobile_app.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReadingEnvelope {
    public final String nodeId;
    public final String roomId;
    public final long sequence;
    public final Map<String, MetricValue> metrics;

    public ReadingEnvelope(String nodeId, String roomId, long sequence,
            Map<String, MetricValue> metrics) {
        this.nodeId = nodeId;
        this.roomId = roomId;
        this.sequence = sequence;
        this.metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
    }

    public static final class MetricValue {
        public final double value;
        public final String unit;
        public final String source;
        public final String quality;

        public MetricValue(double value, String unit, String source, String quality) {
            this.value = value;
            this.unit = unit;
            this.source = source;
            this.quality = quality;
        }
    }
}
