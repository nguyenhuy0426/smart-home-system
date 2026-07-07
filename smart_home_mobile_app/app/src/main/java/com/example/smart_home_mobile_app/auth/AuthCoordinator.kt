package com.example.smart_home_mobile_app.auth

data class AuthUser(val uid: String, val email: String?)

sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult
    data class Failure(val message: String) : AuthResult
}

interface AuthAdapter {
    fun currentUser(): AuthUser?
    fun signIn(email: String, password: String, callback: (AuthResult) -> Unit)
    fun register(email: String, password: String, callback: (AuthResult) -> Unit)
    fun signOut()
}

interface SessionStore {
    fun save(user: AuthUser)
    fun load(): AuthUser?
    fun clear()
}

class AuthCoordinator(
    private val adapter: AuthAdapter,
    private val sessionStore: SessionStore,
) {
    fun restoredUser(): AuthUser? {
        val firebaseUser = adapter.currentUser() ?: run {
            sessionStore.clear()
            return null
        }
        val persisted = sessionStore.load()
        if (persisted?.uid != firebaseUser.uid) sessionStore.save(firebaseUser)
        return firebaseUser
    }

    fun signIn(email: String, password: String, callback: (AuthResult) -> Unit) {
        validate(email, password)?.let {
            callback(AuthResult.Failure(it))
            return
        }
        adapter.signIn(email.trim(), password) { result ->
            if (result is AuthResult.Success) sessionStore.save(result.user)
            callback(result)
        }
    }

    fun register(email: String, password: String, callback: (AuthResult) -> Unit) {
        validate(email, password)?.let {
            callback(AuthResult.Failure(it))
            return
        }
        adapter.register(email.trim(), password) { result ->
            if (result is AuthResult.Success) sessionStore.save(result.user)
            callback(result)
        }
    }

    /**
     * Runs a federated provider flow (Google/Apple) supplied by [start] and
     * persists the session on success, mirroring the email/password paths.
     */
    fun signInWithProvider(
        start: (onResult: (AuthResult) -> Unit) -> Unit,
        callback: (AuthResult) -> Unit,
    ) {
        start { result ->
            if (result is AuthResult.Success) sessionStore.save(result.user)
            callback(result)
        }
    }

    fun logout() {
        adapter.signOut()
        sessionStore.clear()
    }

    private fun validate(email: String, password: String): String? = when {
        !email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) ->
            "Enter a valid email address"
        password.length < 6 -> "Password must contain at least 6 characters"
        else -> null
    }
}

