package com.example.smart_home_mobile_app.data;

public final class CommandRequest {
    public final String requestId;
    public final String requestedBy;
    public final String homeId;
    public final String targetNodeId;
    public final String commandType;
    public final long createdAtEpochMs;
    public final String status;

    public CommandRequest(String requestId, String requestedBy, String homeId, String targetNodeId,
                          String commandType, long createdAtEpochMs, String status) {
        this.requestId = requestId;
        this.requestedBy = requestedBy;
        this.homeId = homeId;
        this.targetNodeId = targetNodeId;
        this.commandType = commandType;
        this.createdAtEpochMs = createdAtEpochMs;
        this.status = status;
    }
}
