package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoomsTab() {
    val rooms = listOf(
        RoomModel("Living Room", listOf("Smart Lamp", "Air Conditioner")),
        RoomModel("Bedroom", listOf("Air Purifier")),
        RoomModel("Kitchen", listOf("Smart Fridge", "Smart Oven")),
        RoomModel("Entrance", listOf("Front Door Lock", "Camera Node"))
    )

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Rooms & Nodes", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(rooms) { room ->
                RoomCard(room)
            }
        }
    }
}

data class RoomModel(val name: String, val nodes: List<String>)

@Composable
fun RoomCard(room: RoomModel) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(room.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            room.nodes.forEach { node ->
                Text("• $node", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
        }
    }
}
