package com.example.smart_home_mobile_app.data

enum class LoadStatus {
    IDLE,
    LOADING,
    READY,
    EMPTY,
    OFFLINE,
    PERMISSION_DENIED,
    ERROR,
}

data class HomeSummary(
    val homeId: String,
    val displayName: String,
    val role: String,
)

data class RoomSummary(
    val roomId: String,
    val label: String,
    val nodeIds: List<String>,
)

data class MetricReading(
    val key: String,
    val value: Double?,
    val unit: String,
    val source: String,
    val validity: String,
    val error: String?,
    val calibrated: Boolean?,
) {
    val isValid: Boolean
        get() = value != null && validity.lowercase() in setOf("valid", "ok", "good", "measured")
}

data class TelemetryReading(
    val readingId: String,
    val homeId: String,
    val nodeId: String,
    val roomId: String,
    val schemaVersion: Int,
    val sequence: Long,
    val observedAtEpochMs: Long,
    val gatewayReceivedAtEpochMs: Long,
    val metrics: Map<String, MetricReading>,
) {
    fun timestampEpochMs(): Long = when {
        observedAtEpochMs > 0L -> observedAtEpochMs
        else -> gatewayReceivedAtEpochMs
    }

    fun isStale(nowEpochMs: Long, staleAfterMs: Long = 5 * 60 * 1000L): Boolean {
        val timestamp = timestampEpochMs()
        return timestamp <= 0L || nowEpochMs - timestamp > staleAfterMs
    }
}

data class NodeSummary(
    val nodeId: String,
    val homeId: String,
    val roomId: String,
    val nodeType: String,
    val label: String,
    val schemaVersion: Int,
    val status: String,
    val actions: List<String>,
    val readings: List<TelemetryReading>,
) {
    val latestReading: TelemetryReading?
        get() = readings.maxByOrNull { it.timestampEpochMs() }
}

data class AccessEvent(
    val eventId: String,
    val nodeId: String,
    val roomId: String,
    val result: String,
    val credentialType: String,
    val timestampEpochMs: Long,
)

data class BoundingBox(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

data class DetectionEvent(
    val eventId: String,
    val cameraNodeId: String,
    val roomId: String,
    val timestampEpochMs: Long,
    val className: String,
    val confidence: Double,
    val boundingBox: BoundingBox?,
)

data class CommandRequest(
    val requestId: String,
    val requestedBy: String,
    val homeId: String,
    val targetNodeId: String,
    val commandType: String,
    val createdAtEpochMs: Long,
    val status: String,
)

data class HomeSnapshot(
    val home: HomeSummary,
    val rooms: List<RoomSummary>,
    val nodes: List<NodeSummary>,
    val accessEvents: List<AccessEvent>,
    val detectionEvents: List<DetectionEvent>,
    val commandRequests: List<CommandRequest>,
)

data class HomeUiState(
    val status: LoadStatus = LoadStatus.IDLE,
    val snapshot: HomeSnapshot? = null,
    val message: String? = null,
    val connected: Boolean = true,
)
