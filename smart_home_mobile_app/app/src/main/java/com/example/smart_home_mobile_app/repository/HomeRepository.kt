package com.example.smart_home_mobile_app.repository

import com.example.smart_home_mobile_app.data.HomeSnapshotParser
import com.example.smart_home_mobile_app.data.HomeUiState
import com.example.smart_home_mobile_app.data.LoadStatus
import com.example.smart_home_mobile_app.firebase.RealtimeErrorKind
import com.example.smart_home_mobile_app.firebase.RealtimeGateway
import com.example.smart_home_mobile_app.firebase.Subscription

class HomeRepository(private val gateway: RealtimeGateway) {
    fun observe(homeId: String, uid: String, callback: (HomeUiState) -> Unit): Subscription {
        requireIdentifier("homeId", homeId)
        require(uid.isNotBlank()) { "uid is required" }
        var lastDataState = HomeUiState(status = LoadStatus.LOADING)
        var connected = true
        fun publish(state: HomeUiState) {
            lastDataState = state
            callback(if (connected) state.copy(connected = true) else state.copy(
                status = LoadStatus.OFFLINE,
                connected = false,
                message = "Offline; showing the last Firebase snapshot when available",
            ))
        }
        callback(lastDataState)
        val homeSubscription = gateway.observe("homes/$homeId", object : RealtimeGateway.Listener {
            override fun onData(value: Any?) {
                try {
                    val snapshot = HomeSnapshotParser.parse(homeId, uid, value)
                    if (snapshot.home.role.isBlank()) {
                        publish(HomeUiState(
                            status = LoadStatus.PERMISSION_DENIED,
                            message = "This account is not a member of home $homeId",
                        ))
                        return
                    }
                    val empty = snapshot.nodes.isEmpty() && snapshot.accessEvents.isEmpty() &&
                        snapshot.detectionEvents.isEmpty()
                    publish(HomeUiState(
                        status = if (empty) LoadStatus.EMPTY else LoadStatus.READY,
                        snapshot = snapshot,
                        message = if (empty) "This home has no node data yet" else null,
                    ))
                } catch (error: IllegalArgumentException) {
                    publish(HomeUiState(status = LoadStatus.ERROR, message = error.message))
                }
            }

            override fun onError(error: com.example.smart_home_mobile_app.firebase.RealtimeError) {
                val status = when (error.kind) {
                    RealtimeErrorKind.PERMISSION_DENIED -> LoadStatus.PERMISSION_DENIED
                    RealtimeErrorKind.OFFLINE -> LoadStatus.OFFLINE
                    RealtimeErrorKind.OTHER -> LoadStatus.ERROR
                }
                publish(HomeUiState(status = status, message = error.message, connected = status != LoadStatus.OFFLINE))
            }
        })
        val connectionSubscription = gateway.observe(".info/connected", object : RealtimeGateway.Listener {
            override fun onData(value: Any?) {
                connected = value as? Boolean ?: connected
                callback(if (connected) lastDataState.copy(connected = true) else lastDataState.copy(
                    status = LoadStatus.OFFLINE,
                    connected = false,
                    message = "Offline; showing the last Firebase snapshot when available",
                ))
            }

            override fun onError(error: com.example.smart_home_mobile_app.firebase.RealtimeError) {
                if (error.kind == RealtimeErrorKind.OFFLINE) {
                    connected = false
                    callback(lastDataState.copy(
                        status = LoadStatus.OFFLINE,
                        connected = false,
                        message = error.message,
                    ))
                }
            }
        })
        return Subscription {
            homeSubscription.cancel()
            connectionSubscription.cancel()
        }
    }

    private fun requireIdentifier(name: String, value: String) {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))) { "$name is invalid" }
    }
}
