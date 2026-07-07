package com.example.smart_home_mobile_app.auth

import android.app.Activity
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider

class FirebaseAuthAdapter(private val auth: FirebaseAuth) : AuthAdapter {
    override fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

    override fun signIn(email: String, password: String, callback: (AuthResult) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                callback(if (user == null) {
                    AuthResult.Failure("Firebase returned no authenticated user")
                } else {
                    AuthResult.Success(user.toAuthUser())
                })
            }
            .addOnFailureListener { callback(AuthResult.Failure(it.safeMessage())) }
    }

    override fun register(email: String, password: String, callback: (AuthResult) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                callback(if (user == null) {
                    AuthResult.Failure("Firebase returned no registered user")
                } else {
                    AuthResult.Success(user.toAuthUser())
                })
            }
            .addOnFailureListener { callback(AuthResult.Failure(it.safeMessage())) }
    }

    fun signInWithGoogleIdToken(idToken: String, callback: (AuthResult) -> Unit) {
        signInWithCredential(GoogleAuthProvider.getCredential(idToken, null), callback)
    }

    fun signInWithApple(activity: Activity, callback: (AuthResult) -> Unit) {
        // The builder must be bound to this named FirebaseAuth instance: the default
        // FirebaseApp does not exist because FirebaseInitProvider is removed.
        val provider = OAuthProvider.newBuilder("apple.com", auth)
            .setScopes(listOf("email", "name"))
            .build()
        // A pending result exists when the custom-tab flow was interrupted (e.g. process
        // restart mid sign-in); consuming it avoids launching a second browser flow.
        val task = auth.pendingAuthResult ?: auth.startActivityForSignInWithProvider(activity, provider)
        task
            .addOnSuccessListener { result ->
                val user = result.user
                callback(if (user == null) {
                    AuthResult.Failure("Firebase returned no authenticated user")
                } else {
                    AuthResult.Success(user.toAuthUser())
                })
            }
            .addOnFailureListener { callback(AuthResult.Failure(it.webFlowMessage())) }
    }

    /**
     * The provider web flow validates this build's signing certificate against the
     * Firebase project; the SDK's raw message for that rejection ("…package certificate
     * hash…", INVALID_CERT_HASH) does not tell the user what to do about it.
     */
    private fun Throwable.webFlowMessage(): String {
        val raw = safeMessage()
        return if (raw.contains("certificate hash", ignoreCase = true)) {
            "Firebase rejected this build's signing certificate. Register the app's " +
                "SHA-1 and SHA-256 fingerprints in the Firebase console and make sure " +
                "the API key's Android restrictions match (see CONFIG_REQUIRED.md §2.1)."
        } else {
            raw
        }
    }

    private fun signInWithCredential(credential: AuthCredential, callback: (AuthResult) -> Unit) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                callback(if (user == null) {
                    AuthResult.Failure("Firebase returned no authenticated user")
                } else {
                    AuthResult.Success(user.toAuthUser())
                })
            }
            .addOnFailureListener { callback(AuthResult.Failure(it.safeMessage())) }
    }

    override fun signOut() = auth.signOut()

    private fun FirebaseUser.toAuthUser() = AuthUser(uid, email)

    private fun Throwable.safeMessage(): String = when {
        message.isNullOrBlank() -> "Firebase authentication failed"
        else -> message!!
    }
}
