package com.example.smart_home_mobile_app.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

enum class RealtimeErrorKind { PERMISSION_DENIED, OFFLINE, OTHER }

data class RealtimeError(val kind: RealtimeErrorKind, val message: String)

fun interface Subscription {
    fun cancel()
}

interface RealtimeGateway {
    fun observe(path: String, listener: Listener): Subscription
    fun write(path: String, value: Map<String, Any?>, callback: (RealtimeError?) -> Unit)

    interface Listener {
        fun onData(value: Any?)
        fun onError(error: RealtimeError)
    }
}

class FirebaseRealtimeGateway(private val database: FirebaseDatabase) : RealtimeGateway {
    override fun observe(path: String, listener: RealtimeGateway.Listener): Subscription {
        val reference = reference(path)
        val firebaseListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = listener.onData(snapshot.value)
            override fun onCancelled(error: DatabaseError) = listener.onError(error.toRealtimeError())
        }
        reference.addValueEventListener(firebaseListener)
        return Subscription { reference.removeEventListener(firebaseListener) }
    }

    override fun write(
        path: String,
        value: Map<String, Any?>,
        callback: (RealtimeError?) -> Unit,
    ) {
        reference(path).setValue(value).addOnCompleteListener { task ->
            val exception = task.exception
            callback(if (exception == null) null else RealtimeError(
                kind = if (exception.message?.contains("permission", ignoreCase = true) == true) {
                    RealtimeErrorKind.PERMISSION_DENIED
                } else {
                    RealtimeErrorKind.OTHER
                },
                message = exception.message ?: "Realtime Database write failed",
            ))
        }
    }

    private fun reference(path: String): DatabaseReference {
        require(!path.startsWith('/') && ".." !in path) { "Unsafe RTDB path" }
        val segments = path.split('/').filter(String::isNotBlank)
        val infoPath = segments == listOf(".info", "connected")
        return segments.foldIndexed(database.reference) { index, current, segment ->
            require(
                segment.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) ||
                    (infoPath && index == 0 && segment == ".info"),
            ) {
                "Unsafe RTDB path segment"
            }
            current.child(segment)
        }
    }

    private fun DatabaseError.toRealtimeError(): RealtimeError {
        val kind = when (code) {
            DatabaseError.PERMISSION_DENIED -> RealtimeErrorKind.PERMISSION_DENIED
            DatabaseError.DISCONNECTED, DatabaseError.NETWORK_ERROR -> RealtimeErrorKind.OFFLINE
            else -> RealtimeErrorKind.OTHER
        }
        return RealtimeError(kind, message)
    }
}
