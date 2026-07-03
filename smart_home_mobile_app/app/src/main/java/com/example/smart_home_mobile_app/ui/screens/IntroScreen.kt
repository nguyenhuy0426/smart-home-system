package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IntroScreen(onNavigateToLogin: () -> Unit) {
    var currentSlide by remember { mutableStateOf(0) }
    
    val slides = listOf(
        IntroSlideData(
            title = "Smart Home",
            description = "Vietnam/English",
            icon = Icons.Default.Home,
            buttonText = "Get Started",
            showDropdown = true
        ),
        IntroSlideData(
            title = "Try Smart Home now.",
            description = "Connect and control all your devices in one place.",
            icon = Icons.Default.Refresh,
            buttonText = "Next"
        ),
        IntroSlideData(
            title = "Usage Report",
            description = "Analyze the energy usage of devices.",
            icon = Icons.Default.Info,
            buttonText = "Next"
        ),
        IntroSlideData(
            title = "Smart Routines",
            description = "Choose routines that fit your lifestyle.",
            icon = Icons.Default.Star,
            buttonText = "Sign In"
        )
    )

    val slide = slides[currentSlide]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        // Vietnam/English Dropdown mock at the top of Slide 0
        if (slide.showDropdown) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.DarkGray.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Vietnam/English ⌵", color = Color.White, fontSize = 14.sp)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Placeholder Image Box
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.DarkGray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = slide.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = slide.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = slide.description,
                color = Color.Gray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            // Dot indicators
            if (currentSlide > 0) {
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 1..3) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (currentSlide == i) Color.White else Color.Gray)
                        )
                    }
                }
            }
        }

        // Bottom buttons
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            if (currentSlide == 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { currentSlide-- },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Previous", color = Color.White, fontSize = 16.sp)
                    }
                    Button(
                        onClick = { currentSlide++ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Next", color = Color.White, fontSize = 16.sp)
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (slide.buttonText == "Sign In") {
                            onNavigateToLogin()
                        } else {
                            currentSlide++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(slide.buttonText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class IntroSlideData(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val buttonText: String,
    val showDropdown: Boolean = false
)
