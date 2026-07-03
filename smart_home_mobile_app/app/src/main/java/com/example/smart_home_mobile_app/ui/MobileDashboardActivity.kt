package com.example.smart_home_mobile_app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.smart_home_mobile_app.ui.screens.MobileAppNavigation

class MobileDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileAppNavigation()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDashboard() {
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Icon(Icons.Default.List, contentDescription = "Scenes") },
                    label = { Text("Smart") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Me") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            HeaderSection()
            Spacer(modifier = Modifier.height(16.dp))
            RoomTabsSection()
            Spacer(modifier = Modifier.height(16.dp))
            DeviceGridSection()
        }
    }
}

@Composable
fun HeaderSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E88E5),
                        Color(0xFF1565C0)
                    )
                )
            )
            .padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 32.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Good Morning,",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Huynn",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Weather / Environment Widget
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EnvironmentItem("24°C", "Temp")
                EnvironmentItem("60%", "Humidity")
                EnvironmentItem("Excellent", "Air Quality")
            }
        }
    }
}

@Composable
fun EnvironmentItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
fun RoomTabsSection() {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("All Devices", "Living Room", "Bedroom", "Kitchen")
    
    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 24.dp,
        divider = {}
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { selectedTabIndex = index },
                text = {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            )
        }
    }
}

@Composable
fun DeviceGridSection() {
    val devices = listOf(
        DeviceModel("Smart Lamp", "Living Room", Icons.Default.AddCircle, true, Color(0xFFFFA000)),
        DeviceModel("Air Conditioner", "Living Room", Icons.Default.PlayArrow, false, Color(0xFF03A9F4)),
        DeviceModel("Air Purifier", "Bedroom", Icons.Default.Refresh, true, Color(0xFF4CAF50)),
        DeviceModel("Front Door", "Entrance", Icons.Default.Lock, false, Color(0xFFF44336))
    )
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(devices) { device ->
            DeviceCard(device)
        }
    }
}

data class DeviceModel(
    val name: String,
    val room: String,
    val icon: ImageVector,
    val isOn: Boolean,
    val activeColor: Color
)

@Composable
fun DeviceCard(device: DeviceModel) {
    var isOn by remember { mutableStateOf(device.isOn) }
    val animatedAlpha by animateFloatAsState(targetValue = if (isOn) 1f else 0.5f, label = "alpha")
    
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) device.activeColor.copy(alpha = 0.1f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .shadow(4.dp, RoundedCornerShape(24.dp), ambientColor = Color.LightGray, spotColor = Color.LightGray)
            .clickable { isOn = !isOn }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isOn) device.activeColor else Color(0xFFF0F0F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = device.icon,
                        contentDescription = device.name,
                        tint = if (isOn) Color.White else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Switch(
                    checked = isOn,
                    onCheckedChange = { isOn = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = device.activeColor
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }
            
            Column(modifier = Modifier.alpha(animatedAlpha)) {
                Text(
                    text = device.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isOn) "ON | ${device.room}" else "OFF | ${device.room}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

