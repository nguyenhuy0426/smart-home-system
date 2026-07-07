package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smart_home_mobile_app.data.HomeUiState
import com.example.smart_home_mobile_app.data.LoadStatus
import com.example.smart_home_mobile_app.data.NodeSummary
import androidx.compose.ui.tooling.preview.Preview
import com.example.smart_home_mobile_app.ui.TuyaTheme
import com.example.smart_home_mobile_app.data.FakeData


@Composable
fun RoomsTab(state: HomeUiState, onNodeSelected: (String) -> Unit) {
    val snapshot = state.snapshot
    if (state.status !in setOf(LoadStatus.READY, LoadStatus.EMPTY) || snapshot == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) { Text(state.message ?: "Loading rooms…") }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Rooms & nodes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (snapshot.rooms.isEmpty()) item { Text("No rooms have been reported") }
        items(snapshot.rooms, key = { it.roomId }) { room ->
            val roomNodes = snapshot.nodes.filter { it.roomId == room.roomId }
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(room.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(room.roomId, style = MaterialTheme.typography.bodySmall)
                    if (roomNodes.isEmpty()) Text("No nodes in this room")
                    roomNodes.forEach { node -> NodeRow(node) { onNodeSelected(node.nodeId) } }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(node: NodeSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(node.label, fontWeight = FontWeight.SemiBold)
            Text("${node.nodeType} · ${node.nodeId}", style = MaterialTheme.typography.bodySmall)
        }
        Text(node.status)
    }
}

@Preview(showBackground = true)
@Composable
fun RoomsTabPreview() {
    TuyaTheme {
        RoomsTab(
            state = FakeData.sampleHomeUiState,
            onNodeSelected = {}
        )
    }
}


