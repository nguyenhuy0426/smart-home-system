package com.example.smart_home_mobile_app.repository;

import com.example.smart_home_mobile_app.data.HomeSnapshot;
import com.example.smart_home_mobile_app.data.HomeSnapshotParser;
import com.example.smart_home_mobile_app.data.HomeUiState;
import com.example.smart_home_mobile_app.data.LoadStatus;
import com.example.smart_home_mobile_app.firebase.RealtimeError;
import com.example.smart_home_mobile_app.firebase.RealtimeErrorKind;
import com.example.smart_home_mobile_app.firebase.RealtimeGateway;
import com.example.smart_home_mobile_app.firebase.Subscription;

import java.util.function.Consumer;
import java.util.regex.Pattern;

public class HomeRepository {
    private static final String OFFLINE_MESSAGE =
            "Offline; showing the last Firebase snapshot when available";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");

    private final RealtimeGateway gateway;

    public HomeRepository(RealtimeGateway gateway) {
        this.gateway = gateway;
    }

    public Subscription observe(String homeId, String uid, Consumer<HomeUiState> callback) {
        requireIdentifier("homeId", homeId);
        if (uid == null || uid.trim().isEmpty()) {
            throw new IllegalArgumentException("uid is required");
        }
        Observation observation = new Observation(callback);
        callback.accept(observation.lastDataState);

        Subscription homeSubscription = gateway.observe("homes/" + homeId, new RealtimeGateway.Listener() {
            @Override
            public void onData(Object value) {
                observation.onHomeData(homeId, uid, value);
            }

            @Override
            public void onError(RealtimeError error) {
                observation.onHomeError(error);
            }
        });

        Subscription connectionSubscription = gateway.observe(".info/connected", new RealtimeGateway.Listener() {
            @Override
            public void onData(Object value) {
                observation.onConnected(value);
            }

            @Override
            public void onError(RealtimeError error) {
                observation.onConnectionError(error);
            }
        });

        return () -> {
            homeSubscription.cancel();
            connectionSubscription.cancel();
        };
    }

    private void requireIdentifier(String name, String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    /** Holds the mutable per-subscription state the Kotlin closure previously captured. */
    private static final class Observation {
        private final Consumer<HomeUiState> callback;
        private HomeUiState lastDataState = new HomeUiState(LoadStatus.LOADING);
        private boolean connected = true;

        Observation(Consumer<HomeUiState> callback) {
            this.callback = callback;
        }

        private void publish(HomeUiState state) {
            lastDataState = state;
            callback.accept(connected ? state.withConnected(true) : state.asOffline(OFFLINE_MESSAGE));
        }

        void onHomeData(String homeId, String uid, Object value) {
            try {
                HomeSnapshot snapshot = HomeSnapshotParser.parse(homeId, uid, value);
                if (snapshot.home.role.trim().isEmpty()) {
                    publish(new HomeUiState(LoadStatus.PERMISSION_DENIED,
                            "This account is not a member of home " + homeId));
                    return;
                }
                boolean empty = snapshot.nodes.isEmpty() && snapshot.accessEvents.isEmpty()
                        && snapshot.detectionEvents.isEmpty();
                publish(new HomeUiState(empty ? LoadStatus.EMPTY : LoadStatus.READY, snapshot,
                        empty ? "This home has no node data yet" : null));
            } catch (IllegalArgumentException error) {
                publish(new HomeUiState(LoadStatus.ERROR, error.getMessage()));
            }
        }

        void onHomeError(RealtimeError error) {
            LoadStatus status;
            switch (error.kind) {
                case PERMISSION_DENIED:
                    status = LoadStatus.PERMISSION_DENIED;
                    break;
                case OFFLINE:
                    status = LoadStatus.OFFLINE;
                    break;
                default:
                    status = LoadStatus.ERROR;
            }
            publish(new HomeUiState(status, error.message, status != LoadStatus.OFFLINE));
        }

        void onConnected(Object value) {
            if (value instanceof Boolean) {
                connected = (Boolean) value;
            }
            callback.accept(connected
                    ? lastDataState.withConnected(true)
                    : lastDataState.asOffline(OFFLINE_MESSAGE));
        }

        void onConnectionError(RealtimeError error) {
            if (error.kind == RealtimeErrorKind.OFFLINE) {
                connected = false;
                callback.accept(lastDataState.asOffline(error.message));
            }
        }
    }
}
