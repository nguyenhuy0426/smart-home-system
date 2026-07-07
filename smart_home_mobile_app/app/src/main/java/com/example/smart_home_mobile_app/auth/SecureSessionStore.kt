package com.example.smart_home_mobile_app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSessionStore(context: Context) : SessionStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun save(user: AuthUser) = try {
        val plaintext = user.uid + "\n" + user.email.orEmpty()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    } catch (error: Exception) {
        // Keystore operations can fail at runtime (corrupted/invalidated key, vendor
        // KeyStoreException). Losing session persistence must never crash sign-in;
        // FirebaseAuth's own persisted user still restores the session on next launch.
        Log.w(TAG, "Failed to persist session; clearing stored session", error)
        clear()
    }

    override fun load(): AuthUser? = try {
        val iv = preferences.getString(KEY_IV, null) ?: return null
        val payload = preferences.getString(KEY_PAYLOAD, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        val plaintext = String(
            cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)),
            StandardCharsets.UTF_8,
        )
        val parts = plaintext.split('\n', limit = 2)
        parts.firstOrNull()?.takeIf(String::isNotBlank)?.let { uid ->
            AuthUser(uid, parts.getOrNull(1)?.takeIf(String::isNotBlank))
        }
    } catch (_: Exception) {
        clear()
        null
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SecureSessionStore"
        const val PREFERENCES = "secure_firebase_session"
        const val KEY_IV = "iv"
        const val KEY_PAYLOAD = "payload"
        const val KEY_ALIAS = "smart_home_mobile_session_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

