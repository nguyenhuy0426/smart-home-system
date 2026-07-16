package com.example.smart_home_mobile_app.ui.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.smart_home_mobile_app.data.CommandRequest;
import com.example.smart_home_mobile_app.data.HomeSnapshot;
import com.example.smart_home_mobile_app.data.HomeSummary;
import com.example.smart_home_mobile_app.data.MetricReading;
import com.example.smart_home_mobile_app.data.NodeSummary;
import com.example.smart_home_mobile_app.data.RoomSummary;
import com.example.smart_home_mobile_app.data.TelemetryReading;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NotificationCenterTest {

    @Test
    public void combinesCommandsAndThresholdCrossingsInReverseTimeOrder() {
        NodeSummary sensor = new NodeSummary(
                "sensor_1", "home_1", "kitchen", "environment_sensor", "Cảm biến bếp",
                1, "online", Collections.emptyList(), Arrays.asList(
                        reading("normal", 100L, 25.0),
                        reading("first_high", 200L, 36.0),
                        reading("still_high", 300L, 37.0),
                        reading("normal_again", 400L, 34.0),
                        reading("second_high", 500L, 38.0)));

        CommandRequest command = new CommandRequest(
                "cmd_1", "user_1", "home_1", "sensor_1",
                "toggle", 600L, "completed");

        HomeSnapshot snapshot = new HomeSnapshot(
                new HomeSummary("home_1", "Nhà", "device_admin"),
                Collections.singletonList(
                        new RoomSummary("kitchen", "Bếp", Collections.singletonList("sensor_1"))),
                Collections.singletonList(sensor),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(command));

        List<NotificationItem> items =
                NotificationCenter.build(snapshot, "user_1", "owner@example.com");

        assertEquals(3, items.size());
        assertEquals(NotificationItem.Kind.COMMAND, items.get(0).kind);
        assertTrue(items.get(0).description.contains("owner@example.com"));
        assertEquals(500L, items.get(1).timestampEpochMs);
        assertEquals(200L, items.get(2).timestampEpochMs);
        assertTrue(items.get(1).description.contains("38"));
        assertTrue(items.get(1).description.contains("35"));
    }

    private static TelemetryReading reading(String id, long timestamp, double temperature) {
        Map<String, MetricReading> metrics = new LinkedHashMap<>();
        metrics.put("temperature", new MetricReading(
                "temperature", temperature, "°C", "DHT22", "valid", null, true));
        return new TelemetryReading(
                id, "home_1", "sensor_1", "kitchen", 1, timestamp,
                timestamp, timestamp, metrics);
    }
}
