package com.example.smart_home_mobile_app.data;

import java.util.List;

public final class RoomSummary {
    public final String roomId;
    public final String label;
    public final List<String> nodeIds;

    public RoomSummary(String roomId, String label, List<String> nodeIds) {
        this.roomId = roomId;
        this.label = label;
        this.nodeIds = nodeIds;
    }
}
