package com.example.smart_home_mobile_app.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Obtains a Google ID token through the Credential Manager "Sign in with Google" flow.
 * The token is exchanged for a Firebase session by [FirebaseAuthAdapter.signInWithGoogleIdToken].
 */
class GoogleCredentialSignIn(private val serverClientId: String) {

    fun start(activity: Activity, onToken: (String) -> Unit, onError: (String) -> Unit) {
        // Button-triggered flow: unlike GetGoogleIdOption (bottom sheet), this shows the
        // full Sign in with Google dialog, which can add an account when none exists.
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()
        CredentialManager.create(activity).getCredentialAsync(
            activity,
            request,
            null,
            activity.mainExecutor,
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    handleResponse(result, onToken, onError)
                }

                override fun onError(e: GetCredentialException) {
                    onError(
                        when (e) {
                            is GetCredentialCancellationException -> "Google sign-in was cancelled"
                            is NoCredentialException ->
                                "No Google account is available on this device. Add one in " +
                                    "Android Settings, then retry."
                            else -> e.message ?: "Google sign-in failed"
                        },
                    )
                }
            },
        )
    }

    private fun handleResponse(
        response: GetCredentialResponse,
        onToken: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            onError("Google returned an unsupported credential type")
            return
        }
        try {
            onToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } catch (e: GoogleIdTokenParsingException) {
            onError("Received an invalid Google ID token response")
        }
    }
}
