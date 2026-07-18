package com.android.smarthome.firebase

import com.android.smarthome.security.FirebaseSessionStore
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Firebase RTDB REST implementation shared by the mobile UI and the AOSP gateway. */
class FirebaseRestDataSource(
    databaseUrl: String,
    private val sessionStore: FirebaseSessionStore,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) : RealtimeDataSource, AutoCloseable {
    private val baseUrl = databaseUrl.trim().trimEnd('/').also {
        require(it.startsWith("https://")) { "A secure Firebase Database URL is required" }
    }
    private val executor = Executors.newScheduledThreadPool(2)

    override fun observe(path: String, listener: RealtimeDataSource.Listener): RealtimeSubscription {
        if (path == ".info/connected") {
            listener.onData(true)
            return RealtimeSubscription { }
        }
        validatePath(path)
        val cancelled = AtomicBoolean(false)
        var lastPayload: String? = null
        val task: ScheduledFuture<*> = executor.scheduleWithFixedDelay({
            if (cancelled.get()) return@scheduleWithFixedDelay
            val result = request("GET", path, null)
            if (result.error != null) {
                listener.onError(result.error)
            } else if (result.body != lastPayload) {
                lastPayload = result.body
                listener.onData(parseJson(result.body))
            }
        }, 0L, pollIntervalMs, TimeUnit.MILLISECONDS)
        return RealtimeSubscription {
            cancelled.set(true)
            task.cancel(true)
        }
    }

    override fun readOnce(path: String, callback: (Any?, RealtimeError?) -> Unit) {
        validatePath(path)
        executor.execute {
            val result = request("GET", path, null)
            callback(if (result.error == null) parseJson(result.body) else null, result.error)
        }
    }

    override fun write(path: String, value: Any?, callback: (RealtimeError?) -> Unit) {
        validatePath(path)
        executor.execute { callback(request("PUT", path, encodeJson(value)).error) }
    }

    override fun update(valuesByPath: Map<String, Any?>, callback: (RealtimeError?) -> Unit) {
        val normalized = linkedMapOf<String, Any?>()
        valuesByPath.forEach { (path, value) ->
            validatePath(path)
            normalized[path.trim('/')] = value
        }
        executor.execute { callback(request("PATCH", "", encodeJson(normalized)).error) }
    }

    private fun request(method: String, path: String, body: String?): Response {
        val session = sessionStore.load()
            ?: return Response(error = RealtimeError(RealtimeErrorKind.PERMISSION_DENIED, "Firebase authentication is required"))
        val encodedToken = URLEncoder.encode(session.idToken, StandardCharsets.UTF_8.name())
        val endpoint = "$baseUrl/${path.trim('/')}.json?auth=$encodedToken"
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val responseBody = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (code in 200..299) Response(responseBody) else Response(error = httpError(code, responseBody))
        } catch (error: IOException) {
            Response(error = RealtimeError(RealtimeErrorKind.OFFLINE,
                error.message ?: "Firebase is unavailable"))
        } catch (error: Exception) {
            Response(error = RealtimeError(RealtimeErrorKind.OTHER,
                error.message ?: "Firebase request failed"))
        } finally {
            connection?.disconnect()
        }
    }

    private fun httpError(code: Int, body: String): RealtimeError {
        val kind = if (code == HttpURLConnection.HTTP_UNAUTHORIZED ||
            code == HttpURLConnection.HTTP_FORBIDDEN) RealtimeErrorKind.PERMISSION_DENIED
        else RealtimeErrorKind.OTHER
        return RealtimeError(kind, body.takeIf { it.isNotBlank() } ?: "Firebase returned HTTP $code")
    }

    private fun validatePath(path: String) {
        require(path.isEmpty() || path.split('/').all { segment ->
            segment.isNotEmpty() && segment.length <= 128 &&
                segment.none { it == '.' || it == '#' || it == '$' || it == '[' || it == ']' }
        }) { "Firebase path is invalid" }
    }

    private fun parseJson(text: String?): Any? {
        if (text.isNullOrBlank()) return null
        return jsonToKotlin(JSONTokener(text).nextValue())
    }

    private fun jsonToKotlin(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { jsonToKotlin(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { jsonToKotlin(value.get(it)) }
        else -> value
    }

    private fun encodeJson(value: Any?): String = when (value) {
        null -> "null"
        is Map<*, *> -> JSONObject(value).toString()
        is Collection<*> -> JSONArray(value).toString()
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> JSONObject.wrap(value)?.toString() ?: "null"
    }

    override fun close() { executor.shutdownNow() }

    private data class Response(val body: String? = null, val error: RealtimeError? = null)

    companion object {
        private const val DEFAULT_POLL_INTERVAL_MS = 3_000L
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
