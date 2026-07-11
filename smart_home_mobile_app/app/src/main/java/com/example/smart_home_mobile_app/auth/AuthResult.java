package com.example.smart_home_mobile_app.auth;

public abstract class AuthResult {
    private AuthResult() {
    }

    public static final class Success extends AuthResult {
        public final AuthUser user;

        public Success(AuthUser user) {
            this.user = user;
        }
    }

    public static final class Failure extends AuthResult {
        public final String message;

        public Failure(String message) {
            this.message = message;
        }
    }
}
