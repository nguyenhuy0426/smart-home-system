package com.example.smart_home_mobile_app.repository

import com.example.smart_home_mobile_app.firebase.RealtimeError
import com.example.smart_home_mobile_app.firebase.RealtimeGateway
import java.util.UUID

class CommandRepository(
    private val gateway: RealtimeGateway,
    private val clock: () -> Long = System::currentTimeMillis,
    private val requestIdFactory: () -> String = {
        "cmd_" + UUID.randomUUID().toString().replace("-", "")
    },
) {
    fun create(
        userId: String,
        homeId: String,
        targetNodeId: String,
        commandType: String,
        callback: (requestId: String?, error: RealtimeError?) -> Unit,
    ) {
        requireIdentifier("userId", userId)
        requireIdentifier("homeId", homeId)
        requireIdentifier("targetNodeId", targetNodeId)
        requireIdentifier("commandType", commandType)
        val requestId = requestIdFactory()
        requireIdentifier("requestId", requestId)
        val payload = linkedMapOf<String, Any?>(
            "requestId" to requestId,
            "requestedBy" to userId,
            "homeId" to homeId,
            "nodeId" to targetNodeId,
            "action" to commandType,
            "createdAtEpochMs" to clock(),
            "status" to "pending",
        )
        gateway.write("homes/$homeId/commandRequests/$requestId", payload) { error ->
            callback(if (error == null) requestId else null, error)
        }
    }

    private fun requireIdentifier(name: String, value: String) {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"))) { "$name is invalid" }
    }
}

