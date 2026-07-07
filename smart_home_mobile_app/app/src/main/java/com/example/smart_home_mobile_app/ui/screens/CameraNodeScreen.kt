package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smart_home_mobile_app.data.DetectionEvent
import com.example.smart_home_mobile_app.ui.formatEventTime
import androidx.compose.ui.tooling.preview.Preview
import com.example.smart_home_mobile_app.ui.TuyaTheme
import com.example.smart_home_mobile_app.data.FakeData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraNodeScreen(events: List<DetectionEvent>, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detection history") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Text(
                        "Live preview is unavailable until Phase 4 RTSP runtime validation passes. " +
                            "This screen only shows real RTDB detection events.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (events.isEmpty()) item { Text("No human or fall detections") }
            items(events, key = DetectionEvent::eventId) { event ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(event.className, fontWeight = FontWeight.Bold)
                            Text("${(event.confidence * 100).toInt()}%")
                        }
                        Text("Camera ${event.cameraNodeId} · Room ${event.roomId}")
                        Text(formatEventTime(event.timestampEpochMs))
                        event.boundingBox?.let {
                            Text(
                                "Box: ${it.left.toInt()}, ${it.top.toInt()} — ${it.right.toInt()}, ${it.bottom.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CameraNodeScreenPreview() {
    TuyaTheme {
        CameraNodeScreen(
            events = listOf(FakeData.sampleDetectionEvent),
            onBack = {}
        )
    }
}

