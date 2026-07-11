package com.example.smart_home_mobile_app.ui;

import java.text.DateFormat;
import java.util.Date;

public final class EventTimeFormat {
    private EventTimeFormat() {
    }

    /**
     * Formats an event timestamp for display. Non-positive values mean the source never
     * had a synced clock, so render a placeholder instead of the 1970 Unix epoch.
     */
    public static String formatEventTime(long epochMs) {
        if (epochMs > 0L) {
            return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(epochMs));
        }
        return "Time unavailable";
    }
}
