package com.example.smart_home_mobile_app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class HomeSnapshotParserTest {

    @Test
    public void parsesMultipleRoomsNodesTelemetryAccessAndDetectionWithoutCredentials() {
        Map<String, Object> raw = map(
                "members", map("user_1", map("role", "home_member")),
                "rooms", map(
                        "living", map("label", "Living room"),
                        "entry", map("label", "Entrance")),
                "nodes", map(
                        "env_1", map(
                                "nodeType", "environment",
                                "roomId", "living",
                                "schemaVersion", 1,
                                "readings", map(
                                        "env_1_1", map(
                                                "readingId", "env_1_1",
                                                "nodeId", "env_1",
                                                "roomId", "living",
                                                "sequence", 1L,
                                                "observedAtEpochMs", 1000L,
                                                "metrics", map(
                                                        "temperature", map("value", 23.5, "unit", "C", "valid", true),
                                                        "co", map("valid", false, "error", "heater_cycle"))))),
                        "access_1", map("nodeType", "access", "roomId", "entry",
                                "readings", new HashMap<String, Object>())),
                "events", map(
                        "access_1_4", map(
                                "eventId", "access_1_4", "eventType", "access.attempt",
                                "nodeId", "access_1", "roomId", "entry", "result", "denied",
                                "credential", map("kind", "rfid", "uid", "must_not_surface"),
                                "observedAtEpochMs", 2000L),
                        "evt_1", map(
                                "eventId", "evt_1", "eventType", "video.fall_detected",
                                "nodeId", "camera_1", "roomId", "living", "className", "Fall-Detected",
                                "confidence", 0.91, "observedAtEpochMs", 3000L,
                                "boundingBox", map("left", 1, "top", 2, "right", 30, "bottom", 40))));

        HomeSnapshot snapshot = HomeSnapshotParser.parse("home_1", "user_1", raw);

        assertEquals(2, snapshot.rooms.size());
        assertEquals(2, snapshot.nodes.size());
        assertEquals(1, snapshot.accessEvents.size());
        assertEquals("rfid", snapshot.accessEvents.get(0).credentialType);
        assertEquals(1, snapshot.detectionEvents.size());
        assertEquals("Fall-Detected", snapshot.detectionEvents.get(0).className);

        NodeSummary env = null;
        for (NodeSummary node : snapshot.nodes) {
            if (node.nodeId.equals("env_1")) {
                env = node;
            }
        }
        assertNotNull(env);
        TelemetryReading reading = env.latestReading();
        assertNotNull(reading);
        assertTrue(reading.metrics.get("temperature").isValid());
        assertFalse(reading.metrics.get("co").isValid());
        assertTrue(reading.isStale(1000L + 5 * 60 * 1000L + 1));
    }

    @Test
    public void emptyHomePreservesMembershipAndProducesNoFabricatedData() {
        HomeSnapshot snapshot = HomeSnapshotParser.parse(
                "home_1", "user_1",
                map("members", map("user_1", map("role", "device_admin"))));
        assertEquals("device_admin", snapshot.home.role);
        assertTrue(snapshot.nodes.isEmpty());
        assertTrue(snapshot.rooms.isEmpty());
        assertTrue(snapshot.accessEvents.isEmpty());
        assertTrue(snapshot.detectionEvents.isEmpty());
    }


    @Test
    public void parsesCompactHomeSchema() {
        HomeSnapshot snapshot = HomeSnapshotParser.parse(
                "home_1", "user_1",
                map(
                        "name", "Nhà của Huy",
                        "owner", "user_1",
                        "members", map("user_1", "admin"),
                        "rooms", map("room_living", "Phòng khách")));

        assertEquals("Nhà của Huy", snapshot.home.displayName);
        assertEquals("device_admin", snapshot.home.role);
        assertEquals(1, snapshot.rooms.size());
        assertEquals("Phòng khách", snapshot.rooms.get(0).label);
    }

    @Test
    public void parsesCompactInvitedMemberRole() {
        HomeSnapshot snapshot = HomeSnapshotParser.parse(
                "home_1", "user_2",
                map("members", map("user_2", map("r", "member", "c", "ABC12345"))));

        assertEquals("home_member", snapshot.home.role);
    }

    private static Map<String, Object> map(Object... keyValues) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }
}
