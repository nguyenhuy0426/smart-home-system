package com.android.smarthome.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Firebase credentials kept in app-private storage for background RTDB synchronization. */
data class FirebaseAuthSession(
    val idToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long
) {
    fun needsRefresh(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        return expiresAtEpochMs <= nowEpochMs + REFRESH_SKEW_MS
    }

    companion object {
        private const val REFRESH_SKEW_MS = 60_000L
        private const val MAX_TOKEN_LIFETIME_SECONDS = 86_400L

        /** Accepts both Identity Toolkit camelCase and Secure Token snake_case responses. */
        fun fromResponse(json: JSONObject, nowEpochMs: Long = System.currentTimeMillis()): FirebaseAuthSession {
            val idToken = json.optString("idToken").ifBlank { json.optString("id_token") }
            val refreshToken = json.optString("refreshToken")
                .ifBlank { json.optString("refresh_token") }
            val expiresText = json.optString("expiresIn")
                .ifBlank { json.optString("expires_in") }
            val expiresInSeconds = expiresText.toLongOrNull()
                ?: throw IllegalStateException("Firebase response did not include token expiry")

            require(idToken.isNotBlank()) { "Firebase response did not include an ID token" }
            require(refreshToken.isNotBlank()) {
                "Firebase response did not include a refresh token"
            }
            require(expiresInSeconds in 1..MAX_TOKEN_LIFETIME_SECONDS) {
                "Firebase response included an invalid token expiry"
            }

            return FirebaseAuthSession(
                idToken = idToken,
                refreshToken = refreshToken,
                expiresAtEpochMs = nowEpochMs + expiresInSeconds * 1_000L
            )
        }
    }
}

/** Small app-private credential store shared by login, dashboard, and the sync service. */
class FirebaseSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): FirebaseAuthSession? {
        val encoded = preferences.getString(KEY_ENCRYPTED_SESSION, null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > GCM_IV_BYTES)
            val iv = packed.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = packed.copyOfRange(GCM_IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            FirebaseAuthSession(
                idToken = json.getString("idToken"),
                refreshToken = json.getString("refreshToken"),
                expiresAtEpochMs = json.getLong("expiresAtEpochMs")
            ).takeIf {
                it.idToken.isNotBlank() && it.refreshToken.isNotBlank() && it.expiresAtEpochMs > 0L
            }
        } catch (_: Exception) {
            preferences.edit().remove(KEY_ENCRYPTED_SESSION).apply()
            null
        }
    }

    @Synchronized
    fun save(session: FirebaseAuthSession) {
        require(session.idToken.isNotBlank() && session.refreshToken.isNotBlank())
        require(session.expiresAtEpochMs > 0L)
        val plaintext = JSONObject().apply {
            put("idToken", session.idToken)
            put("refreshToken", session.refreshToken)
            put("expiresAtEpochMs", session.expiresAtEpochMs)
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        val packed = cipher.iv + ciphertext
        preferences.edit()
            .putString(KEY_ENCRYPTED_SESSION, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return@synchronized it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "smarthome_auth"
        private const val KEY_ENCRYPTED_SESSION = "firebase_session_v1"
        private const val KEY_ALIAS = "smarthome_firebase_session_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private val KEY_LOCK = Any()
    }
}
