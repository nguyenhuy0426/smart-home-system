package com.android.smarthome.gateway

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies HMAC-SHA256 signatures and rejects replays for node telemetry ingest.
 *
 * Contract shared with the ESP32 firmware (`ingest_auth.c` on the nodes):
 *
 *   X-Auth-Timestamp: Unix epoch seconds, decimal ASCII
 *   X-Auth-Nonce:     random per-request token, [A-Za-z0-9_-]{8,64}
 *   X-Auth-Signature: lowercase hex HMAC-SHA256 over
 *                     "<timestamp>\n<nonce>\n" + raw request body,
 *                     keyed with the ASCII bytes of the shared secret
 *
 * The shared secret is `ingest_hmac_secret` in the gateway secrets file and
 * the `auth_key` NVS value on each node (see CONFIG_REQUIRED.md). Requests
 * outside +/- [MAX_CLOCK_SKEW_SECONDS] of gateway time and nonces already
 * seen inside that window are rejected. With no secret configured the
 * authenticator fails closed.
 *
 * This class deliberately has no Android dependencies so it is covered by the
 * host-side unit tests.
 */
class IngestAuthenticator(
    private val secretProvider: () -> String?,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
) {

    enum class Failure {
        NOT_CONFIGURED,
        MISSING_HEADER,
        MALFORMED_HEADER,
        STALE_TIMESTAMP,
        REPLAYED_NONCE,
        BAD_SIGNATURE,
    }

    sealed class Result {
        object Success : Result()
        data class Rejected(val failure: Failure, val message: String) : Result()
    }

    /** nonce -> epoch second after which the entry can be dropped. */
    private val seenNonces = LinkedHashMap<String, Long>()

    @Volatile
    private var cachedSecret: ByteArray? = null

    companion object {
        const val TIMESTAMP_HEADER = "x-auth-timestamp"
        const val NONCE_HEADER = "x-auth-nonce"
        const val SIGNATURE_HEADER = "x-auth-signature"
        const val MAX_CLOCK_SKEW_SECONDS = 300L
        private const val MIN_SECRET_LENGTH = 32
        private const val MAX_TRACKED_NONCES = 10_000
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private val NONCE_PATTERN = Regex("[A-Za-z0-9_-]{8,64}")
        private val SIGNATURE_PATTERN = Regex("[0-9a-fA-F]{64}")
    }

    fun verify(
        timestamp: String?,
        nonce: String?,
        signature: String?,
        body: ByteArray,
    ): Result {
        val secret = loadSecret()
            ?: return Result.Rejected(
                Failure.NOT_CONFIGURED,
                "Ingest authentication is not configured",
            )
        if (timestamp.isNullOrEmpty() || nonce.isNullOrEmpty() || signature.isNullOrEmpty()) {
            return Result.Rejected(Failure.MISSING_HEADER, "Missing authentication headers")
        }
        val requestTime = timestamp.toLongOrNull()
            ?: return Result.Rejected(Failure.MALFORMED_HEADER, "Malformed timestamp")
        if (!NONCE_PATTERN.matches(nonce)) {
            return Result.Rejected(Failure.MALFORMED_HEADER, "Malformed nonce")
        }
        if (!SIGNATURE_PATTERN.matches(signature)) {
            return Result.Rejected(Failure.MALFORMED_HEADER, "Malformed signature")
        }
        val now = clock()
        if (requestTime < now - MAX_CLOCK_SKEW_SECONDS ||
            requestTime > now + MAX_CLOCK_SKEW_SECONDS) {
            return Result.Rejected(Failure.STALE_TIMESTAMP, "Timestamp outside accepted window")
        }
        // The signature covers the exact header strings, not re-serialized values.
        val expected = computeMac(secret, timestamp, nonce, body)
        if (!MessageDigest.isEqual(expected, decodeHex(signature))) {
            return Result.Rejected(Failure.BAD_SIGNATURE, "Signature verification failed")
        }
        // Record the nonce only after the signature checks out so unauthenticated
        // traffic cannot poison the replay cache.
        if (!recordNonce(nonce, requestTime + MAX_CLOCK_SKEW_SECONDS, now)) {
            return Result.Rejected(Failure.REPLAYED_NONCE, "Nonce already used")
        }
        return Result.Success
    }

    private fun loadSecret(): ByteArray? {
        cachedSecret?.let { return it }
        val secret = secretProvider()?.trim().orEmpty()
        if (secret.length < MIN_SECRET_LENGTH) return null
        val bytes = secret.toByteArray(StandardCharsets.US_ASCII)
        cachedSecret = bytes
        return bytes
    }

    private fun computeMac(
        secret: ByteArray,
        timestamp: String,
        nonce: String,
        body: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret, HMAC_ALGORITHM))
        mac.update("$timestamp\n$nonce\n".toByteArray(StandardCharsets.US_ASCII))
        mac.update(body)
        return mac.doFinal()
    }

    /** Returns false when the nonce was already accepted inside the window. */
    private fun recordNonce(nonce: String, expiresAt: Long, now: Long): Boolean {
        synchronized(seenNonces) {
            // Insertion order is not expiry order (timestamps may skew both ways),
            // so scan the whole map; it is capped at MAX_TRACKED_NONCES entries.
            val expired = seenNonces.entries.iterator()
            while (expired.hasNext()) {
                if (expired.next().value < now) expired.remove()
            }
            if (seenNonces.containsKey(nonce)) return false
            if (seenNonces.size >= MAX_TRACKED_NONCES) {
                // Window is saturated; dropping the oldest entry keeps the gateway
                // responsive at the cost of a slightly shorter replay horizon.
                val eldest = seenNonces.entries.iterator()
                if (eldest.hasNext()) {
                    eldest.next()
                    eldest.remove()
                }
            }
            seenNonces[nonce] = expiresAt
            return true
        }
    }

    private fun decodeHex(value: String): ByteArray {
        val bytes = ByteArray(value.length / 2)
        for (index in bytes.indices) {
            bytes[index] = value.substring(2 * index, 2 * index + 2).toInt(16).toByte()
        }
        return bytes
    }
}
