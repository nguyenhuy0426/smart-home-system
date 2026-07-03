package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun HomeTab(onNavigateToNodeDetails: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        HeaderSection()
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Environmental History (Click points to view values)", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
        SimpleLineGraph(modifier = Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 24.dp, vertical = 8.dp))
        
        Spacer(modifier = Modifier.height(8.dp))
        RoomTabsSection()
        Spacer(modifier = Modifier.height(16.dp))
        DeviceGridSection(onNavigateToNodeDetails)
    }
}

@Composable
fun SimpleLineGraph(modifier: Modifier = Modifier) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val points = listOf(22.5f, 23.0f, 22.8f, 24.2f, 23.8f, 25.0f, 24.5f)
    val times = listOf("08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00")
    val maxVal = points.maxOrNull() ?: 30f
    val minVal = points.minOrNull() ?: 20f
    val diff = (maxVal - minVal).coerceAtLeast(1f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val stepX = width / (points.size - 1)
                            var closestIdx = -1
                            var minDist = Float.MAX_VALUE
                            for (i in points.indices) {
                                val px = i * stepX
                                val dist = kotlin.math.abs(offset.x - px)
                                if (dist < minDist && dist < 40.dp.toPx()) {
                                    minDist = dist
                                    closestIdx = i
                                }
                            }
                            selectedIndex = if (closestIdx != -1) closestIdx else null
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val stepX = width / (points.size - 1)

                val path = Path()
                val coordinates = points.mapIndexed { i, valy ->
                    val ratio = (valy - minVal) / diff
                    val px = i * stepX
                    val py = height - (ratio * (height - 30.dp.toPx()) + 15.dp.toPx())
                    Offset(px, py)
                }

                if (coordinates.isNotEmpty()) {
                    path.moveTo(coordinates[0].x, coordinates[0].y)
                    for (i in 1 until coordinates.size) {
                        path.lineTo(coordinates[i].x, coordinates[i].y)
                    }
                    drawPath(path, color = primaryColor, style = Stroke(width = 3.dp.toPx()))

                    coordinates.forEachIndexed { i, offset ->
                        drawCircle(
                            color = if (selectedIndex == i) Color.Red else primaryColor,
                            radius = if (selectedIndex == i) 8.dp.toPx() else 5.dp.toPx(),
                            center = offset
                        )
                    }
                }
            }

            selectedIndex?.let { idx ->
                val valy = points[idx]
                val time = times[idx]
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$valy°C at $time",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
                    colors = listOf(Color(0xFF1E88E5), Color(0xFF1565C0))
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
                    Text(text = "Good Morning,", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                    Text(text = "Huynn", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                
                // Profile Avatar Button
                Button(
                    onClick = { /* Navigate to profile settings */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    // Modern styled initials as avatar visual
                    Text("HN", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
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
                EnvironmentItem("Connected", "Node Status")
            }
        }
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
                        fontSize = 14.sp,
                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            )
        }
    }
}

@Composable
fun DeviceGridSection(onNavigateToNodeDetails: (String) -> Unit) {
    val devices = listOf(
        DeviceModel("node_lamp_1", "Node 1", "Living Room", Icons.Default.AddCircle, true, Color(0xFFFFA000)),
        DeviceModel("node_ac_1", "Node 2", "Living Room", Icons.Default.PlayArrow, false, Color(0xFF03A9F4)),
        DeviceModel("node_air_1", "Node 3", "Bedroom", Icons.Default.Refresh, true, Color(0xFF4CAF50)),
        DeviceModel("node_door_1", "Node 4", "Entrance", Icons.Default.Lock, false, Color(0xFFF44336))
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(devices) { device ->
            DeviceCard(device) { onNavigateToNodeDetails(device.id) }
        }
    }
}

data class DeviceModel(
    val id: String,
    val name: String,
    val room: String,
    val icon: ImageVector,
    val isOn: Boolean,
    val activeColor: Color
)

@Composable
fun DeviceCard(device: DeviceModel, onClick: () -> Unit) {
    var isOn by remember { mutableStateOf(device.isOn) }
    val animatedAlpha by animateFloatAsState(targetValue = if (isOn) 1f else 0.5f, label = "alpha")
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (isOn) device.activeColor.copy(alpha = 0.1f) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .shadow(4.dp, RoundedCornerShape(24.dp), ambientColor = Color.LightGray, spotColor = Color.LightGray)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isOn) device.activeColor else Color(0xFFF0F0F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = device.icon, contentDescription = device.name, tint = if (isOn) Color.White else Color.Gray, modifier = Modifier.size(24.dp))
                }
                Switch(
                    checked = isOn,
                    onCheckedChange = { isOn = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = device.activeColor),
                    modifier = Modifier.scale(0.8f)
                )
            }
            Column(modifier = Modifier.alpha(animatedAlpha)) {
                Text(text = device.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(text = if (isOn) "ON | ${device.room}" else "OFF | ${device.room}", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun EnvironmentItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

