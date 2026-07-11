package com.example.smart_home_mobile_app.ui;

import android.app.Activity;
import android.content.Context;

import com.example.smart_home_mobile_app.BuildConfig;
import com.example.smart_home_mobile_app.auth.AuthCoordinator;
import com.example.smart_home_mobile_app.auth.AuthResult;
import com.example.smart_home_mobile_app.auth.AuthUser;
import com.example.smart_home_mobile_app.auth.FirebaseAuthAdapter;
import com.example.smart_home_mobile_app.auth.GoogleCredentialSignIn;
import com.example.smart_home_mobile_app.auth.SecureSessionStore;
import com.example.smart_home_mobile_app.data.FakeData;
import com.example.smart_home_mobile_app.data.HomeUiState;
import com.example.smart_home_mobile_app.data.LoadStatus;
import com.example.smart_home_mobile_app.data.RoomSummary;
import com.example.smart_home_mobile_app.firebase.FirebaseBootstrap;
import com.example.smart_home_mobile_app.firebase.FirebaseRealtimeGateway;
import com.example.smart_home_mobile_app.firebase.FirebaseServices;
import com.example.smart_home_mobile_app.firebase.Subscription;
import com.example.smart_home_mobile_app.repository.CommandRepository;
import com.example.smart_home_mobile_app.repository.DeviceRepository;
import com.example.smart_home_mobile_app.repository.HomeIdStore;
import com.example.smart_home_mobile_app.repository.HomeManagementRepository;
import com.example.smart_home_mobile_app.repository.HomeRepository;
import com.example.smart_home_mobile_app.repository.RoomRepository;
import com.example.smart_home_mobile_app.repository.UserHomesRepository;
import com.example.smart_home_mobile_app.repository.UserHomesRepository.HomeListItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns all mutable app state and business wiring. Views subscribe with
 * {@link #addListener} and re-read the getters on {@link Listener#onControllerStateChanged()}.
 */
public class SmartHomeAppController {
    public interface Listener {
        void onControllerStateChanged();
    }

    public interface HomeActionCallback {
        void onResult(String value, String errorMessage);
    }

    private static final Set<String> ACCESS_COMMANDS =
            new HashSet<>(Arrays.asList("unlock", "open_door"));

    private final HomeIdStore homeIdStore;
    private AuthCoordinator authCoordinator;
    private FirebaseAuthAdapter firebaseAuthAdapter;
    private HomeRepository homeRepository;
    private CommandRepository commandRepository;
    private RoomRepository roomRepository;
    private DeviceRepository deviceRepository;
    private HomeManagementRepository homeManagementRepository;
    private UserHomesRepository userHomesRepository;
    private Subscription homeSubscription;
    private Subscription userHomesSubscription;

    private final List<Listener> listeners = new ArrayList<>();
    private final List<String> homeIds = new ArrayList<>();
    private final Map<String, String> homeNames = new LinkedHashMap<>();

    private AuthUiState authState = new AuthUiState(AuthStatus.LOADING);
    private HomeUiState homeState = new HomeUiState();
    private String selectedHomeId;
    private String commandMessage;
    private boolean previewMode;

    public SmartHomeAppController(Context context) {
        this(context, false);
    }

    public SmartHomeAppController(Context context, boolean isPreviewMode) {
        homeIdStore = new HomeIdStore(context);
        if (isPreviewMode) {
            previewMode = true;
            homeIds.add("home_123");
            homeNames.put("home_123", "Nhà mẫu");
            selectedHomeId = "home_123";
            homeState = FakeData.SAMPLE_HOME_UI_STATE;
            authState = new AuthUiState(AuthStatus.SIGNED_IN,
                    new AuthUser("uid_preview", "preview@example.com"), null);
        } else {
            homeIds.addAll(homeIdStore.load());
            try {
                FirebaseServices services = FirebaseBootstrap.initialize(context);
                FirebaseAuthAdapter adapter = new FirebaseAuthAdapter(services.auth);
                firebaseAuthAdapter = adapter;
                authCoordinator = new AuthCoordinator(adapter, new SecureSessionStore(context));
                FirebaseRealtimeGateway gateway = new FirebaseRealtimeGateway(services.database);
                homeRepository = new HomeRepository(gateway);
                commandRepository = new CommandRepository(gateway);
                roomRepository = new RoomRepository(gateway);
                deviceRepository = new DeviceRepository(gateway);
                homeManagementRepository = new HomeManagementRepository(services.auth, services.database);
                userHomesRepository = new UserHomesRepository(gateway);
                AuthUser restored = authCoordinator.restoredUser();
                if (restored == null) {
                    authState = new AuthUiState(AuthStatus.SIGNED_OUT);
                } else {
                    onAuthenticated(restored);
                }
            } catch (Throwable error) {
                authState = new AuthUiState(AuthStatus.CONFIG_REQUIRED, null, error.getMessage());
            }
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onControllerStateChanged();
        }
    }

    public AuthUiState authState() {
        return authState;
    }

    public HomeUiState homeState() {
        return homeState;
    }

    public List<String> homeIds() {
        return homeIds;
    }

    public List<HomeListItem> homeListItems() {
        ArrayList<HomeListItem> items = new ArrayList<>();
        for (String homeId : homeIds) {
            items.add(new HomeListItem(homeId, displayNameForHome(homeId)));
        }
        return items;
    }

    public String selectedHomeId() {
        return selectedHomeId;
    }

    public String commandMessage() {
        return commandMessage;
    }

    public boolean isPreviewMode() {
        return previewMode;
    }

    public void signIn(String email, String password) {
        if (authCoordinator == null) {
            authState = new AuthUiState(AuthStatus.CONFIG_REQUIRED, null,
                    "Firebase chưa sẵn sàng. Kiểm tra cấu hình project/API key.");
            notifyListeners();
            return;
        }
        authState = new AuthUiState(AuthStatus.LOADING);
        notifyListeners();
        authCoordinator.signIn(email, password, this::handleAuthResult);
    }

    public void register(String email, String password) {
        if (authCoordinator == null) {
            authState = new AuthUiState(AuthStatus.CONFIG_REQUIRED, null,
                    "Firebase chưa sẵn sàng. Kiểm tra cấu hình project/API key.");
            notifyListeners();
            return;
        }
        authState = new AuthUiState(AuthStatus.LOADING);
        notifyListeners();
        authCoordinator.register(email, password, this::handleAuthResult);
    }

    public void signInWithGoogle(Activity activity) {
        if (authCoordinator == null || firebaseAuthAdapter == null) {
            authState = new AuthUiState(AuthStatus.CONFIG_REQUIRED, null,
                    "Firebase chưa sẵn sàng. Kiểm tra cấu hình project/API key.");
            notifyListeners();
            return;
        }
        String webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim();
        if (webClientId.isEmpty()) {
            authState = new AuthUiState(AuthStatus.ERROR, null,
                    "Google sign-in is not configured. Set GOOGLE_WEB_CLIENT_ID in "
                            + "gradle.properties (see CONFIG_REQUIRED.md).");
            notifyListeners();
            return;
        }
        authState = new AuthUiState(AuthStatus.LOADING);
        notifyListeners();
        FirebaseAuthAdapter adapter = firebaseAuthAdapter;
        authCoordinator.signInWithProvider(
                onResult -> new GoogleCredentialSignIn(webClientId).start(
                        activity,
                        idToken -> adapter.signInWithGoogleIdToken(idToken, onResult),
                        message -> onResult.onResult(new AuthResult.Failure(message))),
                this::handleAuthResult);
    }

    public void signInWithApple(Activity activity) {
        if (authCoordinator == null || firebaseAuthAdapter == null) return;
        FirebaseAuthAdapter adapter = firebaseAuthAdapter;
        authState = new AuthUiState(AuthStatus.LOADING);
        notifyListeners();
        authCoordinator.signInWithProvider(
                onResult -> adapter.signInWithApple(activity, onResult),
                this::handleAuthResult);
    }

    public void logout() {
        cancelHomeSubscription();
        cancelUserHomesSubscription();
        if (authCoordinator != null) authCoordinator.logout();
        authState = new AuthUiState(AuthStatus.SIGNED_OUT);
        homeState = new HomeUiState();
        homeIds.clear();
        homeNames.clear();
        selectedHomeId = null;
        commandMessage = null;
        previewMode = false;
        notifyListeners();
    }

    public void enterPreviewMode() {
        if (!BuildConfig.DEBUG) return;
        cancelHomeSubscription();
        cancelUserHomesSubscription();
        previewMode = true;
        selectedHomeId = null;
        homeState = new HomeUiState(LoadStatus.EMPTY,
                "Debug UI preview: no Firebase session, home data, or device commands are active");
        commandMessage = null;
        notifyListeners();
    }

    public void exitPreviewMode() {
        previewMode = false;
        homeState = new HomeUiState();
        notifyListeners();
    }

    public void addHome(String homeId) {
        try {
            String trimmed = homeId.trim();
            List<String> ids = homeIdStore.add(trimmed);
            homeIds.clear();
            homeIds.addAll(ids);
            homeNames.putIfAbsent(trimmed, trimmed);
            selectHome(trimmed);
        } catch (IllegalArgumentException error) {
            homeState = new HomeUiState(LoadStatus.ERROR, error.getMessage());
            notifyListeners();
        }
    }

    public void removeSelectedHome() {
        if (selectedHomeId == null) return;
        String selected = selectedHomeId;
        cancelHomeSubscription();
        List<String> ids = homeIdStore.remove(selected);
        homeIds.clear();
        homeIds.addAll(ids);
        homeNames.remove(selected);
        selectedHomeId = null;
        homeState = new HomeUiState(LoadStatus.EMPTY, "Nhấn avatar để tạo hoặc tham gia nhà");
        notifyListeners();
        if (!ids.isEmpty()) {
            selectHome(ids.get(0));
        }
    }

    public void selectHome(String homeId) {
        AuthUser user = authState.user;
        if (user == null) return;
        if (!homeIds.contains(homeId)) return;
        cancelHomeSubscription();
        selectedHomeId = homeId;
        if (homeRepository != null) {
            homeSubscription = homeRepository.observe(homeId, user.uid, state -> {
                homeState = state;
                notifyListeners();
            });
        } else {
            notifyListeners();
        }
    }

    public void sendCommand(String nodeId, String commandType) {
        if (previewMode) {
            commandMessage = "Commands are disabled in debug UI preview";
            notifyListeners();
            return;
        }
        AuthUser user = authState.user;
        if (user == null || selectedHomeId == null) return;
        String homeId = selectedHomeId;
        String role = homeState.snapshot != null ? homeState.snapshot.home.role : "";
        if (ACCESS_COMMANDS.contains(commandType) && !"access_admin".equals(role)) {
            commandMessage = "Access commands require the access_admin role";
            notifyListeners();
            return;
        }
        commandMessage = "Submitting request…";
        notifyListeners();
        try {
            if (commandRepository != null) {
                commandRepository.create(user.uid, homeId, nodeId, commandType, (requestId, error) -> {
                    commandMessage = error == null
                            ? "Command request " + (requestId == null ? "" : requestId)
                              + " submitted for gateway authorization"
                            : error.message;
                    notifyListeners();
                });
            }
        } catch (IllegalArgumentException error) {
            commandMessage = error.getMessage();
            notifyListeners();
        }
    }

    /**
     * Tạo phòng mới trong nhà đang chọn. Không cần đợi callback để cập nhật UI —
     * {@code homeRepository.observe(...)} sẽ tự đẩy HomeUiState mới khi Firebase
     * báo có thay đổi (đúng cơ chế realtime), MainFragment không cần tự thêm
     * phòng vào danh sách cục bộ.
     */
    public void addRoom(String label) {
        if (previewMode) {
            commandMessage = "Không thể tạo phòng trong chế độ xem thử";
            notifyListeners();
            return;
        }
        if (authState.user == null) {
            commandMessage = "Bạn cần đăng nhập trước khi tạo phòng";
            notifyListeners();
            return;
        }
        if (selectedHomeId == null) {
            commandMessage = "Chưa chọn Home ID nên chưa thể tạo phòng";
            notifyListeners();
            return;
        }
        if (homeState.snapshot == null) {
            commandMessage = "Chưa thể tạo phòng vì tài khoản chưa có quyền đọc nhà " + selectedHomeId;
            notifyListeners();
            return;
        }
        String role = homeState.snapshot.home.role;
        if (!"device_admin".equals(role) && !"gateway_service".equals(role)) {
            commandMessage = "Tài khoản cần quyền device_admin để tạo phòng";
            notifyListeners();
            return;
        }
        if (roomRepository == null) {
            commandMessage = "Firebase chưa sẵn sàng nên chưa thể tạo phòng";
            notifyListeners();
            return;
        }
        commandMessage = "Đang tạo phòng...";
        notifyListeners();
        try {
            roomRepository.create(selectedHomeId, label, (roomId, error) -> {
                commandMessage = error == null
                        ? "Đã tạo phòng " + (label == null ? "" : label.trim())
                        : "Không tạo được phòng: " + error.message;
                notifyListeners();
            });
        } catch (IllegalArgumentException error) {
            commandMessage = "Không tạo được phòng: " + error.getMessage();
            notifyListeners();
        }
    }


    public void deleteRoom(String roomId) {
        if (previewMode) {
            commandMessage = "Không thể xóa phòng trong chế độ xem thử";
            notifyListeners();
            return;
        }
        if (!canManageDevices("xóa phòng")) return;
        if (roomId == null || roomId.trim().isEmpty()) {
            commandMessage = "Chưa chọn phòng để xóa";
            notifyListeners();
            return;
        }
        RoomSummary target = null;
        if (homeState.snapshot != null) {
            for (RoomSummary room : homeState.snapshot.rooms) {
                if (room.roomId.equals(roomId)) target = room;
            }
        }
        if (target == null) {
            commandMessage = "Phòng không tồn tại hoặc chưa được tải";
            notifyListeners();
            return;
        }
        if (!target.nodeIds.isEmpty()) {
            commandMessage = "Xóa thiết bị trong phòng trước khi xóa phòng";
            notifyListeners();
            return;
        }
        if (roomRepository == null) {
            commandMessage = "Firebase chưa sẵn sàng nên chưa thể xóa phòng";
            notifyListeners();
            return;
        }
        String roomLabel = target.label;
        commandMessage = "Đang xóa phòng...";
        notifyListeners();
        try {
            roomRepository.delete(selectedHomeId, roomId, (deletedRoomId, error) -> {
                commandMessage = error == null
                        ? "Đã xóa phòng " + roomLabel
                        : "Không xóa được phòng: " + error.message;
                notifyListeners();
            });
        } catch (IllegalArgumentException error) {
            commandMessage = "Không xóa được phòng: " + error.getMessage();
            notifyListeners();
        }
    }

    public void addDevice(String label, String roomId, String nodeType) {
        if (previewMode) {
            commandMessage = "Không thể thêm thiết bị trong chế độ xem thử";
            notifyListeners();
            return;
        }
        if (!canManageDevices("thêm thiết bị")) return;
        if (deviceRepository == null) {
            commandMessage = "Firebase chưa sẵn sàng nên chưa thể thêm thiết bị";
            notifyListeners();
            return;
        }
        commandMessage = "Đang thêm thiết bị...";
        notifyListeners();
        try {
            deviceRepository.create(selectedHomeId, roomId, label, nodeType, (nodeId, error) -> {
                commandMessage = error == null
                        ? "Đã thêm thiết bị " + (label == null ? "" : label.trim())
                        : "Không thêm được thiết bị: " + error.message;
                notifyListeners();
            });
        } catch (IllegalArgumentException error) {
            commandMessage = "Không thêm được thiết bị: " + error.getMessage();
            notifyListeners();
        }
    }

    public void deleteDevice(String nodeId, String label) {
        if (previewMode) {
            commandMessage = "Không thể xóa thiết bị trong chế độ xem thử";
            notifyListeners();
            return;
        }
        if (!canManageDevices("xóa thiết bị")) return;
        if (deviceRepository == null) {
            commandMessage = "Firebase chưa sẵn sàng nên chưa thể xóa thiết bị";
            notifyListeners();
            return;
        }
        commandMessage = "Đang xóa thiết bị...";
        notifyListeners();
        try {
            deviceRepository.delete(selectedHomeId, nodeId, (deletedNodeId, error) -> {
                commandMessage = error == null
                        ? "Đã xóa thiết bị " + (label == null ? deletedNodeId : label)
                        : "Không xóa được thiết bị: " + error.message;
                notifyListeners();
            });
        } catch (IllegalArgumentException error) {
            commandMessage = "Không xóa được thiết bị: " + error.getMessage();
            notifyListeners();
        }
    }

    private boolean canManageDevices(String actionLabel) {
        if (authState.user == null) {
            commandMessage = "Bạn cần đăng nhập trước khi " + actionLabel;
            notifyListeners();
            return false;
        }
        if (selectedHomeId == null) {
            commandMessage = "Chưa chọn Home ID nên chưa thể " + actionLabel;
            notifyListeners();
            return false;
        }
        if (homeState.snapshot == null) {
            commandMessage = "Chưa thể " + actionLabel + " vì tài khoản chưa có quyền đọc nhà " + selectedHomeId;
            notifyListeners();
            return false;
        }
        String role = homeState.snapshot.home.role;
        if (!"device_admin".equals(role) && !"gateway_service".equals(role)) {
            commandMessage = "Tài khoản cần quyền device_admin để " + actionLabel;
            notifyListeners();
            return false;
        }
        return true;
    }

    public void createHome(String name, String type, String address, HomeActionCallback callback) {
        if (homeManagementRepository == null) {
            callback.onResult(null, "Backend quản lý nhà chưa sẵn sàng");
            return;
        }
        commandMessage = "Đang tạo nhà...";
        notifyListeners();
        homeManagementRepository.createHome(name, type, address, (result, errorMessage) -> {
            if (errorMessage != null) {
                commandMessage = "Không tạo được nhà: " + errorMessage;
                notifyListeners();
                callback.onResult(null, commandMessage);
                return;
            }
            String homeId = result == null ? null : result.homeId;
            if (homeId == null || homeId.trim().isEmpty()) {
                commandMessage = "Backend không trả về Home ID";
                notifyListeners();
                callback.onResult(null, commandMessage);
                return;
            }
            rememberAndSelectHome(homeId, result.displayName);
            commandMessage = "Đã tạo nhà " + (result.displayName == null ? homeId : result.displayName);
            notifyListeners();
            callback.onResult(homeId, null);
        });
    }

    public void createInvite(String homeId, HomeActionCallback callback) {
        if (homeManagementRepository == null) {
            callback.onResult(null, "Backend quản lý nhà chưa sẵn sàng");
            return;
        }
        String targetHomeId = homeId == null || homeId.trim().isEmpty() ? selectedHomeId : homeId.trim();
        if (targetHomeId == null || targetHomeId.trim().isEmpty()) {
            callback.onResult(null, "Chưa chọn nhà để tạo mã mời");
            return;
        }
        commandMessage = "Đang tạo mã mời...";
        notifyListeners();
        homeManagementRepository.createInvite(targetHomeId, (result, errorMessage) -> {
            if (errorMessage != null) {
                commandMessage = "Không tạo được mã mời: " + errorMessage;
                notifyListeners();
                callback.onResult(null, commandMessage);
                return;
            }
            String code = result == null ? null : result.inviteCode;
            commandMessage = code == null ? "Đã tạo mã mời" : "Mã mời: " + code;
            notifyListeners();
            callback.onResult(code, null);
        });
    }

    public void redeemInvite(String code, HomeActionCallback callback) {
        if (homeManagementRepository == null) {
            callback.onResult(null, "Backend quản lý nhà chưa sẵn sàng");
            return;
        }
        commandMessage = "Đang tham gia nhà...";
        notifyListeners();
        homeManagementRepository.redeemInvite(code, (result, errorMessage) -> {
            if (errorMessage != null) {
                commandMessage = "Không tham gia được nhà: " + errorMessage;
                notifyListeners();
                callback.onResult(null, commandMessage);
                return;
            }
            String homeId = result == null ? null : result.homeId;
            if (homeId == null || homeId.trim().isEmpty()) {
                commandMessage = "Backend không trả về Home ID";
                notifyListeners();
                callback.onResult(null, commandMessage);
                return;
            }
            rememberAndSelectHome(homeId, result.displayName);
            commandMessage = "Đã tham gia nhà " + (result.displayName == null ? homeId : result.displayName);
            notifyListeners();
            callback.onResult(homeId, null);
        });
    }


    public void deleteSelectedOwnedHome(HomeActionCallback callback) {
        if (homeManagementRepository == null) {
            callback.onResult(null, "Backend quản lý nhà chưa sẵn sàng");
            return;
        }
        String homeId = selectedHomeId;
        if (homeId == null || homeId.trim().isEmpty()) {
            callback.onResult(null, "Chưa chọn nhà để xóa");
            return;
        }
        commandMessage = "Đang xóa nhà...";
        notifyListeners();
        homeManagementRepository.deleteOwnedHome(homeId, (result, errorMessage) -> {
            if (errorMessage != null) {
                commandMessage = "Không xóa được nhà: " + errorMessage;
                notifyListeners();
                callback.onResult(null, commandMessage);
                return;
            }
            String deletedHomeId = result == null || result.homeId == null ? homeId : result.homeId;
            forgetHome(deletedHomeId);
            commandMessage = "Đã xóa nhà " + (result == null || result.displayName == null
                    ? deletedHomeId : result.displayName);
            notifyListeners();
            callback.onResult(deletedHomeId, null);
        });
    }

    private void forgetHome(String homeId) {
        boolean deletingSelected = homeId != null && homeId.equals(selectedHomeId);
        if (deletingSelected) cancelHomeSubscription();
        List<String> ids = homeIdStore.remove(homeId);
        homeIds.clear();
        homeIds.addAll(ids);
        homeNames.remove(homeId);
        if (!deletingSelected) return;
        selectedHomeId = null;
        homeState = new HomeUiState(LoadStatus.EMPTY, "Nhấn avatar để tạo hoặc tham gia nhà");
        if (!ids.isEmpty()) {
            selectHome(ids.get(0));
        }
    }

    private void rememberAndSelectHome(String homeId) {
        rememberAndSelectHome(homeId, null);
    }

    private void rememberAndSelectHome(String homeId, String displayName) {
        String trimmed = homeId.trim();
        List<String> ids = homeIdStore.add(trimmed);
        homeIds.clear();
        homeIds.addAll(ids);
        if (displayName != null && !displayName.trim().isEmpty()) {
            homeNames.put(trimmed, displayName.trim());
        } else {
            homeNames.putIfAbsent(trimmed, trimmed);
        }
        selectHome(trimmed);
    }

    private String displayNameForHome(String homeId) {
        String displayName = homeNames.get(homeId);
        return displayName == null || displayName.trim().isEmpty() ? homeId : displayName;
    }

    public void clearCommandMessage() {
        commandMessage = null;
    }

    public void close() {
        cancelHomeSubscription();
    }

    private void cancelHomeSubscription() {
        if (homeSubscription != null) {
            homeSubscription.cancel();
            homeSubscription = null;
        }
    }

    private void cancelUserHomesSubscription() {
        if (userHomesSubscription != null) {
            userHomesSubscription.cancel();
            userHomesSubscription = null;
        }
    }

    private void observeUserHomes(AuthUser user) {
        cancelUserHomesSubscription();
        if (userHomesRepository == null) return;
        userHomesSubscription = userHomesRepository.observe(user.uid, remoteHomes -> {
            boolean changed = false;
            ArrayList<String> remoteIds = new ArrayList<>();
            for (HomeListItem home : remoteHomes) {
                remoteIds.add(home.homeId);
                homeNames.put(home.homeId, home.name);
                if (!homeIds.contains(home.homeId)) {
                    homeIdStore.add(home.homeId);
                    changed = true;
                }
            }
            if (changed) {
                homeIds.clear();
                homeIds.addAll(homeIdStore.load());
            }
            if (!remoteIds.isEmpty() && (selectedHomeId == null || !remoteIds.contains(selectedHomeId))) {
                selectHome(remoteIds.get(0));
            } else {
                notifyListeners();
            }
        });
    }

    private void handleAuthResult(AuthResult result) {
        if (result instanceof AuthResult.Success) {
            onAuthenticated(((AuthResult.Success) result).user);
        } else if (result instanceof AuthResult.Failure) {
            authState = new AuthUiState(AuthStatus.ERROR, null, ((AuthResult.Failure) result).message);
            notifyListeners();
        }
    }

    private void onAuthenticated(AuthUser user) {
        authState = new AuthUiState(AuthStatus.SIGNED_IN, user, null);
        observeUserHomes(user);
        if (homeIds.isEmpty()) {
            homeState = new HomeUiState(LoadStatus.EMPTY, "Nhấn avatar để tạo hoặc tham gia nhà");
            notifyListeners();
        } else {
            selectHome(homeIds.get(0));
        }
    }
}