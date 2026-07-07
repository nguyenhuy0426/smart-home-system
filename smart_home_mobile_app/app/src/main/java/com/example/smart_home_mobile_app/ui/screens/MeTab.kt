package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.smart_home_mobile_app.ui.TuyaTheme


@Composable
fun MeTab(
    email: String,
    role: String,
    homeIds: List<String>,
    onRemoveHome: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Account", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(email.ifBlank { "Firebase account" }, fontWeight = FontWeight.SemiBold)
                Text("Current role: ${role.ifBlank { "not verified" }}")
                Text("Configured homes: ${homeIds.joinToString().ifBlank { "none" }}")
            }
        }
        OutlinedButton(onClick = onRemoveHome, enabled = homeIds.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Remove selected home")
        }
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
        Text(
            "Logout clears the Firebase session and the app's Keystore-encrypted session metadata.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MeTabPreview() {
    TuyaTheme {
        MeTab(
            email = "demo@example.com",
            role = "access_admin",
            homeIds = listOf("home_123", "home_456"),
            onRemoveHome = {},
            onLogout = {}
        )
    }
}


