package com.example.smart_home_mobile_app.repository;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class HomeIdStore {
    private static final String KEY_HOME_IDS = "home_ids";
    private static final Pattern HOME_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");

    private final SharedPreferences preferences;

    public HomeIdStore(Context context) {
        preferences = context.getSharedPreferences("mobile_home_ids", Context.MODE_PRIVATE);
    }

    public List<String> load() {
        Set<String> stored = preferences.getStringSet(KEY_HOME_IDS, Collections.<String>emptySet());
        List<String> ids = new ArrayList<>(stored == null ? Collections.<String>emptySet() : stored);
        Collections.sort(ids);
        return ids;
    }

    public List<String> add(String homeId) {
        if (homeId == null || !HOME_ID.matcher(homeId).matches()) {
            throw new IllegalArgumentException("Invalid home ID");
        }
        Set<String> ids = new HashSet<>(load());
        ids.add(homeId);
        preferences.edit().putStringSet(KEY_HOME_IDS, ids).apply();
        return sorted(ids);
    }

    public List<String> remove(String homeId) {
        Set<String> ids = new HashSet<>(load());
        ids.remove(homeId);
        preferences.edit().putStringSet(KEY_HOME_IDS, ids).apply();
        return sorted(ids);
    }

    private static List<String> sorted(Set<String> ids) {
        List<String> list = new ArrayList<>(ids);
        Collections.sort(list);
        return list;
    }
}
