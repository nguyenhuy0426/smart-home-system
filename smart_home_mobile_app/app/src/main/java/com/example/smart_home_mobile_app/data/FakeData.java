package com.example.smart_home_mobile_app.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FakeData {
    private FakeData() {
    }

    public static final HomeSummary SAMPLE_HOME_SUMMARY =
            new HomeSummary("home_123", "My Sweet Home", "owner");

    public static final RoomSummary SAMPLE_ROOM_SUMMARY =
            new RoomSummary("living_room", "Living Room", Collections.singletonList("node_1"));

    public static final Map<String, MetricReading> SAMPLE_METRIC_READINGS = sampleMetrics();

    private static Map<String, MetricReading> sampleMetrics() {
        Map<String, MetricReading> metrics = new LinkedHashMap<>();
        metrics.put("temperature",
                new MetricReading("temperature", 22.5, "°C", "sensor", "valid", null, true));
        metrics.put("humidity",
                new MetricReading("humidity", 45.0, "%", "sensor", "valid", null, true));
        return metrics;
    }

    public static final TelemetryReading SAMPLE_TELEMETRY_READING = new TelemetryReading(
            "read_1", "home_123", "node_1", "living_room", 1, 100L,
            System.currentTimeMillis(), System.currentTimeMillis(), SAMPLE_METRIC_READINGS);

    public static final NodeSummary SAMPLE_NODE_SUMMARY = new NodeSummary(
            "node_1", "home_123", "living_room", "environment_sensor", "Living Room Sensor",
            1, "online", Collections.singletonList("reboot"),
            Collections.singletonList(SAMPLE_TELEMETRY_READING));

    public static final AccessEvent SAMPLE_ACCESS_EVENT = new AccessEvent(
            "event_1", "lock_1", "entrance", "granted", "rfid", System.currentTimeMillis());

    public static final DetectionEvent SAMPLE_DETECTION_EVENT = new DetectionEvent(
            "det_1", "cam_1", "living_room", System.currentTimeMillis(), "human", 0.98,
            new BoundingBox(0.1, 0.1, 0.5, 0.5));

    public static final HomeSnapshot SAMPLE_HOME_SNAPSHOT = new HomeSnapshot(
            SAMPLE_HOME_SUMMARY,
            Collections.singletonList(SAMPLE_ROOM_SUMMARY),
            Collections.singletonList(SAMPLE_NODE_SUMMARY),
            Collections.singletonList(SAMPLE_ACCESS_EVENT),
            Collections.singletonList(SAMPLE_DETECTION_EVENT),
            Collections.<CommandRequest>emptyList());

    public static final HomeUiState SAMPLE_HOME_UI_STATE =
            new HomeUiState(LoadStatus.READY, SAMPLE_HOME_SNAPSHOT, null, true);
}
