package com.example.smart_home_mobile_app.data;

import java.util.List;

public final class NodeSummary {
    public final String nodeId;
    public final String homeId;
    public final String roomId;
    public final String nodeType;
    public final String label;
    public final int schemaVersion;
    public final String status;
    public final List<String> actions;
    public final List<TelemetryReading> readings;

    public NodeSummary(String nodeId, String homeId, String roomId, String nodeType, String label,
                       int schemaVersion, String status, List<String> actions,
                       List<TelemetryReading> readings) {
        this.nodeId = nodeId;
        this.homeId = homeId;
        this.roomId = roomId;
        this.nodeType = nodeType;
        this.label = label;
        this.schemaVersion = schemaVersion;
        this.status = status;
        this.actions = actions;
        this.readings = readings;
    }

    /** Nullable: the reading with the greatest timestamp, or null when there are none. */
    public TelemetryReading latestReading() {
        TelemetryReading latest = null;
        for (TelemetryReading reading : readings) {
            if (latest == null || reading.timestampEpochMs() > latest.timestampEpochMs()) {
                latest = reading;
            }
        }
        return latest;
    }
}
