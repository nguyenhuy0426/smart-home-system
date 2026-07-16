package com.example.smart_home_mobile_app.ui.notifications;

public final class NotificationItem {
    public enum Kind {
        COMMAND,
        SENSOR_ALERT
    }

    public final String id;
    public final Kind kind;
    public final String title;
    public final String description;
    public final long timestampEpochMs;

    public NotificationItem(String id, Kind kind, String title, String description,
                            long timestampEpochMs) {
        this.id = id;
        this.kind = kind;
        this.title = title;
        this.description = description;
        this.timestampEpochMs = timestampEpochMs;
    }
}
