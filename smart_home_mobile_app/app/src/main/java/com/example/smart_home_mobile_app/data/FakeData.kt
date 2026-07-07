package com.example.smart_home_mobile_app.data

object FakeData {
    val sampleHomeSummary = HomeSummary(
        homeId = "home_123",
        displayName = "My Sweet Home",
        role = "owner"
    )

    val sampleRoomSummary = RoomSummary(
        roomId = "living_room",
        label = "Living Room",
        nodeIds = listOf("node_1")
    )

    val sampleMetricReadings = mapOf(
        "temperature" to MetricReading("temperature", 22.5, "°C", "sensor", "valid", null, true),
        "humidity" to MetricReading("humidity", 45.0, "%", "sensor", "valid", null, true)
    )

    val sampleTelemetryReading = TelemetryReading(
        readingId = "read_1",
        homeId = "home_123",
        nodeId = "node_1",
        roomId = "living_room",
        schemaVersion = 1,
        sequence = 100L,
        observedAtEpochMs = System.currentTimeMillis(),
        gatewayReceivedAtEpochMs = System.currentTimeMillis(),
        metrics = sampleMetricReadings
    )

    val sampleNodeSummary = NodeSummary(
        nodeId = "node_1",
        homeId = "home_123",
        roomId = "living_room",
        nodeType = "environment_sensor",
        label = "Living Room Sensor",
        schemaVersion = 1,
        status = "online",
        actions = listOf("reboot"),
        readings = listOf(sampleTelemetryReading)
    )

    val sampleAccessEvent = AccessEvent(
        eventId = "event_1",
        nodeId = "lock_1",
        roomId = "entrance",
        result = "granted",
        credentialType = "rfid",
        timestampEpochMs = System.currentTimeMillis()
    )

    val sampleDetectionEvent = DetectionEvent(
        eventId = "det_1",
        cameraNodeId = "cam_1",
        roomId = "living_room",
        timestampEpochMs = System.currentTimeMillis(),
        className = "human",
        confidence = 0.98,
        boundingBox = BoundingBox(0.1, 0.1, 0.5, 0.5)
    )

    val sampleHomeSnapshot = HomeSnapshot(
        home = sampleHomeSummary,
        rooms = listOf(sampleRoomSummary),
        nodes = listOf(sampleNodeSummary),
        accessEvents = listOf(sampleAccessEvent),
        detectionEvents = listOf(sampleDetectionEvent),
        commandRequests = emptyList()
    )

    val sampleHomeUiState = HomeUiState(
        status = LoadStatus.READY,
        snapshot = sampleHomeSnapshot,
        message = null,
        connected = true
    )
}
