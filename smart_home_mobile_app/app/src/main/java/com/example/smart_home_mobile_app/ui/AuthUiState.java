package com.example.smart_home_mobile_app.ui;

import com.example.smart_home_mobile_app.auth.AuthUser;

public final class AuthUiState {
    public final AuthStatus status;
    /** Nullable. */
    public final AuthUser user;
    /** Nullable. */
    public final String message;

    public AuthUiState(AuthStatus status) {
        this(status, null, null);
    }

    public AuthUiState(AuthStatus status, AuthUser user, String message) {
        this.status = status;
        this.user = user;
        this.message = message;
    }
}
