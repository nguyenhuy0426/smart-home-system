package com.example.smart_home_mobile_app.repository;

import com.example.smart_home_mobile_app.firebase.RealtimeError;
import com.example.smart_home_mobile_app.firebase.RealtimeGateway;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class CommandRepository {
    /** {@code requestId} is null on failure; {@code error} is null on success. */
    public interface Callback {
        void onResult(String requestId, RealtimeError error);
    }

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

    private final RealtimeGateway gateway;
    private final LongSupplier clock;
    private final Supplier<String> requestIdFactory;

    public CommandRepository(RealtimeGateway gateway) {
        this(gateway,
                System::currentTimeMillis,
                () -> "cmd_" + UUID.randomUUID().toString().replace("-", ""));
    }

    public CommandRepository(RealtimeGateway gateway, LongSupplier clock, Supplier<String> requestIdFactory) {
        this.gateway = gateway;
        this.clock = clock;
        this.requestIdFactory = requestIdFactory;
    }

    public void create(String userId, String homeId, String targetNodeId, String commandType, Callback callback) {
        requireIdentifier("userId", userId);
        requireIdentifier("homeId", homeId);
        requireIdentifier("targetNodeId", targetNodeId);
        requireIdentifier("commandType", commandType);
        String requestId = requestIdFactory.get();
        requireIdentifier("requestId", requestId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", requestId);
        payload.put("requestedBy", userId);
        payload.put("homeId", homeId);
        payload.put("nodeId", targetNodeId);
        payload.put("action", commandType);
        payload.put("createdAtEpochMs", clock.getAsLong());
        payload.put("status", "pending");

        gateway.write("homes/" + homeId + "/commandRequests/" + requestId, payload,
                error -> callback.onResult(error == null ? requestId : null, error));
    }

    private void requireIdentifier(String name, String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
