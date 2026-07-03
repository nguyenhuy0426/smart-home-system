package com.android.smarthome.security

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Performs provider authorization-code and Firebase credential exchanges on the gateway.
 *
 * Provider endpoints and credentials are deployment configuration. This class deliberately has
 * no mock-token fallback: authentication fails closed when configuration or the network is bad.
 */
class OAuthBackendHandler(private val tokenListener: TokenListener) {

    interface TokenListener {
        fun onTokenExchanged(session: FirebaseAuthSession)
        fun onTokenExchangeFailed(message: String) {}
    }

    data class ProviderConfig(
        val name: String,
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val clientId: String,
        val clientSecret: String,
        val redirectUri: String,
        val scope: String,
        val firebaseProviderId: String,
        val firebaseCredentialField: String,
        val pkceEnabled: Boolean
    )

    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val TAG = "OAuthBackendHandler"
        private const val SECRETS_PATH = "/data/secure/smarthome_oauth_secrets.json"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private val ALLOWED_PROVIDERS = setOf("google", "facebook", "apple")
        private val ALLOWED_CREDENTIAL_FIELDS = setOf("id_token", "access_token")

        fun loadSecrets(): JSONObject? {
            val file = File(SECRETS_PATH)
            if (!file.isFile || !file.canRead()) {
                Log.w(TAG, "OAuth configuration is unavailable at $SECRETS_PATH")
                return null
            }
            return try {
                JSONObject(file.readText(StandardCharsets.UTF_8))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse OAuth configuration", e)
                null
            }
        }

        fun loadProviderConfig(provider: String): ProviderConfig? {
            val normalized = provider.lowercase(Locale.US)
            if (normalized !in ALLOWED_PROVIDERS) return null
            val secrets = loadSecrets() ?: return null
            val providerJson = secrets.optJSONObject("oauth_providers")
                ?.optJSONObject(normalized) ?: return null

            val credentialField = providerJson.optString("firebase_credential_field")
            val config = ProviderConfig(
                name = normalized,
                authorizationEndpoint = providerJson.optString("authorization_endpoint"),
                tokenEndpoint = providerJson.optString("token_endpoint"),
                clientId = providerJson.optString("client_id"),
                clientSecret = providerJson.optString("client_secret"),
                redirectUri = providerJson.optString("redirect_uri"),
                scope = providerJson.optString("scope"),
                firebaseProviderId = providerJson.optString("firebase_provider_id"),
                firebaseCredentialField = credentialField,
                pkceEnabled = providerJson.optBoolean("pkce_enabled", true)
            )
            if (!isHttps(config.authorizationEndpoint) || !isHttps(config.tokenEndpoint)
                || config.clientId.isBlank() || config.redirectUri.isBlank()
                || config.scope.isBlank() || config.firebaseProviderId.isBlank()
                || credentialField !in ALLOWED_CREDENTIAL_FIELDS) {
                Log.e(TAG, "OAuth provider '$normalized' has incomplete or unsafe configuration")
                return null
            }
            return config
        }

        private fun isHttps(value: String): Boolean {
            return try {
                URL(value).protocol.equals("https", ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }
    }

    fun start() {
        if (loadSecrets() == null) {
            Log.w(TAG, "OAuth exchanges are disabled until secure configuration is installed")
        } else {
            Log.i(TAG, "OAuth token exchanger ready")
        }
    }

    fun stop() {
        executor.shutdownNow()
    }

    fun exchangeAuthorizationCode(
        provider: String,
        authorizationCode: String,
        codeVerifier: String?
    ) {
        if (authorizationCode.isBlank() || authorizationCode.length > 4096) {
            tokenListener.onTokenExchangeFailed("Invalid authorization code")
            return
        }
        executor.execute {
            try {
                val providerConfig = loadProviderConfig(provider)
                    ?: throw IllegalStateException("OAuth provider is not configured")
                if (providerConfig.pkceEnabled && codeVerifier.isNullOrBlank()) {
                    throw IllegalArgumentException("PKCE verifier is required")
                }
                val credential = exchangeProviderCode(
                    providerConfig,
                    authorizationCode,
                    codeVerifier
                )
                val firebaseSession = exchangeFirebaseCredential(providerConfig, credential)
                tokenListener.onTokenExchanged(firebaseSession)
            } catch (e: Exception) {
                Log.e(TAG, "OAuth exchange failed for provider '$provider'", e)
                tokenListener.onTokenExchangeFailed(e.message ?: "Authentication failed")
            }
        }
    }

    private fun exchangeProviderCode(
        config: ProviderConfig,
        authorizationCode: String,
        codeVerifier: String?
    ): String {
        val form = LinkedHashMap<String, String>()
        form["code"] = authorizationCode
        form["client_id"] = config.clientId
        if (config.clientSecret.isNotBlank()) form["client_secret"] = config.clientSecret
        form["redirect_uri"] = config.redirectUri
        form["grant_type"] = "authorization_code"
        if (!codeVerifier.isNullOrBlank()) form["code_verifier"] = codeVerifier

        val response = postForm(config.tokenEndpoint, encodeForm(form))
        val json = JSONObject(response)
        val credential = json.optString(config.firebaseCredentialField)
        if (credential.isBlank()) {
            throw IllegalStateException(
                "Provider response did not include ${config.firebaseCredentialField}"
            )
        }
        return credential
    }

    private fun exchangeFirebaseCredential(
        config: ProviderConfig,
        credential: String
    ): FirebaseAuthSession {
        val apiKey = loadSecrets()?.optString("firebase_api_key").orEmpty()
        if (apiKey.isBlank()) throw IllegalStateException("Firebase API key is not configured")

        val postBody = encodeForm(
            linkedMapOf(
                config.firebaseCredentialField to credential,
                "providerId" to config.firebaseProviderId
            )
        )
        val request = JSONObject().apply {
            put("postBody", postBody)
            put("requestUri", config.redirectUri)
            put("returnSecureToken", true)
        }
        val endpoint = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp" +
            "?key=" + urlEncode(apiKey)
        val response = postJson(endpoint, request.toString())
        return FirebaseAuthSession.fromResponse(JSONObject(response))
    }

    private fun postForm(endpoint: String, body: String): String {
        return executePost(endpoint, "application/x-www-form-urlencoded", body)
    }

    private fun postJson(endpoint: String, body: String): String {
        return executePost(endpoint, "application/json; charset=UTF-8", body)
    }

    private fun executePost(endpoint: String, contentType: String, body: String): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", contentType)
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use {
                it.write(body)
            }
            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (responseCode !in 200..299) {
                throw IllegalStateException("Token endpoint returned HTTP $responseCode")
            }
            return responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeForm(fields: Map<String, String>): String {
        return fields.entries.joinToString("&") { entry ->
            urlEncode(entry.key) + "=" + urlEncode(entry.value)
        }
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}
