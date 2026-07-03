package com.android.smarthome.security

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.smarthome.gateway.ui.DashboardActivity
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

class LoginRegisterActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LoginRegisterActivity"
        private const val OAUTH_PENDING_PREFS = "smarthome_oauth_pending"
        private const val KEY_PENDING_PROVIDER = "provider"
        private const val KEY_PENDING_STATE = "state"
        private const val KEY_PENDING_VERIFIER = "verifier"
        private const val KEY_PENDING_STARTED_AT = "started_at"
        private const val OAUTH_FLOW_TTL_MS = 10 * 60 * 1_000L
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var apiKey = ""
    private lateinit var oauthHandler: OAuthBackendHandler
    private lateinit var sessionStore: FirebaseSessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read API key
        val secrets = OAuthBackendHandler.loadSecrets()
        apiKey = secrets?.optString("firebase_api_key", "") ?: ""
        sessionStore = FirebaseSessionStore(this)
        oauthHandler = OAuthBackendHandler(object : OAuthBackendHandler.TokenListener {
            override fun onTokenExchanged(session: FirebaseAuthSession) {
                runOnUiThread {
                    try {
                        sessionStore.save(session)
                        proceedToDashboard()
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not protect the Firebase session", e)
                        Toast.makeText(
                            this@LoginRegisterActivity,
                            "Secure credential storage is unavailable",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun onTokenExchangeFailed(message: String) {
                runOnUiThread {
                    Toast.makeText(
                        this@LoginRegisterActivity,
                        "Sign-in failed. Check the provider configuration and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }).also { it.start() }

        // Check if already logged in
        if (sessionStore.load() != null) {
            proceedToDashboard()
            return
        }

        setContent {
            AuthScreen()
        }
        handleOAuthCallback(intent)
    }

    @Composable
    fun AuthScreen() {
        var isLogin by remember { mutableStateOf(true) }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }

        // Liquid Glass Theme: Warm Neutral Palette
        val backgroundBase = Color(0xFFFAF8F5) // Warm off-white
        val cardBackground = Color(0x99FFFFFF) // Translucent frosted white
        val textPrimary = Color(0xFF4A4540)    // Warm dark grey
        val textSecondary = Color(0xFF8A8078)  // Warm grey slate
        val accentTeal = Color(0xFF008080)     // Premium Teal
        val accentRose = Color(0xFFE98074)     // Soft Rose

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBase),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBackground)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Logo Title
                Text(
                    text = "SMART HOME",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentTeal,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Gateway Portal",
                    fontSize = 14.sp,
                    color = textSecondary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentTeal,
                        focusedLabelColor = accentTeal
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentTeal,
                        focusedLabelColor = accentTeal
                    )
                )

                if (!isLogin) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentTeal,
                            focusedLabelColor = accentTeal
                        )
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(color = accentTeal, modifier = Modifier.padding(16.dp))
                } else {
                    Button(
                        onClick = {
                            if (isLogin) {
                                performLogin(email, password) { isLoading = it }
                            } else {
                                if (password == confirmPassword) {
                                    performRegister(email, password) { isLoading = it }
                                } else {
                                    Toast.makeText(
                                        this@LoginRegisterActivity,
                                        "Passwords do not match",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentTeal)
                    ) {
                        Text(if (isLogin) "Sign In" else "Create Account")
                    }

                    // OAuth Options
                    Text("Or sign in with", color = textSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { triggerOAuth("google") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            modifier = Modifier.weight(1f).height(48.dp)
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        ) {
                            Text("Google", color = textPrimary)
                        }
                        Button(
                            onClick = { triggerOAuth("facebook") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            modifier = Modifier.weight(1f).height(48.dp)
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        ) {
                            Text("Facebook", color = textPrimary)
                        }
                        Button(
                            onClick = { triggerOAuth("apple") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            modifier = Modifier.weight(1f).height(48.dp)
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        ) {
                            Text("Apple", color = textPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = { isLogin = !isLogin }) {
                        Text(
                            text = if (isLogin) "Don't have an account? Sign Up" else "Already have an account? Sign In",
                            color = accentRose
                        )
                    }
                }
            }
        }
    }

    private fun performLogin(email: String, password: String, loadingSetter: (Boolean) -> Unit) {
        if (!validateCredentials(email, password)) return
        loadingSetter(true)
        executor.submit {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Content-Type", "application/json")

                val body = JSONObject().apply {
                    put("email", email.trim())
                    put("password", password)
                    put("returnSecureToken", true)
                }

                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                    it.write(body.toString())
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val session = FirebaseAuthSession.fromResponse(json)
                    sessionStore.save(session)
                    runOnUiThread { proceedToDashboard() }
                } else {
                    Log.w(TAG, "Login failed with HTTP ${conn.responseCode}")
                    runOnUiThread { 
                        Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                        loadingSetter(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login exception", e)
                runOnUiThread { 
                    Toast.makeText(this, "Network Error", Toast.LENGTH_SHORT).show()
                    loadingSetter(false)
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun performRegister(email: String, password: String, loadingSetter: (Boolean) -> Unit) {
        if (!validateCredentials(email, password)) return
        loadingSetter(true)
        executor.submit {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Content-Type", "application/json")

                val body = JSONObject().apply {
                    put("email", email.trim())
                    put("password", password)
                    put("returnSecureToken", true)
                }

                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                    it.write(body.toString())
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val session = FirebaseAuthSession.fromResponse(json)
                    sessionStore.save(session)
                    runOnUiThread { proceedToDashboard() }
                } else {
                    Log.w(TAG, "Registration failed with HTTP ${conn.responseCode}")
                    runOnUiThread { 
                        Toast.makeText(this, "Registration failed", Toast.LENGTH_SHORT).show()
                        loadingSetter(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Register exception", e)
                runOnUiThread { 
                    Toast.makeText(this, "Network Error", Toast.LENGTH_SHORT).show()
                    loadingSetter(false)
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun triggerOAuth(provider: String) {
        val config = OAuthBackendHandler.loadProviderConfig(provider)
        if (config == null) {
            Toast.makeText(this, "This sign-in provider is not configured", Toast.LENGTH_LONG).show()
            return
        }
        val state = randomBase64Url(32)
        val verifier = if (config.pkceEnabled) randomBase64Url(64) else ""
        getSharedPreferences(OAUTH_PENDING_PREFS, MODE_PRIVATE).edit()
            .putString(KEY_PENDING_PROVIDER, provider)
            .putString(KEY_PENDING_STATE, state)
            .putString(KEY_PENDING_VERIFIER, verifier)
            .putLong(KEY_PENDING_STARTED_AT, System.currentTimeMillis())
            .apply()

        val authorizationUri = Uri.parse(config.authorizationEndpoint).buildUpon()
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", config.scope)
            .appendQueryParameter("state", state)
            .apply {
                if (provider == "apple") appendQueryParameter("response_mode", "query")
                if (verifier.isNotEmpty()) {
                    val challenge = MessageDigest.getInstance("SHA-256")
                        .digest(verifier.toByteArray(Charsets.US_ASCII))
                    appendQueryParameter(
                        "code_challenge",
                        Base64.encodeToString(
                            challenge,
                            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                        )
                    )
                    appendQueryParameter("code_challenge_method", "S256")
                }
            }
            .build()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, authorizationUri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            })
        } catch (_: ActivityNotFoundException) {
            clearPendingOAuth()
            Toast.makeText(this, "No secure browser is available", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun handleOAuthCallback(callbackIntent: Intent): Boolean {
        val callback = callbackIntent.data ?: return false
        val pending = getSharedPreferences(OAUTH_PENDING_PREFS, MODE_PRIVATE)
        val provider = pending.getString(KEY_PENDING_PROVIDER, null) ?: return false
        val expectedState = pending.getString(KEY_PENDING_STATE, null) ?: return false
        val verifier = pending.getString(KEY_PENDING_VERIFIER, "").orEmpty()
        val startedAt = pending.getLong(KEY_PENDING_STARTED_AT, 0L)
        val config = OAuthBackendHandler.loadProviderConfig(provider)

        val validRedirect = config != null && matchesRedirect(callback, Uri.parse(config.redirectUri))
        val stateMatches = MessageDigest.isEqual(
            callback.getQueryParameter("state").orEmpty().toByteArray(StandardCharsets.UTF_8),
            expectedState.toByteArray(StandardCharsets.UTF_8)
        )
        val withinTtl = startedAt > 0L &&
            System.currentTimeMillis() - startedAt in 0..OAUTH_FLOW_TTL_MS
        if (!validRedirect || !stateMatches || !withinTtl) {
            Log.w(TAG, "Rejected invalid or expired OAuth callback")
            clearPendingOAuth()
            Toast.makeText(this, "The sign-in response was invalid or expired", Toast.LENGTH_LONG).show()
            return true
        }

        val code = callback.getQueryParameter("code")
        val error = callback.getQueryParameter("error")
        clearPendingOAuth()
        if (error != null || code.isNullOrBlank()) {
            Log.w(TAG, "OAuth provider returned an authorization error")
            Toast.makeText(this, "Sign-in was canceled or denied", Toast.LENGTH_LONG).show()
            return true
        }
        oauthHandler.exchangeAuthorizationCode(provider, code, verifier.ifBlank { null })
        return true
    }

    private fun matchesRedirect(actual: Uri, expected: Uri): Boolean {
        return actual.scheme == expected.scheme &&
            actual.authority == expected.authority &&
            actual.path == expected.path
    }

    private fun clearPendingOAuth() {
        getSharedPreferences(OAUTH_PENDING_PREFS, MODE_PRIVATE).edit().clear().apply()
    }

    private fun randomBase64Url(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun validateCredentials(email: String, password: String): Boolean {
        val error = when {
            apiKey.isBlank() -> "Firebase authentication is not configured"
            email.length > 254 -> "Email address is too long"
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address"
            password.length < 6 -> "Password must contain at least 6 characters"
            password.length > 128 -> "Password must contain at most 128 characters"
            else -> null
        }
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun proceedToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        if (::oauthHandler.isInitialized) oauthHandler.stop()
        executor.shutdownNow()
        super.onDestroy()
    }
}
