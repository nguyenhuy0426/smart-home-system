package com.example.smart_home_mobile_app.repository

import com.example.smart_home_mobile_app.data.HomeUiState
import com.example.smart_home_mobile_app.data.LoadStatus
import com.example.smart_home_mobile_app.firebase.RealtimeError
import com.example.smart_home_mobile_app.firebase.RealtimeErrorKind
import com.example.smart_home_mobile_app.firebase.RealtimeGateway
import com.example.smart_home_mobile_app.firebase.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryTest {
    @Test
    fun permissionDeniedIsExposedAsPermissionDeniedState() {
        val gateway = FakeGateway(error = RealtimeError(RealtimeErrorKind.PERMISSION_DENIED, "denied"))
        val states = ArrayList<HomeUiState>()
        HomeRepository(gateway).observe("home_1", "user_1", states::add)
        assertEquals(LoadStatus.LOADING, states.first().status)
        assertEquals(LoadStatus.PERMISSION_DENIED, states.last().status)
    }

    @Test
    fun emptyHomeIsExposedAsEmptyState() {
        val gateway = FakeGateway(value = mapOf(
            "members" to mapOf("user_1" to mapOf("role" to "home_member")),
        ))
        val states = ArrayList<HomeUiState>()
        HomeRepository(gateway).observe("home_1", "user_1", states::add)
        assertEquals(LoadStatus.EMPTY, states.last().status)
        assertNotNull(states.last().snapshot)
    }

    @Test
    fun commandCreatesOnlyCommandRequestWithRequiredIdentityAndStatus() {
        val gateway = FakeGateway()
        val repository = CommandRepository(gateway, { 1234L }, { "cmd_test_1" })
        var returnedId: String? = null
        repository.create("user_1", "home_1", "node_1", "unlock") { id, error ->
            returnedId = id
            assertEquals(null, error)
        }
        assertEquals("cmd_test_1", returnedId)
        assertEquals("homes/home_1/commandRequests/cmd_test_1", gateway.writtenPath)
        assertEquals("user_1", gateway.writtenValue?.get("requestedBy"))
        assertEquals("home_1", gateway.writtenValue?.get("homeId"))
        assertEquals("node_1", gateway.writtenValue?.get("nodeId"))
        assertEquals("unlock", gateway.writtenValue?.get("action"))
        assertEquals("pending", gateway.writtenValue?.get("status"))
        assertTrue(gateway.writtenPath?.contains("readings") == false)
    }

    private class FakeGateway(
        private val value: Any? = null,
        private val error: RealtimeError? = null,
    ) : RealtimeGateway {
        var writtenPath: String? = null
        var writtenValue: Map<String, Any?>? = null

        override fun observe(path: String, listener: RealtimeGateway.Listener): Subscription {
            if (path == ".info/connected") {
                listener.onData(true)
            } else if (error != null) {
                listener.onError(error)
            } else {
                listener.onData(value)
            }
            return Subscription { }
        }

        override fun write(path: String, value: Map<String, Any?>, callback: (RealtimeError?) -> Unit) {
            writtenPath = path
            writtenValue = value
            callback(error)
        }
    }
}
