package com.example.smart_home_mobile_app.repository;

import com.example.smart_home_mobile_app.firebase.RealtimeError;
import com.example.smart_home_mobile_app.firebase.RealtimeGateway;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Ghi phòng mới vào Firebase, đúng schema mà {@code HomeSnapshotParser.parseRooms()}
 * đọc: {@code homes/{homeId}/rooms/{roomId} -> "Tên phòng"}.
 *
 * KHÔNG cần ghi nodeIds — RoomSummary.nodeIds được HomeSnapshotParser TÍNH TOÁN
 * bằng cách quét node nào có roomId trùng, không đọc từ Firebase.
 */
public class RoomRepository {
    /** {@code roomId} is null on failure; {@code error} is null on success. */
    public interface Callback {
        void onResult(String roomId, RealtimeError error);
    }

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

    private final RealtimeGateway gateway;
    private final Supplier<String> roomIdFactory;

    public RoomRepository(RealtimeGateway gateway) {
        this(gateway, () -> "room_" + UUID.randomUUID().toString().replace("-", ""));
    }

    public RoomRepository(RealtimeGateway gateway, Supplier<String> roomIdFactory) {
        this.gateway = gateway;
        this.roomIdFactory = roomIdFactory;
    }

    public void create(String homeId, String label, Callback callback) {
        requireIdentifier("homeId", homeId);
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("label is required");
        }
        String roomId = roomIdFactory.get();
        requireIdentifier("roomId", roomId);

        gateway.writeValue("homes/" + homeId + "/rooms/" + roomId, label.trim(),
                error -> callback.onResult(error == null ? roomId : null, error));
    }

    public void delete(String homeId, String roomId, Callback callback) {
        requireIdentifier("homeId", homeId);
        requireIdentifier("roomId", roomId);
        gateway.writeValue("homes/" + homeId + "/rooms/" + roomId, null,
                error -> callback.onResult(error == null ? roomId : null, error));
    }

    private void requireIdentifier(String name, String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}