package com.android.smarthome.gateway

import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors

/** Receives bounded JSON telemetry requests from provisioned nodes on the home LAN. */
class WifiDataReceiver(private val listener: TelemetryListener) {

    private var serverSocket: ServerSocket? = null
    private val acceptExecutor = Executors.newSingleThreadExecutor()
    private val requestExecutor = Executors.newFixedThreadPool(4)

    @Volatile
    private var running = false

    interface TelemetryListener {
        fun onTelemetryReceived(nodeId: String, roomId: String, payload: String)
        fun onAccessEventReceived(nodeId: String, roomId: String, payload: String)
    }

    companion object {
        private const val TAG = "WifiDataReceiver"
        private const val PORT = 8080
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val MAX_HEADER_LINE_BYTES = 8 * 1024
        private const val MAX_HEADER_BYTES = 32 * 1024
        private const val MAX_HEADER_COUNT = 64
        private const val MAX_BODY_BYTES = 64 * 1024
        private val IDENTIFIER_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
    }

    fun start() {
        if (running) return
        running = true
        acceptExecutor.execute {
            try {
                val socket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT))
                }
                serverSocket = socket
                Log.i(TAG, "HTTP telemetry receiver listening on port $PORT")
                while (running) {
                    val client = try {
                        socket.accept()
                    } catch (e: java.net.SocketException) {
                        if (running) Log.e(TAG, "Telemetry accept failed", e)
                        break
                    }
                    client.soTimeout = SOCKET_TIMEOUT_MS
                    requestExecutor.execute { handleClient(client) }
                }
            } catch (e: Exception) {
                running = false
                Log.e(TAG, "Failed to start HTTP telemetry receiver", e)
            } finally {
                serverSocket?.close()
                serverSocket = null
            }
        }
    }

    fun stop() {
        running = false
        serverSocket?.close()
        acceptExecutor.shutdownNow()
        requestExecutor.shutdownNow()
        Log.i(TAG, "HTTP telemetry receiver stopped")
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                val input = BufferedInputStream(client.getInputStream())
                val requestLine = readAsciiLine(input)
                    ?: throw HttpRequestException(400, "Missing request line")
                val requestParts = requestLine.split(' ')
                if (requestParts.size != 3) {
                    throw HttpRequestException(400, "Malformed request line")
                }
                if (requestParts[2] != "HTTP/1.1" && requestParts[2] != "HTTP/1.0") {
                    throw HttpRequestException(400, "Unsupported HTTP version")
                }
                if (requestParts[0] != "POST") {
                    throw HttpRequestException(405, "Method Not Allowed")
                }
                if (requestParts[1] != "/api/readings") {
                    throw HttpRequestException(404, "Not Found")
                }

                val headers = LinkedHashMap<String, String>()
                var headerBytes = 0
                while (true) {
                    val line = readAsciiLine(input)
                        ?: throw HttpRequestException(400, "Truncated headers")
                    if (line.isEmpty()) break
                    headerBytes += line.length
                    if (headers.size >= MAX_HEADER_COUNT || headerBytes > MAX_HEADER_BYTES) {
                        throw HttpRequestException(431, "Request headers too large")
                    }
                    val separator = line.indexOf(':')
                    if (separator <= 0) {
                        throw HttpRequestException(400, "Malformed header")
                    }
                    val name = line.substring(0, separator).trim().lowercase(Locale.US)
                    if (name in headers) {
                        throw HttpRequestException(400, "Duplicate header")
                    }
                    headers[name] = line.substring(separator + 1).trim()
                }

                val contentType = headers["content-type"]
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase(Locale.US)
                if (contentType != "application/json") {
                    throw HttpRequestException(415, "Content-Type must be application/json")
                }
                val contentLength = headers["content-length"]?.toIntOrNull()
                    ?: throw HttpRequestException(411, "Content-Length required")
                if (contentLength <= 0 || contentLength > MAX_BODY_BYTES) {
                    throw HttpRequestException(413, "Invalid request body size")
                }
                val body = String(readExactly(input, contentLength), StandardCharsets.UTF_8)
                dispatchPayload(body)
                sendResponse(client, 200, "OK")
            } catch (e: HttpRequestException) {
                sendResponse(client, e.status, e.message ?: "Bad Request")
            } catch (e: Exception) {
                Log.w(TAG, "Rejected telemetry request from ${client.inetAddress.hostAddress}", e)
                sendResponse(client, 400, "Bad Request")
            }
        }
    }

    private fun dispatchPayload(body: String) {
        val json = JSONObject(body)
        val nodeId = json.optString("nodeId")
        val roomId = json.optString("roomId")
        if (!IDENTIFIER_PATTERN.matches(nodeId) || !IDENTIFIER_PATTERN.matches(roomId)) {
            throw HttpRequestException(400, "nodeId or roomId is invalid")
        }

        when {
            json.has("metrics") -> listener.onTelemetryReceived(nodeId, roomId, body)
            json.optString("eventType") == "access.attempt" ->
                listener.onAccessEventReceived(nodeId, roomId, body)
            else -> throw HttpRequestException(422, "Unsupported payload type")
        }
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() <= MAX_HEADER_LINE_BYTES) {
            val value = input.read()
            if (value == -1) return null
            if (value == '\n'.code) {
                val line = bytes.toByteArray()
                val length = if (line.isNotEmpty() && line.last() == '\r'.code.toByte()) {
                    line.size - 1
                } else {
                    line.size
                }
                return String(line, 0, length, StandardCharsets.US_ASCII)
            }
            bytes.write(value)
        }
        throw HttpRequestException(431, "Header line too large")
    }

    private fun readExactly(input: BufferedInputStream, length: Int): ByteArray {
        val body = ByteArray(length)
        var offset = 0
        while (offset < body.size) {
            val count = input.read(body, offset, body.size - offset)
            if (count < 0) throw HttpRequestException(400, "Truncated request body")
            offset += count
        }
        return body
    }

    private fun sendResponse(socket: Socket, status: Int, message: String) {
        try {
            val body = message.toByteArray(StandardCharsets.UTF_8)
            val reason = when (status) {
                200 -> "OK"
                400 -> "Bad Request"
                404 -> "Not Found"
                405 -> "Method Not Allowed"
                411 -> "Length Required"
                413 -> "Payload Too Large"
                415 -> "Unsupported Media Type"
                422 -> "Unprocessable Content"
                431 -> "Request Header Fields Too Large"
                else -> "Error"
            }
            val headers = "HTTP/1.1 $status $reason\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(headers.toByteArray(StandardCharsets.US_ASCII))
                write(body)
                flush()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not send HTTP error response", e)
        }
    }

    private class HttpRequestException(val status: Int, message: String) : Exception(message)
}
