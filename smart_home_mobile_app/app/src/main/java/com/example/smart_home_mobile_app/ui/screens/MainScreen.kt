package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smart_home_mobile_app.ui.SmartHomeAppController
import com.example.smart_home_mobile_app.data.*
import com.example.smart_home_mobile_app.auth.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import com.example.smart_home_mobile_app.ui.TuyaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    controller: SmartHomeAppController,
    onNavigateToNodeDetails: (String) -> Unit,
    onNavigateToCamera: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddHome by remember { mutableStateOf(false) }
    var newHomeId by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val commandMessage = controller.commandMessage
    LaunchedEffect(commandMessage) {
        if (commandMessage != null) {
            snackbar.showSnackbar(commandMessage)
            controller.clearCommandMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (controller.previewMode) "Smart Home UI Preview" else controller.homeState.snapshot?.home?.displayName ?: "Smart Home")
                        Text(
                            if (controller.previewMode) "No Firebase session or device access" else controller.selectedHomeId ?: "No home selected",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    if (controller.previewMode) {
                        TextButton(onClick = onNavigateToCamera) { Text("Detections") }
                        TextButton(onClick = controller::exitPreviewMode) { Text("Exit") }
                    } else if (controller.homeIds.size > 1) {
                        controller.homeIds.forEach { homeId ->
                            TextButton(onClick = { controller.selectHome(homeId) }) { Text(homeId) }
                        }
                    }
                    if (!controller.previewMode) {
                        TextButton(onClick = { showAddHome = true }) { Text("Add home") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Overview") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Rooms") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Account") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> HomeTab(controller.homeState, onNavigateToNodeDetails, onNavigateToCamera)
                1 -> RoomsTab(controller.homeState, onNavigateToNodeDetails)
                else -> MeTab(
                    email = if (controller.previewMode) "Debug preview" else controller.authState.user?.email.orEmpty(),
                    role = controller.homeState.snapshot?.home?.role.orEmpty(),
                    homeIds = controller.homeIds,
                    onRemoveHome = if (controller.previewMode) ({}) else controller::removeSelectedHome,
                    onLogout = if (controller.previewMode) controller::exitPreviewMode else controller::logout,
                )
            }
        }
    }

    if (showAddHome && !controller.previewMode) {
        AlertDialog(
            onDismissRequest = { showAddHome = false },
            title = { Text("Add an existing home") },
            text = {
                OutlinedTextField(
                    value = newHomeId,
                    onValueChange = { newHomeId = it },
                    label = { Text("Home ID") },
                    supportingText = { Text("Membership is verified by Firebase RTDB rules.") },
                )
            },
            confirmButton = {
                Button(onClick = {
                    controller.addHome(newHomeId)
                    showAddHome = false
                    newHomeId = ""
                }) { Text("Add") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddHome = false }) { Text("Cancel") }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val context = LocalContext.current
    val controller = remember {
        SmartHomeAppController(context, isPreviewMode = true)
    }
    TuyaTheme {
        MainScreen(
            controller = controller,
            onNavigateToNodeDetails = {},
            onNavigateToCamera = {}
        )
    }
}


