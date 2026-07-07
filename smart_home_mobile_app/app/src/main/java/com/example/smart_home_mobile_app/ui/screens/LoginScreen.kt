package com.example.smart_home_mobile_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_home_mobile_app.ui.AuthStatus
import com.example.smart_home_mobile_app.ui.AuthUiState
import androidx.compose.ui.tooling.preview.Preview
import com.example.smart_home_mobile_app.ui.TuyaTheme

@Composable
fun LoginScreen(
    state: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit,
    previewEnabled: Boolean,
    onPreview: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    val busy = state.status == AuthStatus.LOADING
    val providersEnabled = !busy && state.status != AuthStatus.CONFIG_REQUIRED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Smart Home", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            if (registering) "Create a Firebase account" else "Sign in to your homes",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        state.message?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                if (registering) onRegister(email, password) else onSignIn(email, password)
            },
            enabled = !busy && state.status != AuthStatus.CONFIG_REQUIRED,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            if (busy) CircularProgressIndicator(Modifier.height(24.dp), strokeWidth = 2.dp)
            else Text(if (registering) "Register" else "Sign in", fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            OutlinedButton(onClick = { registering = !registering }, enabled = !busy) {
                Text(if (registering) "Use existing account" else "Create account")
            }
        }
        Text(
            "or continue with",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = providersEnabled,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("Continue with Google")
        }
        OutlinedButton(
            onClick = onAppleSignIn,
            enabled = providersEnabled,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
        ) {
            Text("Continue with Apple")
        }
        if (previewEnabled) {
            OutlinedButton(
                onClick = onPreview,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text("Preview main interface (debug only)")
            }
            Text(
                "Preview mode does not authenticate, read Firebase, or enable device commands.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    TuyaTheme {
        LoginScreen(
            state = AuthUiState(AuthStatus.SIGNED_OUT),
            onSignIn = { _, _ -> },
            onRegister = { _, _ -> },
            onGoogleSignIn = {},
            onAppleSignIn = {},
            previewEnabled = true,
            onPreview = {}
        )
    }
}

