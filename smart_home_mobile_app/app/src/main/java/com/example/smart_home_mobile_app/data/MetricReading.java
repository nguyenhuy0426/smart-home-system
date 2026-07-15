package com.example.smart_home_mobile_app.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MetricReading {
    private static final Set<String> VALID_STATES =
            new HashSet<>(Arrays.asList("valid", "ok", "good", "measured"));

    public final String key;
    /** Nullable: null means the metric carried no numeric value. */
    public final Double value;
    public final String unit;
    public final String source;
    public final String validity;
    /** Nullable. */
    public final String error;
    /** Nullable. */
    public final Boolean calibrated;

    public MetricReading(String key, Double value, String unit, String source,
                         String validity, String error, Boolean calibrated) {
        this.key = key;
        this.value = value;
        this.unit = unit;
        this.source = source;
        this.validity = validity;
        this.error = error;
        this.calibrated = calibrated;
    }

    public boolean isValid() {
        return value != null && VALID_STATES.contains(validity.toLowerCase(Locale.ROOT));
    }
}
