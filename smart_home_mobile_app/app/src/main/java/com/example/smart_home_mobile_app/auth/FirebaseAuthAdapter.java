package com.example.smart_home_mobile_app.auth;

import android.app.Activity;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.OAuthProvider;

import java.util.Arrays;
import java.util.Locale;

public class FirebaseAuthAdapter implements AuthAdapter {
    private final FirebaseAuth auth;

    public FirebaseAuthAdapter(FirebaseAuth auth) {
        this.auth = auth;
    }

    @Override
    public AuthUser currentUser() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : toAuthUser(user);
    }

    @Override
    public void signIn(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    callback.onResult(user == null
                            ? new AuthResult.Failure("Firebase returned no authenticated user")
                            : new AuthResult.Success(toAuthUser(user)));
                })
                .addOnFailureListener(e -> callback.onResult(new AuthResult.Failure(safeMessage(e))));
    }

    @Override
    public void register(String email, String password, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    callback.onResult(user == null
                            ? new AuthResult.Failure("Firebase returned no registered user")
                            : new AuthResult.Success(toAuthUser(user)));
                })
                .addOnFailureListener(e -> callback.onResult(new AuthResult.Failure(safeMessage(e))));
    }

    public void signInWithGoogleIdToken(String idToken, AuthCallback callback) {
        signInWithCredential(GoogleAuthProvider.getCredential(idToken, null), callback);
    }

    public void signInWithApple(Activity activity, AuthCallback callback) {
        // The builder must be bound to this named FirebaseAuth instance: the default
        // FirebaseApp does not exist because FirebaseInitProvider is removed.
        OAuthProvider provider = OAuthProvider.newBuilder("apple.com", auth)
                .setScopes(Arrays.asList("email", "name"))
                .build();
        // A pending result exists when the custom-tab flow was interrupted (e.g. process
        // restart mid sign-in); consuming it avoids launching a second browser flow.
        Task<com.google.firebase.auth.AuthResult> task = auth.getPendingAuthResult();
        if (task == null) {
            task = auth.startActivityForSignInWithProvider(activity, provider);
        }
        task
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    callback.onResult(user == null
                            ? new AuthResult.Failure("Firebase returned no authenticated user")
                            : new AuthResult.Success(toAuthUser(user)));
                })
                .addOnFailureListener(e -> callback.onResult(new AuthResult.Failure(webFlowMessage(e))));
    }

    @Override
    public void signOut() {
        auth.signOut();
    }

    private void signInWithCredential(AuthCredential credential, AuthCallback callback) {
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    callback.onResult(user == null
                            ? new AuthResult.Failure("Firebase returned no authenticated user")
                            : new AuthResult.Success(toAuthUser(user)));
                })
                .addOnFailureListener(e -> callback.onResult(new AuthResult.Failure(safeMessage(e))));
    }

    /**
     * The provider web flow validates this build's signing certificate against the
     * Firebase project; the SDK's raw message for that rejection does not tell the
     * user what to do about it.
     */
    private String webFlowMessage(Throwable error) {
        String raw = safeMessage(error);
        if (raw.toLowerCase(Locale.ROOT).contains("certificate hash")) {
            return "Firebase rejected this build's signing certificate. Register the app's "
                    + "SHA-1 and SHA-256 fingerprints in the Firebase console and make sure "
                    + "the API key's Android restrictions match (see CONFIG_REQUIRED.md §2.1).";
        }
        return raw;
    }

    private AuthUser toAuthUser(FirebaseUser user) {
        return new AuthUser(user.getUid(), user.getEmail());
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "Firebase authentication failed" : message;
    }
}
