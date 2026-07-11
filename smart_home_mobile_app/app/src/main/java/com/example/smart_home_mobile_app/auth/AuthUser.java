package com.example.smart_home_mobile_app.auth;

public final class AuthUser {
    public final String uid;
    /** Nullable. */
    public final String email;

    public AuthUser(String uid, String email) {
        this.uid = uid;
        this.email = email;
    }
}
