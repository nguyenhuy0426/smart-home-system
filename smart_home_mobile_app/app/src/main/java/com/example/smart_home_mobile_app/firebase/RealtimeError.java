package com.example.smart_home_mobile_app.firebase;

public final class RealtimeError {
    public final RealtimeErrorKind kind;
    public final String message;

    public RealtimeError(RealtimeErrorKind kind, String message) {
        this.kind = kind;
        this.message = message;
    }
}
