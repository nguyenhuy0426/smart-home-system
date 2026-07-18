package com.android.smarthome.gateway

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.android.smarthome.firebase.FirebaseSyncService
import com.android.smarthome.gateway.ui.DashboardActivity
import com.android.smarthome.mesh.BleMeshService
import com.android.smarthome.security.OAuthBackendHandler
import com.android.smarthome.video.OnnxInferenceService
import com.android.smarthome.video.RtspFrameReceiver
import com.android.smarthome.video.YoloOutputDecoder
import org.json.JSONObject
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SmartHomeGatewayService : Service(), WifiDataReceiver.TelemetryListener, RtspFrameReceiver.FrameListener {

    private val binder = LocalBinder()

    // Gateways components
    private var wifiReceiver: WifiDataReceiver? = null
    private var rtspReceiver: RtspFrameReceiver? = null
    private var inferenceService: OnnxInferenceService? = null
    private var bleMeshIntent: Intent? = null
    private var firebaseSyncIntent: Intent? = null
    private var isFirebaseBound = false
    private var configuredHomeId: String? = null
    @Volatile
    private var firebaseSync: FirebaseSyncService? = null

    // Live state cache for UI binding
    val latestTelemetry = HashMap<String, JSONObject>() // nodeId -> payload
    val latestAccessEvents = ArrayList<JSONObject>()
    val latestDetections = ArrayList<OnnxInferenceService.Detection>()
    @Volatile
    var latestBitmap: Bitmap? = null

    @Volatile
    private var uiListener: GatewayStateListener? = null
    private val sequence = AtomicLong(System.currentTimeMillis())
    private val lastDetectionEventAt = ConcurrentHashMap<String, Long>()
    private val pendingDetectionEvents = ArrayDeque<PendingDetectionEvent>()

    companion object {
        private const val TAG = "SmartHomeGatewayService"
        private const val NOTIFICATION_CHANNEL_ID = "smarthome_gateway"
        private const val NOTIFICATION_ID = 1401
        private const val DETECTION_EVENT_COOLDOWN_MS = 10_000L
        private const val MAX_PENDING_DETECTION_EVENTS = 100
        private val IDENTIFIER_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
    }

    private data class PendingDetectionEvent(
        val homeId: String,
        val nodeId: String,
        val eventId: String,
        val payload: String
    )

    interface GatewayStateListener {
        fun onStateUpdated()
        fun onFrameUpdated(bitmap: Bitmap, detections: List<OnnxInferenceService.Detection>)
    }

    inner class LocalBinder : Binder() {
        fun getService(): SmartHomeGatewayService = this@SmartHomeGatewayService
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        Log.i(TAG, "SmartHomeGatewayService starting...")
        val secrets = OAuthBackendHandler.loadSecrets()
        configuredHomeId = secrets
            ?.optString("home_id")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) }
        if (configuredHomeId == null) {
            Log.e(TAG, "A valid home_id is required; cloud event upload is disabled")
        }

        // 1. Initialize HTTP Telemetry Receiver
        wifiReceiver = WifiDataReceiver(this)
        wifiReceiver?.start()

        // 2. Keep the BLE control plane active after provisioning.
        bleMeshIntent = Intent(this, BleMeshService::class.java).also { startService(it) }

        // The gateway owns cloud synchronization; UI binding order must not control data delivery.
        firebaseSyncIntent = Intent(this, FirebaseSyncService::class.java).also {
            startService(it)
            isFirebaseBound = bindService(it, firebaseConnection, Context.BIND_AUTO_CREATE)
        }

        // 3. Initialize inference before accepting camera frames.
        inferenceService = OnnxInferenceService(this)
        inferenceService?.init()

        // 4. Start one authenticated RTSP client session per provisioned camera.
        val cameraSources = loadCameraSources(secrets)
        if (cameraSources.isEmpty()) {
            Log.w(TAG, "No provisioned camera_sources are configured; camera ingest is disabled")
        } else {
            rtspReceiver = RtspFrameReceiver(cameraSources, this)
            rtspReceiver?.start()
        }

        Log.i(TAG, "SmartHomeGatewayService initialized completely.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun setUIListener(listener: GatewayStateListener?) {
        this.uiListener = listener
    }

    // Connects FirebaseSyncService reference when it binds
    fun setFirebaseSyncService(service: FirebaseSyncService?) {
        this.firebaseSync = service
        Log.i(TAG, if (service == null) {
            "Unlinked FirebaseSyncService from Gateway orchestrator"
        } else {
            "Linked FirebaseSyncService to Gateway orchestrator"
        })
        if (service != null) flushPendingDetectionEvents(service)
    }

    fun isFirebaseSyncConnected(): Boolean = firebaseSync != null

    fun clearAuthSession() {
        firebaseSync?.clearAuthSession()
    }

    private val firebaseConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val localBinder = service as? FirebaseSyncService.LocalBinder
            setFirebaseSyncService(localBinder?.getService())
            uiListener?.onStateUpdated()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            setFirebaseSyncService(null)
            uiListener?.onStateUpdated()
        }
    }

    private fun createForegroundNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Smart Home gateway",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps local node, camera, and cloud synchronization active"
                setShowBadge(false)
            }
        )
        val openDashboard = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Smart Home gateway active")
            .setContentText("Monitoring provisioned home devices")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openDashboard)
            .setOngoing(true)
            .build()
    }

    // WifiDataReceiver Callbacks (Node telemetry)
    override fun onTelemetryReceived(nodeId: String, roomId: String, payload: String) {
        val json = JSONObject(payload).apply {
            put("gatewayReceivedAtEpochMs", System.currentTimeMillis())
        }
        synchronized(latestTelemetry) {
            latestTelemetry[nodeId] = json
        }
        
        // Forward to Firebase
        val homeId = configuredHomeId
        val recordSequence = json.optLong("sequence", -1L).takeIf { it >= 0L }
            ?: sequence.getAndIncrement()
        val readingId = json.optString("readingId")
            .takeIf { IDENTIFIER_PATTERN.matches(it) && it.startsWith("${nodeId}_") }
            ?: "${nodeId}_$recordSequence"
        json.put("readingId", readingId)
        if (homeId != null) {
            firebaseSync?.enqueueRecord(
                homeId,
                "nodes/$nodeId/readings",
                nodeId,
                readingId,
                cloudPayload(json)
            )
        }

        uiListener?.onStateUpdated()
    }

    override fun onAccessEventReceived(nodeId: String, roomId: String, payload: String) {
        val json = JSONObject(payload).apply {
            put("gatewayReceivedAtEpochMs", System.currentTimeMillis())
        }
        synchronized(latestAccessEvents) {
            latestAccessEvents.add(0, json)
            if (latestAccessEvents.size > 20) {
                latestAccessEvents.removeAt(latestAccessEvents.size - 1)
            }
        }

        // Forward to Firebase
        configuredHomeId?.let { homeId ->
            val eventId = json.optString("eventId")
                .takeIf { IDENTIFIER_PATTERN.matches(it) && it.startsWith("${nodeId}_") }
                ?: "${nodeId}_${sequence.getAndIncrement()}"
            json.put("eventId", eventId)
            firebaseSync?.enqueueRecord(
                homeId,
                "events",
                nodeId,
                eventId,
                cloudPayload(json)
            )
        }

        uiListener?.onStateUpdated()
    }

    // RtspFrameReceiver Callbacks (Camera frame input)
    override fun onFrameReceived(
        camera: RtspFrameReceiver.CameraIdentity,
        bitmap: Bitmap,
        metadata: RtspFrameReceiver.FrameMetadata
    ) {
        latestBitmap = bitmap

        // Run ONNX object detection
        inferenceService?.runInference(bitmap, object : OnnxInferenceService.DetectionListener {
            override fun onDetectionsResult(detections: List<OnnxInferenceService.Detection>, width: Int, height: Int) {
                synchronized(latestDetections) {
                    latestDetections.clear()
                    latestDetections.addAll(detections)
                }

                detections
                    .filter {
                        it.className == YoloOutputDecoder.CLASS_NAME_PERSON ||
                            it.className == YoloOutputDecoder.CLASS_NAME_FALLEN
                    }
                    .forEach { enqueueDetectionEvent(camera, metadata, it, width, height) }

                uiListener?.onFrameUpdated(bitmap, detections)
            }
        })
    }

    private fun enqueueDetectionEvent(
        camera: RtspFrameReceiver.CameraIdentity,
        metadata: RtspFrameReceiver.FrameMetadata,
        detection: OnnxInferenceService.Detection,
        frameWidth: Int,
        frameHeight: Int
    ) {
        val eventType = if (detection.className == YoloOutputDecoder.CLASS_NAME_FALLEN) {
            "video.fall_detected"
        } else {
            "video.human_detected"
        }
        val now = metadata.receivedAtEpochMs
        val cooldownKey = camera.nodeId + ":" + eventType
        val previous = lastDetectionEventAt[cooldownKey]
        if (previous != null && now - previous < DETECTION_EVENT_COOLDOWN_MS) return
        lastDetectionEventAt[cooldownKey] = now

        val homeId = configuredHomeId ?: return
        val box = detection.boundingBox
        val eventId = deterministicDetectionEventId(camera, metadata, detection)
        val event = JSONObject().apply {
            put("eventId", eventId)
            put("nodeId", camera.nodeId)
            put("roomId", camera.roomId)
            put("eventType", eventType)
            put("className", detection.className)
            put("classIndex", detection.classIndex)
            put("observedAtEpochMs", now)
            put("cameraRtpTimestamp", metadata.rtpTimestamp)
            put("cameraSsrc", metadata.ssrc)
            put("confidence", detection.confidence.toDouble())
            put("frameWidth", frameWidth)
            put("frameHeight", frameHeight)
            put("boundingBox", JSONObject().apply {
                put("left", box.left.toDouble())
                put("top", box.top.toDouble())
                put("right", box.right.toDouble())
                put("bottom", box.bottom.toDouble())
            })
        }
        Log.i(TAG, "Queueing $eventType event")
        enqueueOrBufferDetectionEvent(
            PendingDetectionEvent(homeId, camera.nodeId, eventId, cloudPayload(event))
        )
    }

    private fun deterministicDetectionEventId(
        camera: RtspFrameReceiver.CameraIdentity,
        metadata: RtspFrameReceiver.FrameMetadata,
        detection: OnnxInferenceService.Detection
    ): String {
        val box = detection.boundingBox
        val canonical = listOf(
            camera.nodeId,
            camera.roomId,
            metadata.ssrc.toString(),
            metadata.rtpTimestamp.toString(),
            detection.classIndex.toString(),
            detection.className,
            java.lang.Float.floatToIntBits(detection.confidence).toString(),
            java.lang.Float.floatToIntBits(box.left).toString(),
            java.lang.Float.floatToIntBits(box.top).toString(),
            java.lang.Float.floatToIntBits(box.right).toString(),
            java.lang.Float.floatToIntBits(box.bottom).toString()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        val suffix = digest.take(20).joinToString("") {
            "%02x".format(Locale.US, it.toInt() and 0xff)
        }
        return "evt_$suffix"
    }

    private fun enqueueOrBufferDetectionEvent(event: PendingDetectionEvent) {
        val service = firebaseSync
        if (service != null) {
            service.enqueueRecord(event.homeId, "events", event.nodeId, event.eventId, event.payload)
            return
        }
        synchronized(pendingDetectionEvents) {
            if (pendingDetectionEvents.size >= MAX_PENDING_DETECTION_EVENTS) {
                pendingDetectionEvents.removeFirst()
                Log.w(TAG, "Detection event handoff buffer full; dropping oldest event")
            }
            pendingDetectionEvents.addLast(event)
        }
    }

    private fun flushPendingDetectionEvents(service: FirebaseSyncService) {
        val pending = ArrayList<PendingDetectionEvent>()
        synchronized(pendingDetectionEvents) {
            while (pendingDetectionEvents.isNotEmpty()) {
                pending += pendingDetectionEvents.removeFirst()
            }
        }
        pending.forEach { event ->
            service.enqueueRecord(event.homeId, "events", event.nodeId, event.eventId, event.payload)
        }
    }

    private fun cloudPayload(localPayload: JSONObject): String {
        return JSONObject(localPayload.toString()).apply {
            put("gatewayReceivedAtEpochMs", JSONObject().put(".sv", "timestamp"))
        }.toString()
    }

    private fun loadCameraSources(
        secrets: JSONObject?
    ): List<RtspFrameReceiver.CameraSource> {
        val result = ArrayList<RtspFrameReceiver.CameraSource>()
        val nodeIds = HashSet<String>()
        val endpoints = HashSet<String>()
        val configured = secrets?.optJSONArray("camera_sources") ?: return result
        for (index in 0 until configured.length()) {
            if (result.size >= RtspFrameReceiver.MAX_CAMERA_SOURCES) {
                Log.e(TAG, "Ignoring camera_sources beyond the configured safety limit")
                break
            }
            val item = configured.optJSONObject(index) ?: continue
            val nodeId = item.optString("node_id")
            val roomId = item.optString("room_id")
            val host = item.optString("rtsp_host")
            val port = item.optInt("rtsp_port", -1)
            val streamPath = item.optString("stream_path")
            val authKey = item.optString("auth_key")
            val endpoint = "$host:$port$streamPath"
            if (!IDENTIFIER_PATTERN.matches(nodeId) || !IDENTIFIER_PATTERN.matches(roomId) ||
                host.isBlank() || host.length > 253 ||
                host.any { it == '\r' || it == '\n' || it == '\u0000' } ||
                port !in 1..65535 || !streamPath.startsWith('/') || streamPath.length > 255 ||
                streamPath.any { it == '\r' || it == '\n' || it == '\u0000' } ||
                !authKey.matches(Regex("[0-9A-Fa-f]{64}"))
            ) {
                Log.e(TAG, "Ignoring camera_sources[$index] with invalid required fields")
                continue
            }
            if (!nodeIds.add(nodeId) || !endpoints.add(endpoint)) {
                Log.e(TAG, "Ignoring duplicate camera source at index $index")
                continue
            }
            result += RtspFrameReceiver.CameraSource(
                nodeId,
                roomId,
                host,
                port,
                streamPath,
                authKey
            )
        }
        return result
    }

    override fun onDestroy() {
        wifiReceiver?.stop()
        rtspReceiver?.stop()
        inferenceService?.close()
        if (isFirebaseBound) {
            unbindService(firebaseConnection)
            isFirebaseBound = false
        }
        firebaseSyncIntent?.let { stopService(it) }
        firebaseSyncIntent = null
        bleMeshIntent?.let { stopService(it) }
        bleMeshIntent = null
        firebaseSync = null
        configuredHomeId = null
        super.onDestroy()
    }
}
