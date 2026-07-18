package com.android.smarthome.repository

import com.android.smarthome.data.LoadStatus
import com.android.smarthome.firebase.*
import com.android.smarthome.security.AuthValidation
import org.junit.Assert.*
import org.junit.Test

class RepositoryPortTest {
    @Test
    fun commandWritesOnlyRequiredCommandRequestPayload() {
        val source = FakeSource()
        var returnedId: String? = null
        CommandRepository(source, { 1234L }, { "cmd_test_1" }).create(
            "user_1", "home_1", "node_1", "unlock"
        ) { id, error -> returnedId = id; assertNull(error) }

        assertEquals("cmd_test_1", returnedId)
        assertEquals("homes/home_1/commandRequests/cmd_test_1", source.writtenPath)
        val payload = source.writtenValue as Map<*, *>
        assertEquals("user_1", payload["requestedBy"])
        assertEquals("pending", payload["status"])
        assertFalse(source.writtenPath!!.contains("readings"))
    }

    @Test
    fun roomUsesParserCompatibleScalarSchema() {
        val source = FakeSource()
        RoomRepository(source) { "room_living" }.create("home_1", "  Living room  ") { id, error ->
            assertEquals("room_living", id); assertNull(error)
        }
        assertEquals("homes/home_1/rooms/room_living", source.writtenPath)
        assertEquals("Living room", source.writtenValue)
    }

    @Test
    fun permissionDeniedAndEmptyHomeHaveTypedStates() {
        val denied = mutableListOf<LoadStatus>()
        HomeRepository(FakeSource(error = RealtimeError(RealtimeErrorKind.PERMISSION_DENIED, "denied")))
            .observe("home_1", "user_1") { denied += it.status }
        assertEquals(LoadStatus.PERMISSION_DENIED, denied.last())

        val empty = mutableListOf<LoadStatus>()
        HomeRepository(FakeSource(mapOf("members" to mapOf("user_1" to mapOf("role" to "home_member")))))
            .observe("home_1", "user_1") { empty += it.status }
        assertEquals(LoadStatus.EMPTY, empty.last())
    }

    @Test
    fun authValidationMatchesSourceApplication() {
        assertEquals("Enter a valid email address", AuthValidation.validate("invalid", "secret1"))
        assertEquals("Password must contain at least 6 characters", AuthValidation.validate("a@example.com", "123"))
        assertNull(AuthValidation.validate(" a@example.com ", "secret1"))
    }

    private class FakeSource(
        private val observed: Any? = null,
        private val error: RealtimeError? = null
    ) : RealtimeDataSource {
        var writtenPath: String? = null
        var writtenValue: Any? = null

        override fun observe(path: String, listener: RealtimeDataSource.Listener): RealtimeSubscription {
            if (path == ".info/connected") listener.onData(true)
            else if (error != null) listener.onError(error) else listener.onData(observed)
            return RealtimeSubscription { }
        }

        override fun readOnce(path: String, callback: (Any?, RealtimeError?) -> Unit) = callback(observed, error)
        override fun write(path: String, value: Any?, callback: (RealtimeError?) -> Unit) {
            writtenPath = path; writtenValue = value; callback(error)
        }
        override fun update(valuesByPath: Map<String, Any?>, callback: (RealtimeError?) -> Unit) = callback(error)
    }
}
