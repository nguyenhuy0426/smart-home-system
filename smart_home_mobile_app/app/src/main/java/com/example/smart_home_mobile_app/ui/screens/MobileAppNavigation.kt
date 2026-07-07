package com.example.smart_home_mobile_app.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smart_home_mobile_app.BuildConfig
import com.example.smart_home_mobile_app.ui.AuthStatus
import com.example.smart_home_mobile_app.ui.SmartHomeAppController
import com.example.smart_home_mobile_app.ui.TuyaTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

@Composable
fun MobileAppNavigation(controller: SmartHomeAppController) {
    TuyaTheme {
        if (controller.authState.status != AuthStatus.SIGNED_IN && !controller.previewMode) {
            val activity = LocalContext.current.findComponentActivity()
            LoginScreen(
                state = controller.authState,
                onSignIn = controller::signIn,
                onRegister = controller::register,
                onGoogleSignIn = { activity?.let(controller::signInWithGoogle) },
                onAppleSignIn = { activity?.let(controller::signInWithApple) },
                previewEnabled = BuildConfig.DEBUG,
                onPreview = controller::enterPreviewMode,
            )
            return@TuyaTheme
        }

        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainScreen(
                    controller = controller,
                    onNavigateToNodeDetails = { navController.navigate("node_details/$it") },
                    onNavigateToCamera = { navController.navigate("camera_events") },
                )
            }
            composable("node_details/{nodeId}") { entry ->
                NodeDetailsScreen(
                    nodeId = entry.arguments?.getString("nodeId").orEmpty(),
                    state = controller.homeState,
                    role = controller.homeState.snapshot?.home?.role.orEmpty(),
                    onCommand = controller::sendCommand,
                    onBack = navController::popBackStack,
                )
            }
            composable("camera_events") {
                CameraNodeScreen(
                    events = controller.homeState.snapshot?.detectionEvents.orEmpty(),
                    onBack = navController::popBackStack,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MobileAppNavigationPreview() {
    val context = LocalContext.current
    val controller = remember {
        SmartHomeAppController(context, isPreviewMode = true)
    }
    MobileAppNavigation(controller = controller)
}


