package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smart_home_mobile_app.ui.TuyaTheme

@Composable
fun MobileAppNavigation() {
    val navController = rememberNavController()

    TuyaTheme {
        NavHost(navController = navController, startDestination = "intro") {
            composable("intro") {
                IntroScreen(
                    onNavigateToLogin = { navController.navigate("login") }
                )
            }
            composable("login") {
                LoginScreen(
                    onLoginSuccess = { navController.navigate("profile_setup") { popUpTo("login") { inclusive = true } } }
                )
            }
            composable("profile_setup") {
                ProfileSetupScreen(
                    onSetupComplete = { navController.navigate("main") { popUpTo("intro") { inclusive = true } } }
                )
            }
            composable("main") {
                MainScreen(
                    onNavigateToProvisioning = { navController.navigate("provisioning") },
                    onNavigateToNodeDetails = { nodeId -> navController.navigate("node_details/$nodeId") },
                    onNavigateToCamera = { navController.navigate("camera_view") },
                    onNavigateToChatbot = { navController.navigate("chatbot") },
                    onLogout = { navController.navigate("intro") { popUpTo(0) { inclusive = true } } }
                )
            }
            composable("provisioning") {
                NodeProvisioningScreen(
                    onBack = { navController.popBackStack() },
                    onProvisionSuccess = { navController.popBackStack() }
                )
            }
            composable("node_details/{nodeId}") { backStackEntry ->
                val nodeId = backStackEntry.arguments?.getString("nodeId") ?: "Unknown"
                NodeDetailsScreen(
                    nodeId = nodeId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("camera_view") {
                CameraNodeScreen(onBack = { navController.popBackStack() })
            }
            composable("chatbot") {
                ChatbotScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
