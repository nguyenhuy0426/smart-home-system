package com.example.smart_home_mobile_app.firebase;

import java.util.Map;

public interface RealtimeGateway {
    Subscription observe(String path, Listener listener);

    void write(String path, Map<String, Object> value, WriteCallback callback);

    void writeValue(String path, Object value, WriteCallback callback);

    interface Listener {
        void onData(Object value);

        void onError(RealtimeError error);
    }

    interface WriteCallback {
        /** {@code error} is null on success. */
        void onComplete(RealtimeError error);
    }
}
