package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
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
import com.example.smart_home_mobile_app.data.HomeUiState
import androidx.compose.ui.tooling.preview.Preview
import com.example.smart_home_mobile_app.ui.TuyaTheme
import com.example.smart_home_mobile_app.data.FakeData


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailsScreen(
    nodeId: String,
    state: HomeUiState,
    role: String,
    onCommand: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val node = state.snapshot?.nodes?.firstOrNull { it.nodeId == nodeId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(node?.label ?: "Node") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (node == null) {
            Text("Node not found", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(node.nodeId, fontWeight = FontWeight.Bold)
                    Text("Type: ${node.nodeType}")
                    Text("Room: ${node.roomId}")
                    Text("Schema: ${node.schemaVersion}")
                    Text("Status: ${node.status}")
                    Text("Readings: ${node.readings.size}")
                }
            }
            Text("Latest telemetry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val latest = node.latestReading
            if (latest == null || latest.metrics.isEmpty()) {
                Text("No telemetry has been received")
            } else {
                latest.metrics.values.sortedBy { it.key }.forEach { metric ->
                    Card {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(metric.key, fontWeight = FontWeight.SemiBold)
                                Text("${metric.source} · ${metric.validity}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (metric.isValid) "${metric.value} ${metric.unit}" else metric.error ?: "Invalid")
                        }
                    }
                }
            }
            Text("Device actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (node.actions.isEmpty()) {
                Text("No actions are declared by this node descriptor")
            } else {
                node.actions.forEach { action ->
                    val accessAction = action == "unlock" || action == "open_door"
                    Button(
                        onClick = { onCommand(node.nodeId, action) },
                        enabled = !accessAction || role == "access_admin",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Request ${action.replace('_', ' ')}")
                    }
                    if (accessAction) {
                        Text(
                            "This creates an RTDB command request; it never unlocks a door directly. " +
                                "The gateway must authorize it.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NodeDetailsScreenPreview() {
    TuyaTheme {
        NodeDetailsScreen(
            nodeId = "node_1",
            state = FakeData.sampleHomeUiState,
            role = "owner",
            onCommand = { _, _ -> },
            onBack = {}
        )
    }
}


