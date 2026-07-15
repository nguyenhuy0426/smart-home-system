package com.example.smart_home_mobile_app.auth;

public interface AuthAdapter {
    /** Nullable. */
    AuthUser currentUser();

    void signIn(String email, String password, AuthCallback callback);

    void register(String email, String password, AuthCallback callback);

    void signOut();
}
