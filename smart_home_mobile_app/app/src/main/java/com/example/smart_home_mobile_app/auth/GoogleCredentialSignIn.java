package com.example.smart_home_mobile_app.auth;

import android.app.Activity;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException;

import java.util.function.Consumer;

/**
 * Obtains a Google ID token through the Credential Manager "Sign in with Google" flow.
 * The token is exchanged for a Firebase session by
 * {@link FirebaseAuthAdapter#signInWithGoogleIdToken}.
 */
public class GoogleCredentialSignIn {
    private final String serverClientId;

    public GoogleCredentialSignIn(String serverClientId) {
        this.serverClientId = serverClientId;
    }

    public void start(Activity activity, Consumer<String> onToken, Consumer<String> onError) {
        // Button-triggered flow: unlike GetGoogleIdOption (bottom sheet), this shows the
        // full Sign in with Google dialog, which can add an account when none exists.
        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(serverClientId).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();
        CredentialManager.create(activity).getCredentialAsync(
                activity,
                request,
                null,
                activity.getMainExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleResponse(result, onToken, onError);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            onError.accept("Google sign-in was cancelled");
                        } else if (e instanceof NoCredentialException) {
                            onError.accept("No Google account is available on this device. Add one in "
                                    + "Android Settings, then retry.");
                        } else {
                            onError.accept(e.getMessage() != null ? e.getMessage() : "Google sign-in failed");
                        }
                    }
                });
    }

    private void handleResponse(GetCredentialResponse response,
                                Consumer<String> onToken, Consumer<String> onError) {
        Credential credential = response.getCredential();
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            onError.accept("Google returned an unsupported credential type");
            return;
        }
        try {
            onToken.accept(GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData()).getIdToken());
        } catch (RuntimeException e) {
            onError.accept("Received an invalid Google ID token response");
        }
    }
}
