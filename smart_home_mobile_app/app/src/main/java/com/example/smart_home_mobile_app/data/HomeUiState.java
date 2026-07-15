package com.example.smart_home_mobile_app.data;

public final class HomeUiState {
    public final LoadStatus status;
    /** Nullable. */
    public final HomeSnapshot snapshot;
    /** Nullable. */
    public final String message;
    public final boolean connected;

    public HomeUiState() {
        this(LoadStatus.IDLE, null, null, true);
    }

    public HomeUiState(LoadStatus status) {
        this(status, null, null, true);
    }

    public HomeUiState(LoadStatus status, String message) {
        this(status, null, message, true);
    }

    public HomeUiState(LoadStatus status, String message, boolean connected) {
        this(status, null, message, connected);
    }

    public HomeUiState(LoadStatus status, HomeSnapshot snapshot, String message) {
        this(status, snapshot, message, true);
    }

    public HomeUiState(LoadStatus status, HomeSnapshot snapshot, String message, boolean connected) {
        this.status = status;
        this.snapshot = snapshot;
        this.message = message;
        this.connected = connected;
    }

    /** Returns a copy with {@code connected} overridden, preserving all other fields. */
    public HomeUiState withConnected(boolean connected) {
        return new HomeUiState(status, snapshot, message, connected);
    }

    /** Returns an OFFLINE copy that keeps the last snapshot but marks the app disconnected. */
    public HomeUiState asOffline(String offlineMessage) {
        return new HomeUiState(LoadStatus.OFFLINE, snapshot, offlineMessage, false);
    }
}
