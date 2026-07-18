package com.android.smarthome.data

data class AuthUser(val uid: String, val email: String? = null)

sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult
    data class Failure(val message: String) : AuthResult
}

enum class AuthStatus { LOADING, SIGNED_OUT, SIGNED_IN, ERROR, CONFIG_REQUIRED }

data class AuthUiState(
    val status: AuthStatus = AuthStatus.LOADING,
    val user: AuthUser? = null,
    val message: String? = null
)

data class BoundingBox(val left: Double, val top: Double, val right: Double, val bottom: Double)

data class AccessEvent(
    val eventId: String,
    val nodeId: String,
    val roomId: String,
    val result: String,
    val credentialType: String,
    val timestampEpochMs: Long
)

data class DetectionEvent(
    val eventId: String,
    val cameraNodeId: String,
    val roomId: String,
    val timestampEpochMs: Long,
    val className: String,
    val confidence: Double,
    val boundingBox: BoundingBox? = null
)

data class CommandRequest(
    val requestId: String,
    val requestedBy: String,
    val homeId: String,
    val targetNodeId: String,
    val commandType: String,
    val createdAtEpochMs: Long,
    val status: String
)

data class MetricReading(
    val key: String,
    val value: Double?,
    val unit: String,
    val source: String,
    val validity: String,
    val error: String? = null,
    val calibrated: Boolean? = null
)

data class TelemetryReading(
    val readingId: String,
    val homeId: String,
    val nodeId: String,
    val roomId: String,
    val schemaVersion: Int,
    val sequence: Long,
    val observedAtEpochMs: Long,
    val gatewayReceivedAtEpochMs: Long,
    val metrics: Map<String, MetricReading>
) {
    val timestampEpochMs: Long
        get() = if (observedAtEpochMs > 0L) observedAtEpochMs else gatewayReceivedAtEpochMs

    fun isStale(nowEpochMs: Long, staleAfterMs: Long = 5 * 60_000L): Boolean =
        timestampEpochMs <= 0L || nowEpochMs - timestampEpochMs > staleAfterMs
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
    val readings: List<TelemetryReading>
) {
    val latestReading: TelemetryReading? get() = readings.maxByOrNull { it.timestampEpochMs }
}

data class RoomSummary(val roomId: String, val label: String, val nodeIds: List<String>)
data class HomeSummary(val homeId: String, val displayName: String, val role: String)

data class HomeSnapshot(
    val home: HomeSummary,
    val rooms: List<RoomSummary>,
    val nodes: List<NodeSummary>,
    val accessEvents: List<AccessEvent>,
    val detectionEvents: List<DetectionEvent>,
    val commandRequests: List<CommandRequest>
)

enum class LoadStatus { LOADING, READY, EMPTY, PERMISSION_DENIED, OFFLINE, ERROR }

data class HomeUiState(
    val status: LoadStatus = LoadStatus.LOADING,
    val snapshot: HomeSnapshot? = null,
    val message: String? = null,
    val connected: Boolean = true
) {
    fun withConnected(value: Boolean) = copy(connected = value)
    fun asOffline(reason: String) = copy(status = LoadStatus.OFFLINE, message = reason, connected = false)
}

data class HomeListItem(val homeId: String, val name: String)

data class HomeActionResult(
    val homeId: String? = null,
    val displayName: String? = null,
    val role: String? = null,
    val inviteCode: String? = null
)

object DeviceActions {
    const val TOGGLE = "toggle"
    const val SET_MODE = "set_mode"
    const val SET_INTENSITY = "set_intensity"
    const val UNLOCK = "unlock"
    const val OPEN_DOOR = "open_door"
    const val LOCK = "lock"
    const val ENABLE_FALL_DETECTION = "enable_fall_detection"
    const val DISABLE_FALL_DETECTION = "disable_fall_detection"
    const val ENABLE_HUMAN_DETECTION = "enable_human_detection"
    const val DISABLE_HUMAN_DETECTION = "disable_human_detection"
}

