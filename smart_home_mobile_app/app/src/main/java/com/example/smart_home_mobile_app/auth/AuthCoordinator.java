package com.example.smart_home_mobile_app.auth;

import java.util.regex.Pattern;

public class AuthCoordinator {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Runs a federated provider flow (Google/Apple) and reports its result. */
    public interface ProviderFlow {
        void start(AuthCallback onResult);
    }

    private final AuthAdapter adapter;
    private final SessionStore sessionStore;

    public AuthCoordinator(AuthAdapter adapter, SessionStore sessionStore) {
        this.adapter = adapter;
        this.sessionStore = sessionStore;
    }

    /** Nullable. */
    public AuthUser restoredUser() {
        AuthUser firebaseUser = adapter.currentUser();
        if (firebaseUser == null) {
            sessionStore.clear();
            return null;
        }
        AuthUser persisted = sessionStore.load();
        if (persisted == null || !persisted.uid.equals(firebaseUser.uid)) {
            sessionStore.save(firebaseUser);
        }
        return firebaseUser;
    }

    public void signIn(String email, String password, AuthCallback callback) {
        String error = validate(email, password);
        if (error != null) {
            callback.onResult(new AuthResult.Failure(error));
            return;
        }
        adapter.signIn(email.trim(), password, result -> {
            if (result instanceof AuthResult.Success) {
                sessionStore.save(((AuthResult.Success) result).user);
            }
            callback.onResult(result);
        });
    }

    public void register(String email, String password, AuthCallback callback) {
        String error = validate(email, password);
        if (error != null) {
            callback.onResult(new AuthResult.Failure(error));
            return;
        }
        adapter.register(email.trim(), password, result -> {
            if (result instanceof AuthResult.Success) {
                sessionStore.save(((AuthResult.Success) result).user);
            }
            callback.onResult(result);
        });
    }

    /**
     * Runs a federated provider flow supplied by {@code start} and persists the
     * session on success, mirroring the email/password paths.
     */
    public void signInWithProvider(ProviderFlow start, AuthCallback callback) {
        start.start(result -> {
            if (result instanceof AuthResult.Success) {
                sessionStore.save(((AuthResult.Success) result).user);
            }
            callback.onResult(result);
        });
    }

    public void logout() {
        adapter.signOut();
        sessionStore.clear();
    }

    /** Nullable: returns an error message when the credentials are invalid, else null. */
    private String validate(String email, String password) {
        String trimmed = email == null ? "" : email.trim();
        if (!EMAIL.matcher(trimmed).matches()) {
            return "Enter a valid email address";
        }
        if (password == null || password.length() < 6) {
            return "Password must contain at least 6 characters";
        }
        return null;
    }
}
