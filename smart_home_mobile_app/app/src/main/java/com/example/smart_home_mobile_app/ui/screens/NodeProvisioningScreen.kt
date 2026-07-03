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
fun NodeProvisioningScreen(onBack: () -> Unit, onProvisionSuccess: () -> Unit) {
    var nodeId by remember { mutableStateOf("") }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Node") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
            Text("1. Scanning for BLE/Wi-Fi devices...", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = nodeId, onValueChange = { nodeId = it }, label = { Text("Discovered Node ID") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = wifiSsid, onValueChange = { wifiSsid = it }, label = { Text("Wi-Fi SSID") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = wifiPassword, onValueChange = { wifiPassword = it }, label = { Text("Wi-Fi Password") }, modifier = Modifier.fillMaxWidth())
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onProvisionSuccess,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = nodeId.isNotBlank() && wifiSsid.isNotBlank()
            ) {
                Text("Provision Node")
            }
        }
    }
}
