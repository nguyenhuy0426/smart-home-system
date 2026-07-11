package com.example.smart_home_mobile_app.auth;

public interface SessionStore {
    void save(AuthUser user);

    /** Nullable. */
    AuthUser load();

    void clear();
}
