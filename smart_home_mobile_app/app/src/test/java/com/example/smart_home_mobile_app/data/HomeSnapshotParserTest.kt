package com.example.smart_home_mobile_app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSnapshotParserTest {
    @Test
    fun parsesMultipleRoomsNodesTelemetryAccessAndDetectionWithoutCredentials() {
        val raw = mapOf(
            "members" to mapOf("user_1" to mapOf("role" to "home_member")),
            "rooms" to mapOf(
                "living" to mapOf("label" to "Living room"),
                "entry" to mapOf("label" to "Entrance"),
            ),
            "nodes" to mapOf(
                "env_1" to mapOf(
                    "nodeType" to "environment",
                    "roomId" to "living",
                    "schemaVersion" to 1,
                    "readings" to mapOf(
                        "env_1_1" to mapOf(
                            "readingId" to "env_1_1",
                            "nodeId" to "env_1",
                            "roomId" to "living",
                            "sequence" to 1L,
                            "observedAtEpochMs" to 1_000L,
                            "metrics" to mapOf(
                                "temperature" to mapOf("value" to 23.5, "unit" to "C", "valid" to true),
                                "co" to mapOf("valid" to false, "error" to "heater_cycle"),
                            ),
                        ),
                    ),
                ),
                "access_1" to mapOf("nodeType" to "access", "roomId" to "entry", "readings" to emptyMap<String, Any>()),
            ),
            "events" to mapOf(
                "access_1_4" to mapOf(
                    "eventId" to "access_1_4", "eventType" to "access.attempt",
                    "nodeId" to "access_1", "roomId" to "entry", "result" to "denied",
                    "credential" to mapOf("kind" to "rfid", "uid" to "must_not_surface"),
                    "observedAtEpochMs" to 2_000L,
                ),
                "evt_1" to mapOf(
                    "eventId" to "evt_1", "eventType" to "video.fall_detected",
                    "nodeId" to "camera_1", "roomId" to "living", "className" to "Fall-Detected",
                    "confidence" to 0.91, "observedAtEpochMs" to 3_000L,
                    "boundingBox" to mapOf("left" to 1, "top" to 2, "right" to 30, "bottom" to 40),
                ),
            ),
        )

        val snapshot = HomeSnapshotParser.parse("home_1", "user_1", raw)

        assertEquals(2, snapshot.rooms.size)
        assertEquals(2, snapshot.nodes.size)
        assertEquals("rfid", snapshot.accessEvents.single().credentialType)
        assertEquals("Fall-Detected", snapshot.detectionEvents.single().className)
        val reading = snapshot.nodes.first { it.nodeId == "env_1" }.latestReading!!
        assertTrue(reading.metrics.getValue("temperature").isValid)
        assertFalse(reading.metrics.getValue("co").isValid)
        assertTrue(reading.isStale(1_000L + 5 * 60 * 1000L + 1))
    }

    @Test
    fun emptyHomePreservesMembershipAndProducesNoFabricatedData() {
        val snapshot = HomeSnapshotParser.parse(
            "home_1",
            "user_1",
            mapOf("members" to mapOf("user_1" to mapOf("role" to "device_admin"))),
        )
        assertEquals("device_admin", snapshot.home.role)
        assertTrue(snapshot.nodes.isEmpty())
        assertTrue(snapshot.rooms.isEmpty())
        assertTrue(snapshot.accessEvents.isEmpty())
        assertTrue(snapshot.detectionEvents.isEmpty())
    }
}

