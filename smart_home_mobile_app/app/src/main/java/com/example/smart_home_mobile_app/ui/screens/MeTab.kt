package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MeTab(onLogout: () -> Unit) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // Avatar Button Mock
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFFE5E7EB))
                .clickable { /* Edit avatar mock */ },
            contentAlignment = Alignment.Center
        ) {
            // Displaying a simple placeholder avatar or text
            Text("H", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Text("Huynn", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text("+1234567890", fontSize = 14.sp, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Account Block
        SettingsBlock(title = "Account") {
            SettingsRow(icon = Icons.Default.Lock, title = "Account Information and Security") { }
            SettingsRow(icon = Icons.Default.Info, title = "Login History") { }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Device & Settings Block
        SettingsBlock(title = "Device & App Settings") {
            SettingsRow(icon = Icons.Default.Build, title = "Device Data") { }
            SettingsRow(icon = Icons.Default.Notifications, title = "Notifications") { }
            SettingsRow(icon = Icons.Default.Menu, title = "Interface and Language") { }
            SettingsRow(icon = Icons.Default.Star, title = "App Information") { }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Account Switch and Logout
        SettingsBlock(title = "System") {
            SettingsRow(icon = Icons.Default.Refresh, title = "Switch Accounts") { }
            SettingsRow(
                icon = Icons.Default.ExitToApp,
                title = "Log Out",
                textColor = Color.Red,
                iconColor = Color.Red,
                onClick = onLogout
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun SettingsBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    textColor: Color = Color.Black,
    iconColor: Color = Color.Gray,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.LightGray
        )
    }
}
