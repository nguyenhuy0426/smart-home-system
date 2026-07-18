package com.android.smarthome.data

object PreviewData {
    private val now = System.currentTimeMillis()
    private val metrics = linkedMapOf(
        "temperature" to MetricReading("temperature", 22.5, "°C", "sensor", "valid", calibrated = true),
        "humidity" to MetricReading("humidity", 45.0, "%", "sensor", "valid", calibrated = true)
    )
    private val reading = TelemetryReading(
        "read_1", "home_123", "node_1", "living_room", 1, 100L, now, now, metrics
    )
    private val node = NodeSummary(
        "node_1", "home_123", "living_room", "environment_sensor", "Living Room Sensor",
        1, "online", listOf("reboot"), listOf(reading)
    )
    val snapshot = HomeSnapshot(
        HomeSummary("home_123", "My Sweet Home", "owner"),
        listOf(RoomSummary("living_room", "Living Room", listOf("node_1"))),
        listOf(node),
        listOf(AccessEvent("event_1", "lock_1", "entrance", "granted", "rfid", now)),
        listOf(DetectionEvent("det_1", "cam_1", "living_room", now, "human", 0.98,
            BoundingBox(0.1, 0.1, 0.5, 0.5))),
        emptyList()
    )
    val homeUiState = HomeUiState(LoadStatus.READY, snapshot)
}
