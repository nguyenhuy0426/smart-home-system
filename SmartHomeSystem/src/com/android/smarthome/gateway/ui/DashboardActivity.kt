package com.android.smarthome.gateway.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.smarthome.gateway.SmartHomeGatewayService
import com.android.smarthome.security.LoginRegisterActivity
import com.android.smarthome.security.FirebaseSessionStore
import com.android.smarthome.video.OnnxInferenceService
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : ComponentActivity(), SmartHomeGatewayService.GatewayStateListener {

    companion object {
        private const val TAG = "DashboardActivity"
    }

    private var gatewayService: SmartHomeGatewayService? = null
    private var isBound = false
    private lateinit var sessionStore: FirebaseSessionStore

    // State holders for Compose
    private val telemetryState = mutableStateMapOf<String, JSONObject>()
    private val accessEventsState = mutableStateListOf<JSONObject>()
    private val detectionsState = mutableStateListOf<OnnxInferenceService.Detection>()
    private var currentBitmapState by mutableStateOf<Bitmap?>(null)
    private var gatewayStatusState by mutableStateOf("Offline")
    private var firebaseStatusState by mutableStateOf("Connecting")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStore = FirebaseSessionStore(this)

        // Start background gateway service
        val serviceIntent = Intent(this, SmartHomeGatewayService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            DashboardScreen()
        }
    }

    override fun onStart() {
        super.onStart()
        val session = sessionStore.load()
        if (session == null) {
            handleLogout()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SmartHomeGatewayService.LocalBinder
            gatewayService = binder.getService()
            gatewayService?.setUIListener(this@DashboardActivity)
            isBound = true
            gatewayStatusState = "Online"
            refreshLocalData()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            gatewayService = null
            isBound = false
            gatewayStatusState = "Offline"
        }
    }

    private fun refreshLocalData() {
        val service = gatewayService ?: return
        synchronized(service.latestTelemetry) {
            telemetryState.clear()
            telemetryState.putAll(service.latestTelemetry)
        }
        synchronized(service.latestAccessEvents) {
            accessEventsState.clear()
            accessEventsState.addAll(service.latestAccessEvents)
        }
        synchronized(service.latestDetections) {
            detectionsState.clear()
            detectionsState.addAll(service.latestDetections)
        }
        currentBitmapState = service.latestBitmap
        firebaseStatusState = if (service.isFirebaseSyncConnected()) "Active" else "Connecting"
    }

    // GatewayStateListener Callbacks
    override fun onStateUpdated() {
        runOnUiThread { refreshLocalData() }
    }

    override fun onFrameUpdated(bitmap: Bitmap, detections: List<OnnxInferenceService.Detection>) {
        runOnUiThread {
            currentBitmapState = bitmap
            synchronized(detectionsState) {
                detectionsState.clear()
                detectionsState.addAll(detections)
            }
        }
    }

    private fun handleLogout() {
        gatewayService?.clearAuthSession()
        sessionStore.clear()

        val intent = Intent(this, LoginRegisterActivity::class.java)
        startActivity(intent)
        finish()
    }

    @Composable
    fun DashboardScreen() {
        var currentTime by remember { mutableStateOf(Calendar.getInstance().time) }
        
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                currentTime = Calendar.getInstance().time
            }
        }

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

        // Styling Color Palette: Warm Neutral Liquid Glass
        val backgroundBase = Color(0xFFFAF8F5) // Warm off-white
        val cardBackground = Color(0xAAFFFFFF) // Translucent glass card
        val textPrimary = Color(0xFF4A4540)
        val textSecondary = Color(0xFF8A8078)
        val accentTeal = Color(0xFF008080)
        val accentAmber = Color(0xFFD97706)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBase)
                .padding(24.dp)
        ) {
            // Left Column: Clock, System status, Event log
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header clock
                Column {
                    Text(
                        text = timeFormat.format(currentTime),
                        color = textPrimary,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateFormat.format(currentTime),
                        color = textSecondary,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // System Status
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBackground)
                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp).background(Color.Transparent)) {
                        Text("System Dashboard", color = accentTeal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Gateway: $gatewayStatusState", color = textPrimary, fontSize = 14.sp)
                        Text("Firebase Sync: $firebaseStatusState", color = textPrimary, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Event Logs
                Text("Access & Motion Logs", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBackground)
                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    if (accessEventsState.isEmpty()) {
                        item {
                            Text("No recent log events", color = textSecondary, fontSize = 12.sp)
                        }
                    } else {
                        items(accessEventsState.toList()) { event ->
                            val result = event.optString("result", "unknown")
                            val nodeObserved = event.optLong("observedAtEpochMs", 0)
                            val observed = if (nodeObserved >= 946684800000L) {
                                nodeObserved
                            } else {
                                event.optLong("gatewayReceivedAtEpochMs", 0)
                            }
                            val kind = event.optJSONObject("credential")?.optString("kind", "card") ?: "card"
                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(observed))

                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = "[$timeStr] Access attempt: $result",
                                    color = if (result == "granted") accentTeal else Color.Red,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text("Credential: $kind", color = textSecondary, fontSize = 11.sp)
                                Divider(modifier = Modifier.padding(top = 6.dp), color = Color.LightGray.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                // Logout Button
                Button(
                    onClick = { handleLogout() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentAmber)
                ) {
                    Text("Logout Session")
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Right Column: Camera live feed and Sensor grids
            Column(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
            ) {
                // Live camera card
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = currentBitmapState
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Live feed from ESP32-CAM",
                            modifier = Modifier.fillMaxSize()
                        )

                        // Draw bounding boxes on Canvas overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasW = size.width
                            val canvasH = size.height

                            val imgW = bitmap.width.toFloat()
                            val imgH = bitmap.height.toFloat()

                            val scale = minOf(canvasW / imgW, canvasH / imgH)
                            val dx = (canvasW - imgW * scale) / 2f
                            val dy = (canvasH - imgH * scale) / 2f

                            synchronized(detectionsState) {
                                for (det in detectionsState) {
                                    val rect = det.boundingBox
                                    val mappedLeft = rect.left * scale + dx
                                    val mappedTop = rect.top * scale + dy
                                    val mappedRight = rect.right * scale + dx
                                    val mappedBottom = rect.bottom * scale + dy

                                    drawRect(
                                        color = if (det.className == "Fall-Detected") Color.Red else accentAmber,
                                        topLeft = Offset(mappedLeft, mappedTop),
                                        size = Size(mappedRight - mappedLeft, mappedBottom - mappedTop),
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }
                        }
                    } else {
                        Text("No live RTSP camera feed", color = Color.Gray, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // One live telemetry card per node; no synthetic fallback values are displayed.
                val nodeReadings = telemetryState.entries
                    .sortedWith(compareBy({ it.value.optString("roomId") }, { it.key }))
                if (nodeReadings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Waiting for environmental nodes",
                            color = textSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyRow(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        items(nodeReadings, key = { it.key }) { entry ->
                            NodeTelemetryCard(entry.key, entry.value, accentTeal, accentAmber)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun NodeTelemetryCard(
        nodeId: String,
        reading: JSONObject,
        accentTeal: Color,
        accentAmber: Color
    ) {
        val cardBackground = Color(0xAAFFFFFF)
        val textPrimary = Color(0xFF4A4540)
        val textSecondary = Color(0xFF8A8078)
        val metrics = reading.optJSONObject("metrics")
        val rows = listOf(
            Triple("ambientTemperature", "Temperature", "°C"),
            Triple("relativeHumidity", "Humidity", "%"),
            Triple("co", "Carbon monoxide", "ppm"),
            Triple("pm25", "Fine dust", "µg/m³"),
            Triple("pressure", "Pressure", "hPa"),
            Triple("eco2", "eCO₂", "ppm"),
            Triple("tvoc", "TVOC", "ppb")
        ).mapNotNull { (key, label, fallbackUnit) ->
            val metric = metrics?.optJSONObject(key) ?: return@mapNotNull null
            if (!metric.has("value")) return@mapNotNull null
            val value = metric.optDouble("value", Double.NaN)
            if (!value.isFinite()) return@mapNotNull null
            val unit = metric.optString("unit").ifBlank { fallbackUnit }
            Triple(label, String.format(Locale.US, "%.1f", value), unit)
        }

        Card(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBackground)
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                Text(
                    reading.optString("roomId", "Unassigned room"),
                    color = textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(nodeId, color = textSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(12.dp))
                if (rows.isEmpty()) {
                    Text("No valid metrics", color = textSecondary, fontSize = 12.sp)
                } else {
                    rows.forEachIndexed { index, (label, value, unit) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = textSecondary, fontSize = 12.sp)
                            Text(
                                "$value $unit",
                                color = if (label == "Carbon monoxide") accentAmber else accentTeal,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (index < rows.lastIndex) {
                            Divider(color = Color.White.copy(alpha = 0.45f))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isBound) {
            gatewayService?.setUIListener(null)
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}
