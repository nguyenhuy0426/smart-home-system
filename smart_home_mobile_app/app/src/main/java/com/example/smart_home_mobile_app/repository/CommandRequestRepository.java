/*
 * Responsibility: placeholder repository boundary for writing mobile command
 * requests that the gateway validates and executes.
 */
package com.example.smart_home_mobile_app.repository;

import java.util.ArrayList;
import java.util.List;

public final class CommandRequestRepository {
    private final List<CommandRequest> requests = new ArrayList<>();

    public CommandRequest request(String homeId, String nodeId, String actionKey, String userRole) {
        CommandRequest request = new CommandRequest(homeId, nodeId, actionKey, userRole);
        requests.add(request);
        return request;
    }

    public List<CommandRequest> requests() {
        return new ArrayList<>(requests);
    }

    public static final class CommandRequest {
        public final String homeId;
        public final String nodeId;
        public final String actionKey;
        public final String userRole;

        CommandRequest(String homeId, String nodeId, String actionKey, String userRole) {
            this.homeId = homeId;
            this.nodeId = nodeId;
            this.actionKey = actionKey;
            this.userRole = userRole;
        }
    }
}
