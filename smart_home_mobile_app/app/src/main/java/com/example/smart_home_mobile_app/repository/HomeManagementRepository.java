package com.example.smart_home_mobile_app.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class HomeManagementRepository {
    public interface Callback {
        void onResult(HomeActionResult result, String errorMessage);
    }

    public static final class HomeActionResult {
        public final String homeId;
        public final String displayName;
        public final String role;
        public final String inviteCode;

        HomeActionResult(String homeId, String displayName, String role, String inviteCode) {
            this.homeId = homeId;
            this.displayName = displayName;
            this.role = role;
            this.inviteCode = inviteCode;
        }
    }

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final FirebaseAuth auth;
    private final FirebaseDatabase database;
    private final SecureRandom random = new SecureRandom();

    public HomeManagementRepository(FirebaseAuth auth, FirebaseDatabase database) {
        this.auth = auth;
        this.database = database;
    }

    public void createHome(String name, String type, String address, Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onResult(null, "Bạn cần đăng nhập trước khi tạo nhà");
            return;
        }
        String displayName = clean(name, 80);
        if (displayName.isEmpty()) {
            callback.onResult(null, "Tên nhà không được để trống");
            return;
        }
        String homeId = "home_" + database.getReference().push().getKey().replaceAll("[^A-Za-z0-9_-]", "");

        Map<String, Object> home = new LinkedHashMap<>();
        home.put("name", displayName);
        home.put("owner", user.getUid());
        home.put("members", map(user.getUid(), "admin"));
        home.put("rooms", new LinkedHashMap<String, Object>());

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("homes/" + homeId, home);
        updates.put("userHomes/" + user.getUid() + "/" + homeId, displayName);
        database.getReference().updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(new HomeActionResult(homeId, displayName, "device_admin", null), null);
            } else {
                callback.onResult(null, message(task.getException()));
            }
        });
    }

    public void createInvite(String homeId, Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onResult(null, "Bạn cần đăng nhập trước khi tạo mã mời");
            return;
        }
        String cleanedHomeId = clean(homeId, 128);
        if (cleanedHomeId.isEmpty()) {
            callback.onResult(null, "Chưa chọn nhà để tạo mã mời");
            return;
        }
        String code = randomInviteCode();
        long now = System.currentTimeMillis();
        long expiresAt = now + 7L * 24L * 60L * 60L * 1000L;
        DatabaseReference homeRef = database.getReference("homes").child(cleanedHomeId);
        homeRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                callback.onResult(null, "Nhà không tồn tại hoặc tài khoản chưa có quyền đọc");
                return;
            }
            String homeName = value(snapshot.child("name"), value(snapshot.child("displayName"), cleanedHomeId));
            Map<String, Object> invite = map(
                    "h", cleanedHomeId,
                    "r", "member",
                    "e", expiresAt,
                    "n", homeName);
            database.getReference("homeInvites").child(code).setValue(invite).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    callback.onResult(new HomeActionResult(cleanedHomeId, homeName, "home_member", code), null);
                } else {
                    callback.onResult(null, message(task.getException()));
                }
            });
        }).addOnFailureListener(error -> callback.onResult(null, message(error)));
    }

    public void redeemInvite(String code, Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onResult(null, "Bạn cần đăng nhập trước khi tham gia nhà");
            return;
        }
        String cleanedCode = clean(code, 32).toUpperCase(Locale.ROOT);
        if (cleanedCode.isEmpty()) {
            callback.onResult(null, "Mã mời không được để trống");
            return;
        }
        database.getReference("homeInvites").child(cleanedCode).get()
                .addOnSuccessListener(snapshot -> redeemInviteSnapshot(user, cleanedCode, snapshot, callback))
                .addOnFailureListener(error -> callback.onResult(null, message(error)));
    }

    private void redeemInviteSnapshot(FirebaseUser user, String code, DataSnapshot snapshot, Callback callback) {
        if (!snapshot.exists()) {
            callback.onResult(null, "Không tìm thấy mã mời");
            return;
        }
        String homeId = value(snapshot.child("h"), value(snapshot.child("homeId"), ""));
        String homeName = value(snapshot.child("n"), value(snapshot.child("homeName"), homeId));
        String compactRole = value(snapshot.child("r"), value(snapshot.child("role"), "home_member"));
        String role = normalizeRole(compactRole);
        Long expiresAt = snapshot.child("e").getValue(Long.class);
        if (expiresAt == null) expiresAt = snapshot.child("expiresAtEpochMs").getValue(Long.class);
        if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
            callback.onResult(null, "Mã mời đã hết hạn");
            return;
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("homes/" + homeId + "/members/" + user.getUid(), map(
                "r", compactRole(role),
                "c", code));
        updates.put("userHomes/" + user.getUid() + "/" + homeId, homeName);
        database.getReference().updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(new HomeActionResult(homeId, homeName, role, null), null);
            } else {
                callback.onResult(null, message(task.getException()));
            }
        });
    }


    public void deleteOwnedHome(String homeId, Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onResult(null, "Bạn cần đăng nhập trước khi xóa nhà");
            return;
        }
        String cleanedHomeId = clean(homeId, 128);
        if (cleanedHomeId.isEmpty()) {
            callback.onResult(null, "Chưa chọn nhà để xóa");
            return;
        }
        DatabaseReference homeRef = database.getReference("homes").child(cleanedHomeId);
        homeRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                callback.onResult(null, "Nhà không tồn tại hoặc tài khoản chưa có quyền đọc");
                return;
            }
            String owner = value(snapshot.child("owner"), value(snapshot.child("ownerUid"), ""));
            if (!user.getUid().equals(owner)) {
                callback.onResult(null, "Chỉ chủ nhà mới được xóa nhà này");
                return;
            }
            String homeName = value(snapshot.child("name"), value(snapshot.child("displayName"), cleanedHomeId));
            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put("homes/" + cleanedHomeId, null);
            for (DataSnapshot member : snapshot.child("members").getChildren()) {
                String memberUid = member.getKey();
                if (memberUid != null && !memberUid.trim().isEmpty()) {
                    updates.put("userHomes/" + memberUid + "/" + cleanedHomeId, null);
                }
            }
            updates.put("userHomes/" + user.getUid() + "/" + cleanedHomeId, null);
            database.getReference().updateChildren(updates).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    callback.onResult(new HomeActionResult(cleanedHomeId, homeName, null, null), null);
                } else {
                    callback.onResult(null, message(task.getException()));
                }
            });
        }).addOnFailureListener(error -> callback.onResult(null, message(error)));
    }

    private String randomInviteCode() {
        StringBuilder builder = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            builder.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return builder.toString();
    }

    private String clean(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String value(DataSnapshot snapshot, String fallback) {
        Object value = snapshot.getValue();
        return value instanceof String ? (String) value : fallback;
    }


    private String normalizeRole(String role) {
        if ("admin".equals(role) || "owner".equals(role)) return "device_admin";
        if ("member".equals(role)) return "home_member";
        return role == null || role.trim().isEmpty() ? "home_member" : role;
    }

    private String compactRole(String role) {
        if ("device_admin".equals(role) || "gateway_service".equals(role)) return "admin";
        if ("home_member".equals(role)) return "member";
        return role == null || role.trim().isEmpty() ? "member" : role;
    }

    private Map<String, Object> map(Object... keyValues) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    private String message(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Không thể ghi Realtime Database"
                : message;
    }
}
