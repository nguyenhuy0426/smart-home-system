package com.android.smarthome.repository

import android.content.Context
import com.android.smarthome.data.*
import com.android.smarthome.firebase.*
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

class HomeIdStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): List<String> = preferences.getStringSet(KEY_HOME_IDS, emptySet()).orEmpty().sorted()

    fun add(homeId: String): List<String> {
        require(HOME_ID.matches(homeId)) { "Invalid home ID" }
        return persist(load().toMutableSet().apply { add(homeId) })
    }

    fun remove(homeId: String): List<String> = persist(load().toMutableSet().apply { remove(homeId) })

    private fun persist(ids: Set<String>): List<String> {
        preferences.edit().putStringSet(KEY_HOME_IDS, ids).apply()
        return ids.sorted()
    }

    companion object {
        private const val PREFERENCES = "mobile_home_ids"
        private const val KEY_HOME_IDS = "home_ids"
        private val HOME_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
    }
}

class HomeRepository(private val source: RealtimeDataSource) {
    fun observe(homeId: String, uid: String, callback: (HomeUiState) -> Unit): RealtimeSubscription {
        requireIdentifier("homeId", homeId)
        require(uid.isNotBlank()) { "uid is required" }
        val observation = Observation(callback)
        callback(observation.lastState)
        val home = source.observe("homes/$homeId", object : RealtimeDataSource.Listener {
            override fun onData(value: Any?) = observation.onHomeData(homeId, uid, value)
            override fun onError(error: RealtimeError) = observation.onError(error)
        })
        val connection = source.observe(".info/connected", object : RealtimeDataSource.Listener {
            override fun onData(value: Any?) = observation.onConnected(value)
            override fun onError(error: RealtimeError) = observation.onConnectionError(error)
        })
        return RealtimeSubscription { home.cancel(); connection.cancel() }
    }

    private class Observation(private val callback: (HomeUiState) -> Unit) {
        var lastState = HomeUiState()
        private var connected = true

        private fun publish(state: HomeUiState) {
            lastState = state
            callback(if (connected) state.withConnected(true) else state.asOffline(OFFLINE_MESSAGE))
        }

        fun onHomeData(homeId: String, uid: String, value: Any?) {
            try {
                val snapshot = HomeSnapshotParser.parse(homeId, uid, value)
                if (snapshot.home.role.isBlank()) {
                    publish(HomeUiState(LoadStatus.PERMISSION_DENIED,
                        message = "This account is not a member of home $homeId"))
                    return
                }
                val empty = snapshot.nodes.isEmpty() && snapshot.accessEvents.isEmpty() &&
                    snapshot.detectionEvents.isEmpty()
                publish(HomeUiState(if (empty) LoadStatus.EMPTY else LoadStatus.READY, snapshot,
                    if (empty) "This home has no node data yet" else null))
            } catch (error: IllegalArgumentException) {
                publish(HomeUiState(LoadStatus.ERROR, message = error.message))
            }
        }

        fun onError(error: RealtimeError) {
            val status = when (error.kind) {
                RealtimeErrorKind.PERMISSION_DENIED -> LoadStatus.PERMISSION_DENIED
                RealtimeErrorKind.OFFLINE -> LoadStatus.OFFLINE
                else -> LoadStatus.ERROR
            }
            publish(HomeUiState(status, message = error.message, connected = status != LoadStatus.OFFLINE))
        }

        fun onConnected(value: Any?) {
            if (value is Boolean) connected = value
            callback(if (connected) lastState.withConnected(true) else lastState.asOffline(OFFLINE_MESSAGE))
        }

        fun onConnectionError(error: RealtimeError) {
            if (error.kind == RealtimeErrorKind.OFFLINE) {
                connected = false
                callback(lastState.asOffline(error.message))
            }
        }
    }

    companion object {
        private const val OFFLINE_MESSAGE = "Offline; showing the last Firebase snapshot when available"
    }
}

class CommandRepository(
    private val source: RealtimeDataSource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val requestIdFactory: () -> String = { "cmd_${UUID.randomUUID().toString().replace("-", "")}" }
) {
    fun create(userId: String, homeId: String, nodeId: String, action: String,
               callback: (String?, RealtimeError?) -> Unit) {
        listOf("userId" to userId, "homeId" to homeId, "targetNodeId" to nodeId,
            "commandType" to action).forEach { requireIdentifier(it.first, it.second, true) }
        val requestId = requestIdFactory().also { requireIdentifier("requestId", it, true) }
        val payload = linkedMapOf<String, Any?>(
            "requestId" to requestId, "requestedBy" to userId, "homeId" to homeId,
            "nodeId" to nodeId, "action" to action, "createdAtEpochMs" to clock(), "status" to "pending"
        )
        source.write("homes/$homeId/commandRequests/$requestId", payload) { error ->
            callback(if (error == null) requestId else null, error)
        }
    }
}

class RoomRepository(
    private val source: RealtimeDataSource,
    private val roomIdFactory: () -> String = { "room_${UUID.randomUUID().toString().replace("-", "")}" }
) {
    fun create(homeId: String, label: String, callback: (String?, RealtimeError?) -> Unit) {
        requireIdentifier("homeId", homeId, true)
        require(label.isNotBlank()) { "label is required" }
        val roomId = roomIdFactory().also { requireIdentifier("roomId", it, true) }
        source.write("homes/$homeId/rooms/$roomId", label.trim()) { error ->
            callback(if (error == null) roomId else null, error)
        }
    }

    fun delete(homeId: String, roomId: String, callback: (String?, RealtimeError?) -> Unit) {
        requireIdentifier("homeId", homeId, true); requireIdentifier("roomId", roomId, true)
        source.write("homes/$homeId/rooms/$roomId", null) { callback(if (it == null) roomId else null, it) }
    }
}

class DeviceRepository(
    private val source: RealtimeDataSource,
    private val nodeIdFactory: () -> String = { "node_${UUID.randomUUID().toString().replace("-", "")}" }
) {
    fun create(homeId: String, roomId: String, label: String, nodeType: String,
               callback: (String?, RealtimeError?) -> Unit) {
        listOf("homeId" to homeId, "roomId" to roomId, "nodeType" to nodeType)
            .forEach { requireIdentifier(it.first, it.second, true) }
        require(label.isNotBlank()) { "label is required" }
        val nodeId = nodeIdFactory().also { requireIdentifier("nodeId", it, true) }
        val payload = linkedMapOf<String, Any?>(
            "nodeId" to nodeId, "label" to label.trim(), "nodeType" to nodeType,
            "roomId" to roomId, "status" to "unpaired", "source" to "manual",
            "provisioned" to false, "schemaVersion" to 1, "actions" to defaultActions(nodeType)
        )
        source.write("homes/$homeId/nodes/$nodeId", payload) { error ->
            callback(if (error == null) nodeId else null, error)
        }
    }

    fun delete(homeId: String, nodeId: String, callback: (String?, RealtimeError?) -> Unit) {
        requireIdentifier("homeId", homeId, true); requireIdentifier("nodeId", nodeId, true)
        source.write("homes/$homeId/nodes/$nodeId", null) { callback(if (it == null) nodeId else null, it) }
    }

    private fun defaultActions(type: String) = when (type) {
        "light", "air_conditioner" -> listOf(DeviceActions.TOGGLE, DeviceActions.SET_MODE, DeviceActions.SET_INTENSITY)
        "fan" -> listOf(DeviceActions.TOGGLE, DeviceActions.SET_INTENSITY)
        "door_lock" -> listOf(DeviceActions.UNLOCK, DeviceActions.LOCK)
        else -> emptyList()
    }
}

class UserHomesRepository(private val source: RealtimeDataSource) {
    fun observe(uid: String, callback: (List<HomeListItem>) -> Unit): RealtimeSubscription {
        require(uid.isNotBlank()) { "uid is required" }
        return source.observe("userHomes/$uid", object : RealtimeDataSource.Listener {
            override fun onData(value: Any?) = callback(parse(value))
            override fun onError(error: RealtimeError) = callback(emptyList())
        })
    }

    internal fun parse(value: Any?): List<HomeListItem> = (value as? Map<*, *>)?.mapNotNull { (key, raw) ->
        val id = key as? String ?: return@mapNotNull null
        if (!SIMPLE_IDENTIFIER.matches(id)) return@mapNotNull null
        val map = raw as? Map<*, *>
        val name = when {
            raw is String && raw.isNotBlank() -> raw.trim()
            map?.get("displayName") is String -> (map["displayName"] as String).trim().ifBlank { id }
            map?.get("name") is String -> (map["name"] as String).trim().ifBlank { id }
            else -> id
        }
        HomeListItem(id, name)
    }?.sortedBy { it.name.lowercase(Locale.getDefault()) }.orEmpty()
}

class HomeManagementRepository(
    private val source: RealtimeDataSource,
    private val currentUser: () -> AuthUser?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom()
) {
    fun createHome(name: String, type: String, address: String,
                   callback: (HomeActionResult?, String?) -> Unit) {
        val user = currentUser() ?: return callback(null, "Bạn cần đăng nhập trước khi tạo nhà")
        val cleanName = clean(name, 80)
        if (cleanName.isEmpty()) return callback(null, "Tên nhà không được để trống")
        val homeId = "home_${UUID.randomUUID().toString().replace("-", "")}" 
        val role = "device_admin"
        val payload = linkedMapOf<String, Any?>(
            "homeId" to homeId, "name" to cleanName, "displayName" to cleanName,
            "type" to clean(type, 40), "address" to clean(address, 160),
            "owner" to user.uid, "ownerUid" to user.uid, "createdAtEpochMs" to clock(),
            "members" to mapOf(user.uid to mapOf("role" to role, "r" to "admin"))
        )
        source.update(mapOf("homes/$homeId" to payload, "userHomes/${user.uid}/$homeId" to cleanName)) {
            if (it == null) callback(HomeActionResult(homeId, cleanName, role), null)
            else callback(null, it.message)
        }
    }

    fun createInvite(homeId: String, callback: (HomeActionResult?, String?) -> Unit) {
        val user = currentUser() ?: return callback(null, "Bạn cần đăng nhập trước khi tạo mã mời")
        val id = clean(homeId, 128)
        if (id.isEmpty()) return callback(null, "Chưa chọn nhà để tạo mã mời")
        source.readOnce("homes/$id") { raw, error ->
            if (error != null) return@readOnce callback(null, error.message)
            val home = raw as? Map<*, *> ?: return@readOnce callback(null, "Nhà không tồn tại")
            val owner = home["owner"] ?: home["ownerUid"]
            if (owner != user.uid) return@readOnce callback(null, "Chỉ chủ nhà mới được tạo mã mời")
            val name = stringValue(home["name"], stringValue(home["displayName"], id))
            val code = randomInviteCode()
            val invite = mapOf("h" to id, "r" to "member", "e" to clock() + INVITE_TTL_MS, "n" to name)
            source.write("homeInvites/$code", invite) {
                if (it == null) callback(HomeActionResult(id, name, "home_member", code), null)
                else callback(null, it.message)
            }
        }
    }

    fun redeemInvite(code: String, callback: (HomeActionResult?, String?) -> Unit) {
        val user = currentUser() ?: return callback(null, "Bạn cần đăng nhập trước khi tham gia nhà")
        val cleanCode = clean(code, 32).uppercase(Locale.ROOT)
        if (cleanCode.isEmpty()) return callback(null, "Mã mời không được để trống")
        source.readOnce("homeInvites/$cleanCode") { raw, error ->
            if (error != null) return@readOnce callback(null, error.message)
            val invite = raw as? Map<*, *> ?: return@readOnce callback(null, "Không tìm thấy mã mời")
            val homeId = stringValue(invite["h"], stringValue(invite["homeId"], ""))
            if (homeId.isEmpty()) return@readOnce callback(null, "Mã mời không hợp lệ")
            val expires = (invite["e"] as? Number)?.toLong()
                ?: (invite["expiresAtEpochMs"] as? Number)?.toLong()
            if (expires != null && expires < clock()) return@readOnce callback(null, "Mã mời đã hết hạn")
            val name = stringValue(invite["n"], stringValue(invite["homeName"], homeId))
            val role = normalizeRole(stringValue(invite["r"], stringValue(invite["role"], "home_member")))
            source.update(mapOf(
                "homes/$homeId/members/${user.uid}" to mapOf("r" to compactRole(role), "c" to cleanCode),
                "userHomes/${user.uid}/$homeId" to name
            )) {
                if (it == null) callback(HomeActionResult(homeId, name, role), null)
                else callback(null, it.message)
            }
        }
    }

    fun deleteOwnedHome(homeId: String, callback: (HomeActionResult?, String?) -> Unit) {
        val user = currentUser() ?: return callback(null, "Bạn cần đăng nhập trước khi xóa nhà")
        val id = clean(homeId, 128)
        if (id.isEmpty()) return callback(null, "Chưa chọn nhà để xóa")
        source.readOnce("homes/$id") { raw, error ->
            if (error != null) return@readOnce callback(null, error.message)
            val home = raw as? Map<*, *> ?: return@readOnce callback(null,
                "Nhà không tồn tại hoặc tài khoản chưa có quyền đọc")
            val owner = home["owner"] ?: home["ownerUid"]
            if (owner != user.uid) return@readOnce callback(null, "Chỉ chủ nhà mới được xóa nhà này")
            val name = stringValue(home["name"], stringValue(home["displayName"], id))
            val updates = linkedMapOf<String, Any?>("homes/$id" to null, "userHomes/${user.uid}/$id" to null)
            (home["members"] as? Map<*, *>)?.keys?.filterIsInstance<String>()?.forEach {
                updates["userHomes/$it/$id"] = null
            }
            source.update(updates) {
                if (it == null) callback(HomeActionResult(id, name), null) else callback(null, it.message)
            }
        }
    }

    private fun randomInviteCode() = buildString(8) { repeat(8) { append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]) } }
    private fun clean(value: String?, max: Int) = value.orEmpty().trim().take(max)
    private fun stringValue(value: Any?, fallback: String) = (value as? String) ?: fallback
    private fun normalizeRole(role: String) = when (role) { "admin", "owner" -> "device_admin"; "member" -> "home_member"; else -> role.ifBlank { "home_member" } }
    private fun compactRole(role: String) = when (role) { "device_admin", "gateway_service" -> "admin"; "home_member" -> "member"; else -> role.ifBlank { "member" } }

    companion object {
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

private val SIMPLE_IDENTIFIER = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
private val EXTENDED_IDENTIFIER = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
private fun requireIdentifier(name: String, value: String, extended: Boolean = false) {
    require((if (extended) EXTENDED_IDENTIFIER else SIMPLE_IDENTIFIER).matches(value)) { "$name is invalid" }
}
