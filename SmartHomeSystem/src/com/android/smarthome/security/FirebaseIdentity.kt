package com.android.smarthome.security

import android.util.Base64
import com.android.smarthome.data.AuthUser
import org.json.JSONObject

object FirebaseIdentity {
    fun fromSession(session: FirebaseAuthSession?): AuthUser? {
        val token = session?.idToken ?: return null
        return try {
            val parts = token.split('.')
            require(parts.size == 3)
            val payload = JSONObject(String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP)))
            val uid = payload.optString("user_id").ifBlank { payload.optString("sub") }
            if (uid.isBlank()) null else AuthUser(uid, payload.optString("email").ifBlank { null })
        } catch (_: Exception) {
            null
        }
    }
}

object AuthValidation {
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun validate(email: String?, password: String?): String? {
        if (!emailPattern.matches(email.orEmpty().trim())) return "Enter a valid email address"
        if (password == null || password.length < 6) return "Password must contain at least 6 characters"
        return null
    }
}

