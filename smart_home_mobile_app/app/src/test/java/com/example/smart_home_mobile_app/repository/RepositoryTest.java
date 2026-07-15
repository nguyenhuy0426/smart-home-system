package com.example.smart_home_mobile_app.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.example.smart_home_mobile_app.data.HomeUiState;
import com.example.smart_home_mobile_app.data.LoadStatus;
import com.example.smart_home_mobile_app.firebase.RealtimeError;
import com.example.smart_home_mobile_app.firebase.RealtimeErrorKind;
import com.example.smart_home_mobile_app.firebase.RealtimeGateway;
import com.example.smart_home_mobile_app.firebase.Subscription;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RepositoryTest {

    @Test
    public void permissionDeniedIsExposedAsPermissionDeniedState() {
        FakeGateway gateway = new FakeGateway(null,
                new RealtimeError(RealtimeErrorKind.PERMISSION_DENIED, "denied"));
        List<HomeUiState> states = new ArrayList<>();
        new HomeRepository(gateway).observe("home_1", "user_1", states::add);
        assertEquals(LoadStatus.LOADING, states.get(0).status);
        assertEquals(LoadStatus.PERMISSION_DENIED, states.get(states.size() - 1).status);
    }

    @Test
    public void emptyHomeIsExposedAsEmptyState() {
        FakeGateway gateway = new FakeGateway(
                map("members", map("user_1", map("role", "home_member"))), null);
        List<HomeUiState> states = new ArrayList<>();
        new HomeRepository(gateway).observe("home_1", "user_1", states::add);
        assertEquals(LoadStatus.EMPTY, states.get(states.size() - 1).status);
        assertNotNull(states.get(states.size() - 1).snapshot);
    }

    @Test
    public void commandCreatesOnlyCommandRequestWithRequiredIdentityAndStatus() {
        FakeGateway gateway = new FakeGateway(null, null);
        CommandRepository repository = new CommandRepository(gateway, () -> 1234L, () -> "cmd_test_1");
        String[] returnedId = new String[1];
        repository.create("user_1", "home_1", "node_1", "unlock", (id, error) -> {
            returnedId[0] = id;
            assertNull(error);
        });
        assertEquals("cmd_test_1", returnedId[0]);
        assertEquals("homes/home_1/commandRequests/cmd_test_1", gateway.writtenPath);
        assertEquals("user_1", gateway.writtenValue.get("requestedBy"));
        assertEquals("home_1", gateway.writtenValue.get("homeId"));
        assertEquals("node_1", gateway.writtenValue.get("nodeId"));
        assertEquals("unlock", gateway.writtenValue.get("action"));
        assertEquals("pending", gateway.writtenValue.get("status"));
        assertFalse(gateway.writtenPath.contains("readings"));
    }

    @Test
    public void roomCreatesRoomEntryWithParserSchema() {
        FakeGateway gateway = new FakeGateway(null, null);
        RoomRepository repository = new RoomRepository(gateway, () -> "room_living");
        String[] returnedId = new String[1];
        repository.create("home_1", "  Living room  ", (id, error) -> {
            returnedId[0] = id;
            assertNull(error);
        });
        assertEquals("room_living", returnedId[0]);
        assertEquals("homes/home_1/rooms/room_living", gateway.writtenPath);
        assertEquals("Living room", gateway.writtenRawValue);
    }

    private static Map<String, Object> map(Object... keyValues) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    private static final class FakeGateway implements RealtimeGateway {
        private final Object value;
        private final RealtimeError error;
        String writtenPath;
        Map<String, Object> writtenValue;
        Object writtenRawValue;

        FakeGateway(Object value, RealtimeError error) {
            this.value = value;
            this.error = error;
        }

        @Override
        public Subscription observe(String path, Listener listener) {
            if (path.equals(".info/connected")) {
                listener.onData(true);
            } else if (error != null) {
                listener.onError(error);
            } else {
                listener.onData(value);
            }
            return () -> {
            };
        }

        @Override
        public void write(String path, Map<String, Object> value, WriteCallback callback) {
            writtenPath = path;
            writtenValue = value;
            writtenRawValue = value;
            callback.onComplete(error);
        }

        @Override
        public void writeValue(String path, Object value, WriteCallback callback) {
            writtenPath = path;
            writtenRawValue = value;
            callback.onComplete(error);
        }
    }
}
