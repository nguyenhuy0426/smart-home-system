package com.example.smart_home_mobile_app.data;

import java.util.Map;

public final class TelemetryReading {
    private static final long DEFAULT_STALE_AFTER_MS = 5 * 60 * 1000L;

    public final String readingId;
    public final String homeId;
    public final String nodeId;
    public final String roomId;
    public final int schemaVersion;
    public final long sequence;
    public final long observedAtEpochMs;
    public final long gatewayReceivedAtEpochMs;
    public final Map<String, MetricReading> metrics;

    public TelemetryReading(String readingId, String homeId, String nodeId, String roomId,
                            int schemaVersion, long sequence, long observedAtEpochMs,
                            long gatewayReceivedAtEpochMs, Map<String, MetricReading> metrics) {
        this.readingId = readingId;
        this.homeId = homeId;
        this.nodeId = nodeId;
        this.roomId = roomId;
        this.schemaVersion = schemaVersion;
        this.sequence = sequence;
        this.observedAtEpochMs = observedAtEpochMs;
        this.gatewayReceivedAtEpochMs = gatewayReceivedAtEpochMs;
        this.metrics = metrics;
    }

    public long timestampEpochMs() {
        return observedAtEpochMs > 0L ? observedAtEpochMs : gatewayReceivedAtEpochMs;
    }

    public boolean isStale(long nowEpochMs) {
        return isStale(nowEpochMs, DEFAULT_STALE_AFTER_MS);
    }

    public boolean isStale(long nowEpochMs, long staleAfterMs) {
        long timestamp = timestampEpochMs();
        return timestamp <= 0L || nowEpochMs - timestamp > staleAfterMs;
    }
}
