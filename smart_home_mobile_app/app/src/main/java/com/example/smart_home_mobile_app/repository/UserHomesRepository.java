package com.example.smart_home_mobile_app.repository;

import com.example.smart_home_mobile_app.firebase.RealtimeGateway;
import com.example.smart_home_mobile_app.firebase.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class UserHomesRepository {
    public static final class HomeListItem {
        public final String homeId;
        public final String name;

        public HomeListItem(String homeId, String name) {
            this.homeId = homeId;
            this.name = name;
        }
    }

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");

    private final RealtimeGateway gateway;

    public UserHomesRepository(RealtimeGateway gateway) {
        this.gateway = gateway;
    }

    public Subscription observe(String uid, Consumer<List<HomeListItem>> callback) {
        if (uid == null || uid.trim().isEmpty()) {
            throw new IllegalArgumentException("uid is required");
        }
        return gateway.observe("userHomes/" + uid, new RealtimeGateway.Listener() {
            @Override
            public void onData(Object value) {
                callback.accept(parseHomes(value));
            }

            @Override
            public void onError(com.example.smart_home_mobile_app.firebase.RealtimeError error) {
                callback.accept(Collections.emptyList());
            }
        });
    }

    private List<HomeListItem> parseHomes(Object value) {
        ArrayList<HomeListItem> homes = new ArrayList<>();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) continue;
                String homeId = (String) entry.getKey();
                if (!IDENTIFIER.matcher(homeId).matches()) continue;
                homes.add(new HomeListItem(homeId, nameFromValue(entry.getValue(), homeId)));
            }
        }
        Collections.sort(homes, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return homes;
    }

    private String nameFromValue(Object value, String fallback) {
        if (value instanceof String && !((String) value).trim().isEmpty()) {
            return ((String) value).trim();
        }
        if (value instanceof Map) {
            Object displayName = ((Map<?, ?>) value).get("displayName");
            if (displayName instanceof String && !((String) displayName).trim().isEmpty()) {
                return ((String) displayName).trim();
            }
            Object name = ((Map<?, ?>) value).get("name");
            if (name instanceof String && !((String) name).trim().isEmpty()) {
                return ((String) name).trim();
            }
        }
        return fallback;
    }
}
