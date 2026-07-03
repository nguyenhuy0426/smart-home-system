package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailsScreen(nodeId: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Node Details: $nodeId") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
            Text("Environmental Parameters Graph", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            SimpleLineGraph(modifier = Modifier.fillMaxWidth().height(200.dp))
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("Node Connection Information", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Status: Connected")
            Text("IP Address: 192.168.1.150")
            Text("Firmware Version: 1.2.0")
        }
    }
}
