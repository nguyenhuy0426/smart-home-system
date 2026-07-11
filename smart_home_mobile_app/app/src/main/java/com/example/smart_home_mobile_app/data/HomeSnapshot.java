package com.example.smart_home_mobile_app.data;

import java.util.List;

public final class HomeSnapshot {
    public final HomeSummary home;
    public final List<RoomSummary> rooms;
    public final List<NodeSummary> nodes;
    public final List<AccessEvent> accessEvents;
    public final List<DetectionEvent> detectionEvents;
    public final List<CommandRequest> commandRequests;

    public HomeSnapshot(HomeSummary home, List<RoomSummary> rooms, List<NodeSummary> nodes,
                        List<AccessEvent> accessEvents, List<DetectionEvent> detectionEvents,
                        List<CommandRequest> commandRequests) {
        this.home = home;
        this.rooms = rooms;
        this.nodes = nodes;
        this.accessEvents = accessEvents;
        this.detectionEvents = detectionEvents;
        this.commandRequests = commandRequests;
    }
}
