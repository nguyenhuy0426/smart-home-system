package com.android.smarthome.gateway.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.smarthome.data.*
import com.android.smarthome.firebase.RealtimeSubscription
import com.android.smarthome.repository.*

/** Mutable business-state owner ported from Project A, independent of XML or Compose. */
class SmartHomeController(
    context: Context,
    private val currentUser: () -> AuthUser?,
    private val homeRepository: HomeRepository,
    private val commandRepository: CommandRepository,
    private val roomRepository: RoomRepository,
    private val deviceRepository: DeviceRepository,
    private val homeManagementRepository: HomeManagementRepository,
    private val userHomesRepository: UserHomesRepository,
    preview: Boolean = false
) : AutoCloseable {
    fun interface Listener { fun onStateChanged(state: State) }

    data class State(
        val authUser: AuthUser? = null,
        val homeState: HomeUiState = HomeUiState(),
        val homes: List<HomeListItem> = emptyList(),
        val selectedHomeId: String? = null,
        val commandMessage: String? = null,
        val previewMode: Boolean = false
    )

    private val homeIdStore = HomeIdStore(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = linkedSetOf<Listener>()
    private val names = linkedMapOf<String, String>()
    private var homeSubscription: RealtimeSubscription? = null
    private var userHomesSubscription: RealtimeSubscription? = null

    var state: State = if (preview) State(
        AuthUser("uid_preview", "preview@example.com"), PreviewData.homeUiState,
        listOf(HomeListItem("home_123", "Nhà mẫu")), "home_123", previewMode = true
    ) else State(authUser = currentUser(), homes = localHomes())
        private set

    fun start() {
        if (state.previewMode) return publish()
        val user = currentUser()
        state = state.copy(authUser = user)
        if (user == null) return publish()
        observeUserHomes(user)
        state.homes.firstOrNull()?.let { selectHome(it.homeId) } ?: publish()
    }

    fun addListener(listener: Listener) { listeners += listener; listener.onStateChanged(state) }
    fun removeListener(listener: Listener) { listeners -= listener }

    fun enterPreviewMode() {
        cancelSubscriptions()
        state = State(
            AuthUser("uid_preview", "preview@example.com"), PreviewData.homeUiState,
            listOf(HomeListItem("home_123", "Nhà mẫu")), "home_123",
            "Debug UI preview: Firebase writes and device commands are disabled", true
        )
        publish()
    }

    fun exitPreviewMode() {
        state = State(authUser = currentUser(), homes = localHomes())
        start()
    }

    fun selectHome(homeId: String) {
        val user = state.authUser ?: return
        if (state.homes.none { it.homeId == homeId }) return
        homeSubscription?.cancel()
        state = state.copy(selectedHomeId = homeId, homeState = HomeUiState())
        publish()
        homeSubscription = homeRepository.observe(homeId, user.uid) { homeState ->
            state = state.copy(homeState = homeState)
            publish()
        }
    }

    fun sendCommand(nodeId: String, action: String) {
        if (state.previewMode) return message("Commands are disabled in debug UI preview")
        val user = state.authUser ?: return
        val homeId = state.selectedHomeId ?: return
        val role = state.homeState.snapshot?.home?.role.orEmpty()
        if (action in ACCESS_COMMANDS && role != "access_admin") {
            return message("Access commands require the access_admin role")
        }
        message("Submitting request…")
        try {
            commandRepository.create(user.uid, homeId, nodeId, action) { requestId, error ->
                message(if (error == null) "Command request ${requestId.orEmpty()} submitted for gateway authorization"
                else error.message)
            }
        } catch (error: IllegalArgumentException) { message(error.message) }
    }

    fun addRoom(label: String) {
        if (state.previewMode) return message("Không thể tạo phòng trong chế độ xem thử")
        if (!canManageDevices("tạo phòng")) return
        val homeId = state.selectedHomeId ?: return
        message("Đang tạo phòng...")
        try {
            roomRepository.create(homeId, label) { _, error ->
                message(if (error == null) "Đã tạo phòng ${label.trim()}" else "Không tạo được phòng: ${error.message}")
            }
        } catch (error: IllegalArgumentException) { message("Không tạo được phòng: ${error.message}") }
    }

    fun deleteRoom(roomId: String) {
        if (state.previewMode) return message("Không thể xóa phòng trong chế độ xem thử")
        if (!canManageDevices("xóa phòng")) return
        val room = state.homeState.snapshot?.rooms?.firstOrNull { it.roomId == roomId }
            ?: return message("Phòng không tồn tại hoặc chưa được tải")
        if (room.nodeIds.isNotEmpty()) return message("Xóa thiết bị trong phòng trước khi xóa phòng")
        val homeId = state.selectedHomeId ?: return
        message("Đang xóa phòng...")
        try {
            roomRepository.delete(homeId, roomId) { _, error ->
                message(if (error == null) "Đã xóa phòng ${room.label}" else "Không xóa được phòng: ${error.message}")
            }
        } catch (error: IllegalArgumentException) { message("Không xóa được phòng: ${error.message}") }
    }

    fun addDevice(label: String, roomId: String, nodeType: String) {
        if (state.previewMode) return message("Không thể thêm thiết bị trong chế độ xem thử")
        if (!canManageDevices("thêm thiết bị")) return
        val homeId = state.selectedHomeId ?: return
        message("Đang thêm thiết bị...")
        try {
            deviceRepository.create(homeId, roomId, label, nodeType) { _, error ->
                message(if (error == null) "Đã thêm thiết bị ${label.trim()}" else "Không thêm được thiết bị: ${error.message}")
            }
        } catch (error: IllegalArgumentException) { message("Không thêm được thiết bị: ${error.message}") }
    }

    fun deleteDevice(nodeId: String, label: String?) {
        if (state.previewMode) return message("Không thể xóa thiết bị trong chế độ xem thử")
        if (!canManageDevices("xóa thiết bị")) return
        val homeId = state.selectedHomeId ?: return
        message("Đang xóa thiết bị...")
        try {
            deviceRepository.delete(homeId, nodeId) { deletedId, error ->
                message(if (error == null) "Đã xóa thiết bị ${label ?: deletedId.orEmpty()}"
                else "Không xóa được thiết bị: ${error.message}")
            }
        } catch (error: IllegalArgumentException) { message("Không xóa được thiết bị: ${error.message}") }
    }

    fun createHome(name: String, type: String, address: String, callback: (String?, String?) -> Unit) {
        message("Đang tạo nhà...")
        homeManagementRepository.createHome(name, type, address) { result, error ->
            val id = result?.homeId
            if (error != null || id == null) {
                val text = "Không tạo được nhà: ${error ?: "Backend không trả về Home ID"}"
                message(text); callback(null, text)
            } else {
                rememberHome(id, result.displayName)
                message("Đã tạo nhà ${result.displayName ?: id}"); callback(id, null)
            }
        }
    }

    fun createInvite(homeId: String? = null, callback: (String?, String?) -> Unit) {
        val id = homeId?.takeIf { it.isNotBlank() } ?: state.selectedHomeId
            ?: return callback(null, "Chưa chọn nhà để tạo mã mời")
        message("Đang tạo mã mời...")
        homeManagementRepository.createInvite(id) { result, error ->
            val code = result?.inviteCode
            message(error?.let { "Không tạo được mã mời: $it" } ?: "Mã mời: ${code.orEmpty()}")
            callback(code, error)
        }
    }

    fun redeemInvite(code: String, callback: (String?, String?) -> Unit) {
        message("Đang tham gia nhà...")
        homeManagementRepository.redeemInvite(code) { result, error ->
            val id = result?.homeId
            if (error != null || id == null) {
                val text = error ?: "Backend không trả về Home ID"; message("Không tham gia được nhà: $text"); callback(null, text)
            } else {
                rememberHome(id, result.displayName); message("Đã tham gia nhà ${result.displayName ?: id}"); callback(id, null)
            }
        }
    }

    fun deleteSelectedOwnedHome(callback: (String?, String?) -> Unit) {
        val id = state.selectedHomeId ?: return callback(null, "Chưa chọn nhà để xóa")
        message("Đang xóa nhà...")
        homeManagementRepository.deleteOwnedHome(id) { result, error ->
            if (error != null) { message("Không xóa được nhà: $error"); callback(null, error) }
            else {
                forgetHome(result?.homeId ?: id); message("Đã xóa nhà ${result?.displayName ?: id}"); callback(id, null)
            }
        }
    }

    fun clearCommandMessage() { state = state.copy(commandMessage = null); publish() }

    private fun canManageDevices(action: String): Boolean {
        if (state.authUser == null) { message("Bạn cần đăng nhập trước khi $action"); return false }
        if (state.selectedHomeId == null) { message("Chưa chọn Home ID nên chưa thể $action"); return false }
        val snapshot = state.homeState.snapshot
        if (snapshot == null) { message("Chưa thể $action vì tài khoản chưa có quyền đọc nhà ${state.selectedHomeId}"); return false }
        if (snapshot.home.role !in setOf("device_admin", "gateway_service")) {
            message("Tài khoản cần quyền device_admin để $action"); return false
        }
        return true
    }

    private fun observeUserHomes(user: AuthUser) {
        userHomesSubscription?.cancel()
        userHomesSubscription = userHomesRepository.observe(user.uid) { remote ->
            remote.forEach { names[it.homeId] = it.name; homeIdStore.add(it.homeId) }
            state = state.copy(homes = localHomes())
            if (remote.isNotEmpty() && state.selectedHomeId !in remote.map { it.homeId }) selectHome(remote.first().homeId)
            else publish()
        }
    }

    private fun rememberHome(id: String, name: String?) {
        homeIdStore.add(id); names[id] = name?.takeIf { it.isNotBlank() } ?: id
        state = state.copy(homes = localHomes()); selectHome(id)
    }

    private fun forgetHome(id: String) {
        if (state.selectedHomeId == id) homeSubscription?.cancel()
        homeIdStore.remove(id); names.remove(id)
        val homes = localHomes()
        state = state.copy(homes = homes, selectedHomeId = null,
            homeState = HomeUiState(LoadStatus.EMPTY, message = "Nhấn avatar để tạo hoặc tham gia nhà"))
        homes.firstOrNull()?.let { selectHome(it.homeId) } ?: publish()
    }

    private fun localHomes() = homeIdStore.load().map { HomeListItem(it, names[it] ?: it) }
    private fun message(value: String?) { state = state.copy(commandMessage = value); publish() }
    private fun publish() {
        val snapshot = state
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listeners.toList().forEach { it.onStateChanged(snapshot) }
        } else {
            mainHandler.post { listeners.toList().forEach { it.onStateChanged(snapshot) } }
        }
    }
    private fun cancelSubscriptions() { homeSubscription?.cancel(); userHomesSubscription?.cancel(); homeSubscription = null; userHomesSubscription = null }
    override fun close() = cancelSubscriptions()

    companion object { private val ACCESS_COMMANDS = setOf(DeviceActions.UNLOCK, DeviceActions.OPEN_DOOR) }
}
