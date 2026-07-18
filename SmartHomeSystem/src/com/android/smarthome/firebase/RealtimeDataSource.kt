package com.android.smarthome.firebase

enum class RealtimeErrorKind { PERMISSION_DENIED, OFFLINE, OTHER }
data class RealtimeError(val kind: RealtimeErrorKind, val message: String)

fun interface RealtimeSubscription { fun cancel() }

interface RealtimeDataSource {
    fun observe(path: String, listener: Listener): RealtimeSubscription
    fun readOnce(path: String, callback: (Any?, RealtimeError?) -> Unit)
    fun write(path: String, value: Any?, callback: (RealtimeError?) -> Unit)
    fun update(valuesByPath: Map<String, Any?>, callback: (RealtimeError?) -> Unit)

    interface Listener {
        fun onData(value: Any?)
        fun onError(error: RealtimeError)
    }
}

