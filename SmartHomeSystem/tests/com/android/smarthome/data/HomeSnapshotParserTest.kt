package com.android.smarthome.data

import org.junit.Assert.*
import org.junit.Test

class HomeSnapshotParserTest {
    @Test
    fun parsesRoomsTelemetryAccessAndDetectionWithoutCredentialValue() {
        val raw = mapOf(
            "members" to mapOf("user_1" to mapOf("role" to "home_member")),
            "rooms" to mapOf("living" to mapOf("label" to "Living room"), "entry" to "Entrance"),
            "nodes" to mapOf("env_1" to mapOf(
                "nodeType" to "environment", "roomId" to "living", "schemaVersion" to 1,
                "readings" to mapOf("env_1_1" to mapOf(
                    "readingId" to "env_1_1", "nodeId" to "env_1", "roomId" to "living",
                    "observedAtEpochMs" to 1000L,
                    "metrics" to mapOf(
                        "temperature" to mapOf("value" to 23.5, "unit" to "C", "valid" to true),
                        "co" to mapOf("valid" to false, "error" to "heater_cycle")
                    )
                ))
            )),
            "events" to mapOf(
                "access_1_4" to mapOf(
                    "eventType" to "access.attempt", "nodeId" to "access_1", "roomId" to "entry",
                    "result" to "denied", "credential" to mapOf("kind" to "rfid", "uid" to "secret"),
                    "observedAtEpochMs" to 0L, "gatewayReceivedAtEpochMs" to 2000L
                ),
                "evt_1" to mapOf(
                    "eventType" to "video.fall_detected", "nodeId" to "camera_1", "roomId" to "living",
                    "className" to "Fall-Detected", "confidence" to 0.91, "observedAtEpochMs" to 3000L,
                    "boundingBox" to mapOf("left" to 1, "top" to 2, "right" to 30, "bottom" to 40)
                )
            )
        )

        val snapshot = HomeSnapshotParser.parse("home_1", "user_1", raw)
        assertEquals(2, snapshot.rooms.size)
        assertEquals(1, snapshot.nodes.size)
        assertEquals("rfid", snapshot.accessEvents.first().credentialType)
        assertEquals(2000L, snapshot.accessEvents.first().timestampEpochMs)
        assertEquals("Fall-Detected", snapshot.detectionEvents.first().className)
        val reading = snapshot.nodes.first().latestReading!!
        assertEquals("valid", reading.metrics["temperature"]?.validity)
        assertNotEquals("valid", reading.metrics["co"]?.validity)
        assertTrue(reading.isStale(1000L + 5 * 60_000L + 1))
    }

    @Test
    fun compactRolesAndEmptyHomeArePreserved() {
        val owner = HomeSnapshotParser.parse("home_1", "user_1", mapOf(
            "name" to "Nhà của Huy", "owner" to "user_1",
            "members" to mapOf("user_1" to "admin"), "rooms" to mapOf("room_living" to "Phòng khách")
        ))
        assertEquals("Nhà của Huy", owner.home.displayName)
        assertEquals("device_admin", owner.home.role)
        assertEquals("Phòng khách", owner.rooms.first().label)
        assertTrue(owner.nodes.isEmpty())

        val member = HomeSnapshotParser.parse("home_1", "user_2",
            mapOf("members" to mapOf("user_2" to mapOf("r" to "member", "c" to "ABC12345"))))
        assertEquals("home_member", member.home.role)
    }
}
