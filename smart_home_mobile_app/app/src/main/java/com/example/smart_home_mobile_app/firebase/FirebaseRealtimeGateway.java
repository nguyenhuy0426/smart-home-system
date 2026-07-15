package com.example.smart_home_mobile_app.firebase;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class FirebaseRealtimeGateway implements RealtimeGateway {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");

    private final FirebaseDatabase database;

    public FirebaseRealtimeGateway(FirebaseDatabase database) {
        this.database = database;
    }

    @Override
    public Subscription observe(String path, Listener listener) {
        DatabaseReference reference = reference(path);
        ValueEventListener firebaseListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listener.onData(snapshot.getValue());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(toRealtimeError(error));
            }
        };
        reference.addValueEventListener(firebaseListener);
        return () -> reference.removeEventListener(firebaseListener);
    }

    @Override
    public void write(String path, Map<String, Object> value, WriteCallback callback) {
        writeValue(path, value, callback);
    }

    @Override
    public void writeValue(String path, Object value, WriteCallback callback) {
        reference(path).setValue(value).addOnCompleteListener(task -> {
            Exception exception = task.getException();
            if (exception == null) {
                callback.onComplete(null);
                return;
            }
            String message = exception.getMessage();
            RealtimeErrorKind kind = (message != null && message.toLowerCase(Locale.ROOT).contains("permission"))
                    ? RealtimeErrorKind.PERMISSION_DENIED
                    : RealtimeErrorKind.OTHER;
            callback.onComplete(new RealtimeError(kind,
                    message != null ? message : "Realtime Database write failed"));
        });
    }

    private DatabaseReference reference(String path) {
        if (path.startsWith("/") || path.contains("..")) {
            throw new IllegalArgumentException("Unsafe RTDB path");
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.trim().isEmpty()) {
                segments.add(segment);
            }
        }
        boolean infoPath = segments.size() == 2
                && segments.get(0).equals(".info") && segments.get(1).equals("connected");
        DatabaseReference current = database.getReference();
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            boolean allowed = SEGMENT.matcher(segment).matches()
                    || (infoPath && i == 0 && segment.equals(".info"));
            if (!allowed) {
                throw new IllegalArgumentException("Unsafe RTDB path segment");
            }
            current = current.child(segment);
        }
        return current;
    }

    private RealtimeError toRealtimeError(DatabaseError error) {
        RealtimeErrorKind kind;
        switch (error.getCode()) {
            case DatabaseError.PERMISSION_DENIED:
                kind = RealtimeErrorKind.PERMISSION_DENIED;
                break;
            case DatabaseError.DISCONNECTED:
            case DatabaseError.NETWORK_ERROR:
                kind = RealtimeErrorKind.OFFLINE;
                break;
            default:
                kind = RealtimeErrorKind.OTHER;
        }
        return new RealtimeError(kind, error.getMessage());
    }
}
