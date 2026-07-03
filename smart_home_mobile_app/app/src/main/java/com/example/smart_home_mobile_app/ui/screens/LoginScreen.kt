package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var linkingPlatform by remember { mutableStateOf<String?>(null) }
    var linkingStep by remember { mutableStateOf(0) } // 0: Init, 1: Connecting, 2: Linked

    LaunchedEffect(linkingPlatform) {
        if (linkingPlatform != null) {
            linkingStep = 1
            delay(1500) // Simulate connection delay
            linkingStep = 2
            delay(1000) // Show success confirmation
            onLoginSuccess()
            linkingPlatform = null
            linkingStep = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Smart Home",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "If you are not logged in, you must register an account or log in using Gmail, Facebook, or Apple account.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = { linkingPlatform = "Gmail" },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA4335)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Email, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log in with Gmail", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { linkingPlatform = "Facebook" },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log in with Facebook", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { linkingPlatform = "Apple" },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Log in with Apple", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Overlay dialog to simulate account linking
        if (linkingPlatform != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (linkingStep == 1) {
                            CircularProgressIndicator(color = Color(0xFF4F46E5))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Linking with your $linkingPlatform account...",
                                color = Color.White,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        } else if (linkingStep == 2) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.Green,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Successfully Linked to $linkingPlatform!",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Access granted to main app interface.",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
