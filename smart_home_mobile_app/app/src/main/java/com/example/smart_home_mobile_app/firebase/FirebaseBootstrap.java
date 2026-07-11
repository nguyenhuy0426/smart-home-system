package com.example.smart_home_mobile_app.firebase;

import android.content.Context;

import com.example.smart_home_mobile_app.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

public final class FirebaseBootstrap {
    private static final String APP_NAME = "smart-home-mobile";

    private FirebaseBootstrap() {
    }

    public static FirebaseServices initialize(Context context) {
        String applicationId = BuildConfig.FIREBASE_APPLICATION_ID.trim();
        String apiKey = BuildConfig.FIREBASE_API_KEY.trim();
        String databaseUrl = trimTrailingSlashes(BuildConfig.FIREBASE_DATABASE_URL.trim());
        String projectId = BuildConfig.FIREBASE_PROJECT_ID.trim();
        if (projectId.isEmpty()) {
            projectId = inferProjectId(databaseUrl);
        }
        if (applicationId.isEmpty() || apiKey.isEmpty() || databaseUrl.isEmpty()) {
            throw new IllegalStateException(
                    "Firebase is not configured. Set FIREBASE_APPLICATION_ID, "
                            + "FIREBASE_API_KEY, and FIREBASE_DATABASE_URL in ~/.gradle/gradle.properties.");
        }
        if (!databaseUrl.startsWith("https://")) {
            throw new IllegalStateException("FIREBASE_DATABASE_URL must use HTTPS");
        }
        FirebaseApp app = null;
        for (FirebaseApp existing : FirebaseApp.getApps(context)) {
            if (APP_NAME.equals(existing.getName())) {
                app = existing;
                break;
            }
        }
        if (app == null) {
            app = FirebaseApp.initializeApp(
                    context,
                    new FirebaseOptions.Builder()
                            .setApplicationId(applicationId)
                            .setApiKey(apiKey)
                            .setDatabaseUrl(databaseUrl)
                            .setProjectId(projectId)
                            .build(),
                    APP_NAME);
        }
        return new FirebaseServices(
                FirebaseAuth.getInstance(app),
                FirebaseDatabase.getInstance(app));
    }

    private static String inferProjectId(String databaseUrl) {
        String prefix = "https://";
        if (!databaseUrl.startsWith(prefix)) return "";
        String host = databaseUrl.substring(prefix.length());
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        String marker = "-default-rtdb";
        int markerIndex = host.indexOf(marker);
        if (markerIndex > 0) {
            return host.substring(0, markerIndex);
        }
        String firebaseIo = ".firebaseio.com";
        if (host.endsWith(firebaseIo)) {
            return host.substring(0, host.length() - firebaseIo.length());
        }
        return "";
    }

    private static String trimTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
