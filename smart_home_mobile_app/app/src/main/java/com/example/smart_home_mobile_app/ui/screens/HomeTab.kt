package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smart_home_mobile_app.data.AccessEvent
import com.example.smart_home_mobile_app.data.FakeData
import com.example.smart_home_mobile_app.data.HomeUiState
import com.example.smart_home_mobile_app.data.LoadStatus
import com.example.smart_home_mobile_app.data.MetricReading
import com.example.smart_home_mobile_app.data.NodeSummary
import com.example.smart_home_mobile_app.ui.TuyaTheme
import com.example.smart_home_mobile_app.ui.formatEventTime

private data class MetricSpec(val keys: List<String>, val label: String, val fallbackUnit: String)

private val environmentMetricSpecs = listOf(
    MetricSpec(listOf("ambientTemperature", "temperature"), "Temperature", "°C"),
    MetricSpec(listOf("relativeHumidity", "humidity"), "Humidity", "%"),
    MetricSpec(listOf("co", "mq7", "mq7Raw"), "CO / MQ7", "ppm"),
    MetricSpec(listOf("pm25", "fineDust"), "PM2.5 / GP2Y1014", "µg/m³"),
    MetricSpec(listOf("pressure"), "Pressure", "hPa"),
    MetricSpec(listOf("gasResistance", "airQuality", "eco2", "tvoc"), "Air quality", ""),
)

@Composable
fun HomeTab(
    state: HomeUiState,
    onNavigateToNodeDetails: (String) -> Unit,
    onNavigateToCamera: () -> Unit,
) {
    when (state.status) {
        LoadStatus.IDLE, LoadStatus.LOADING -> StatusPane { CircularProgressIndicator() }
        LoadStatus.EMPTY -> StatusPane { Text(state.message ?: "No data") }
        LoadStatus.PERMISSION_DENIED -> StatusPane {
            Text(state.message ?: "Firebase permission denied", color = MaterialTheme.colorScheme.error)
        }
        LoadStatus.OFFLINE -> StatusPane { Text("Offline: ${state.message.orEmpty()}") }
        LoadStatus.ERROR -> StatusPane {
            Text(state.message ?: "Unable to load home", color = MaterialTheme.colorScheme.error)
        }
        LoadStatus.READY -> {
            val snapshot = state.snapshot ?: return
            val temperatureHistory = snapshot.nodes.flatMap(NodeSummary::readings)
                .sortedBy { it.timestampEpochMs() }
                .mapNotNull { reading ->
                    val metric = environmentMetricSpecs.first().keys.firstNotNullOfOrNull(reading.metrics::get)
                    metric?.takeIf(MetricReading::isValid)?.value
                }
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        "${snapshot.rooms.size} rooms · ${snapshot.nodes.size} nodes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item {
                    HistoryCard("Temperature history", temperatureHistory)
                }
                items(snapshot.nodes, key = NodeSummary::nodeId) { node ->
                    EnvironmentNodeCard(node) { onNavigateToNodeDetails(node.nodeId) }
                }
                item {
                    SectionTitle("Access events")
                }
                if (snapshot.accessEvents.isEmpty()) {
                    item { EmptyCard("No access events") }
                } else {
                    items(snapshot.accessEvents.take(20), key = AccessEvent::eventId) { event ->
                        AccessEventCard(event)
                    }
                }
                item {
                    SectionTitle("Camera detections")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${snapshot.detectionEvents.size} recorded human/fall events")
                            Text(
                                "Live preview is blocked until Phase 4 gateway RTSP runtime validation passes. " +
                                    "No fake frames are displayed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            Button(onClick = onNavigateToCamera) { Text("View detection history") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentNodeCard(node: NodeSummary, onClick: () -> Unit) {
    val latest = node.latestReading
    val stale = latest?.isStale(System.currentTimeMillis()) ?: true
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(node.label, fontWeight = FontWeight.Bold)
                    Text("${node.roomId} · ${node.nodeType} · ${node.nodeId}", style = MaterialTheme.typography.bodySmall)
                }
                Text(if (stale) "STALE" else node.status.uppercase(), color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            environmentMetricSpecs.forEach { spec ->
                val metric = spec.keys.firstNotNullOfOrNull { latest?.metrics?.get(it) }
                MetricRow(spec, metric, stale)
            }
        }
    }
}

@Composable
private fun MetricRow(spec: MetricSpec, metric: MetricReading?, stale: Boolean) {
    val display = when {
        metric == null -> "Missing"
        !metric.isValid -> "${metric.validity}${metric.error?.let { ": $it" }.orEmpty()}"
        metric.calibrated == false -> "${metric.value} ${metric.unit.ifBlank { spec.fallbackUnit }} · uncalibrated"
        stale -> "${metric.value} ${metric.unit.ifBlank { spec.fallbackUnit }} · stale"
        else -> "${metric.value} ${metric.unit.ifBlank { spec.fallbackUnit }}"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(spec.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(display, fontWeight = FontWeight.Medium, color = if (metric?.isValid == true && !stale) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun HistoryCard(title: String, values: List<Double>) {
    val lineColor = MaterialTheme.colorScheme.primary
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (values.isEmpty()) {
                Text("No valid measurements", modifier = Modifier.padding(top = 18.dp))
            } else {
                Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 16.dp)) {
                    val min = values.minOrNull() ?: return@Canvas
                    val max = values.maxOrNull() ?: return@Canvas
                    val range = (max - min).coerceAtLeast(1.0)
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = if (values.size == 1) size.width / 2f else size.width * index / (values.size - 1)
                        val y = size.height - ((value - min) / range * size.height).toFloat()
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(3.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun AccessEventCard(event: AccessEvent) {
    Card {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(event.result.uppercase(), fontWeight = FontWeight.Bold)
                Text("${event.credentialType} · ${event.nodeId} · ${event.roomId}")
            }
            Text(formatEventTime(event.timestampEpochMs))
        }
    }
}

@Composable
private fun StatusPane(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyCard(text: String) {
    Card { Text(text, Modifier.fillMaxWidth().padding(18.dp)) }
}

@Preview(showBackground = true)
@Composable
fun HomeTabPreview() {
    TuyaTheme {
        HomeTab(
            state = FakeData.sampleHomeUiState,
            onNavigateToNodeDetails = {},
            onNavigateToCamera = {}
        )
    }
}
