package com.example.smart_home_mobile_app.ui

import java.text.DateFormat
import java.util.Date

/**
 * Formats an event timestamp for display. Non-positive values mean the
 * source never had a synced clock, so render a placeholder instead of
 * the 1970 Unix epoch.
 */
fun formatEventTime(epochMs: Long): String =
    if (epochMs > 0L) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))
    } else {
        "Time unavailable"
    }
