package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(onBack: () -> Unit) {
    var message by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf("AI: Hello! I am your Smart Home Assistant. How can I help you today?")) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gemini Home Assistant") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about your home...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (message.isNotBlank()) {
                            chatHistory = chatHistory + "You: $message"
                            chatHistory = chatHistory + "AI: Simulating Gemini response for '$message'..."
                            message = ""
                        }
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            items(chatHistory) { msg ->
                val isUser = msg.startsWith("You:")
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(0.8f)
                    ) {
                        Text(
                            text = msg.removePrefix("You: ").removePrefix("AI: "),
                            modifier = Modifier.padding(16.dp),
                            color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
