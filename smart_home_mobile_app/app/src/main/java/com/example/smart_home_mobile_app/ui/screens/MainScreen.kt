package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToProvisioning: () -> Unit,
    onNavigateToNodeDetails: (String) -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToChatbot: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Rooms") },
                    label = { Text("Rooms") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Me") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                Column {
                    FloatingActionButton(onClick = onNavigateToCamera, containerColor = MaterialTheme.colorScheme.secondary) {
                        Icon(Icons.Default.Place, contentDescription = "Camera", tint = Color.White) // Mocking Camera icon
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FloatingActionButton(onClick = onNavigateToChatbot, containerColor = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Default.Email, contentDescription = "AI Assistant", tint = Color.White) // Mocking Chat icon
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FloatingActionButton(onClick = onNavigateToProvisioning, containerColor = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Default.Add, contentDescription = "Add Node", tint = Color.White)
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> HomeTab(onNavigateToNodeDetails = onNavigateToNodeDetails)
                1 -> RoomsTab()
                2 -> MeTab(onLogout = onLogout)
            }
        }
    }
}
