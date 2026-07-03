package com.android.smarthome.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Keeps each home's non-exportable 256-bit HMAC key in Android Keystore. */
class KeystoreHomeSecretProvider : GatewaySecurityPolicy.HomeSecretProvider {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun hmacSha256(homeId: String, value: String): String {
        require(HOME_ID_PATTERN.matches(homeId)) { "homeId is invalid" }
        val key = getOrCreateKey(alias(homeId))
        val digest = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256).run {
            init(key)
            doFinal(value.toByteArray(Charsets.UTF_8))
        }
        return buildString(7 + digest.size * 2) {
            append("sha256:")
            digest.forEach { byte -> append(String.format("%02x", byte.toInt() and 0xff)) }
        }
    }

    @Synchronized
    private fun getOrCreateKey(keyAlias: String): SecretKey {
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun alias(homeId: String) = "smarthome.home.$homeId.hardware-fingerprint"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private val HOME_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
    }
}
