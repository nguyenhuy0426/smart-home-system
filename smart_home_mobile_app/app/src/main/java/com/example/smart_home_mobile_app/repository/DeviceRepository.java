package com.example.smart_home_mobile_app.repository;

import com.example.smart_home_mobile_app.firebase.RealtimeError;
import com.example.smart_home_mobile_app.firebase.RealtimeGateway;
import com.example.smart_home_mobile_app.ui.adapter.DeviceAdapter;
import com.example.smart_home_mobile_app.ui.adapter.DoorAdapter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class DeviceRepository {
    /** {@code nodeId} is null on failure; {@code error} is null on success. */
    public interface Callback {
        void onResult(String nodeId, RealtimeError error);
    }

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

    private final RealtimeGateway gateway;
    private final Supplier<String> nodeIdFactory;

    public DeviceRepository(RealtimeGateway gateway) {
        this(gateway, () -> "node_" + UUID.randomUUID().toString().replace("-", ""));
    }

    public DeviceRepository(RealtimeGateway gateway, Supplier<String> nodeIdFactory) {
        this.gateway = gateway;
        this.nodeIdFactory = nodeIdFactory;
    }

    public void create(String homeId, String roomId, String label, String nodeType, Callback callback) {
        requireIdentifier("homeId", homeId);
        requireIdentifier("roomId", roomId);
        requireIdentifier("nodeType", nodeType);
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("label is required");
        }
        String nodeId = nodeIdFactory.get();
        requireIdentifier("nodeId", nodeId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("label", label.trim());
        payload.put("nodeType", nodeType);
        payload.put("roomId", roomId);
        payload.put("status", "unpaired");
        payload.put("source", "manual");
        payload.put("provisioned", false);
        payload.put("schemaVersion", 1);
        payload.put("actions", defaultActions(nodeType));

        gateway.write("homes/" + homeId + "/nodes/" + nodeId, payload,
                error -> callback.onResult(error == null ? nodeId : null, error));
    }

    public void delete(String homeId, String nodeId, Callback callback) {
        requireIdentifier("homeId", homeId);
        requireIdentifier("nodeId", nodeId);
        gateway.writeValue("homes/" + homeId + "/nodes/" + nodeId, null,
                error -> callback.onResult(error == null ? nodeId : null, error));
    }

    private List<String> defaultActions(String nodeType) {
        if (DeviceAdapter.NODE_TYPE_LIGHT.equals(nodeType)) {
            return Arrays.asList(DeviceAdapter.ACTION_TOGGLE,
                    DeviceAdapter.ACTION_SET_MODE,
                    DeviceAdapter.ACTION_SET_INTENSITY);
        }
        if (DeviceAdapter.NODE_TYPE_FAN.equals(nodeType)) {
            return Arrays.asList(DeviceAdapter.ACTION_TOGGLE,
                    DeviceAdapter.ACTION_SET_INTENSITY);
        }
        if (DeviceAdapter.NODE_TYPE_AIR_CONDITIONER.equals(nodeType)) {
            return Arrays.asList(DeviceAdapter.ACTION_TOGGLE,
                    DeviceAdapter.ACTION_SET_MODE,
                    DeviceAdapter.ACTION_SET_INTENSITY);
        }
        if ("door_lock".equals(nodeType)) {
            return Arrays.asList(DoorAdapter.ACTION_UNLOCK, DoorAdapter.ACTION_LOCK);
        }
        return Collections.emptyList();
    }

    private void requireIdentifier(String name, String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
