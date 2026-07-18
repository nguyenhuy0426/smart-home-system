package com.android.smarthome.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/** Authenticated RTSP client and RFC 2435 RTP/JPEG receiver for provisioned cameras. */
class RtspFrameReceiver(
    private val sources: List<CameraSource>,
    private val listener: FrameListener
) {
    data class CameraSource(
        val nodeId: String,
        val roomId: String,
        val host: String,
        val port: Int,
        val streamPath: String,
        val authKey: String
    )

    data class CameraIdentity(val nodeId: String, val roomId: String)

    data class FrameMetadata(
        val ssrc: Long,
        val rtpTimestamp: Long,
        val receivedAtEpochMs: Long
    )

    interface FrameListener {
        fun onFrameReceived(camera: CameraIdentity, bitmap: Bitmap, metadata: FrameMetadata)
    }

    companion object {
        private const val TAG = "RtspFrameReceiver"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val CONTROL_TIMEOUT_MS = 5_000
        private const val RTP_RECEIVE_TIMEOUT_MS = 500
        private const val RTP_IDLE_TIMEOUT_MS = 5_000L
        private const val MAX_RTP_PACKET_BYTES = 2_048
        private const val MAX_FRAME_BYTES = 4 * 1024 * 1024
        private const val MAX_PACKETS_PER_FRAME = 512
        private const val FRAME_TIMEOUT_MS = 500L
        private const val MAX_INVALID_PACKETS = 20
        private const val CLIENT_PORT_FIRST = 40_000
        private const val CLIENT_PORT_LAST = 60_000
        const val MAX_CAMERA_SOURCES = 16
    }

    private val running = AtomicBoolean(false)
    private val random = SecureRandom()
    private val activeConnections = ConcurrentHashMap<String, Connection>()
    private var executor: ExecutorService? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        if (sources.isEmpty()) {
            running.set(false)
            return
        }
        require(sources.size <= MAX_CAMERA_SOURCES) { "too many camera sources" }
        executor = Executors.newFixedThreadPool(sources.size)
        sources.forEach { source -> executor?.submit { runSource(source) } }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        activeConnections.values.forEach { it.close() }
        activeConnections.clear()
        executor?.shutdownNow()
        executor?.awaitTermination(2, TimeUnit.SECONDS)
        executor = null
        Log.i(TAG, "RTSP camera ingestion stopped")
    }

    private fun runSource(source: CameraSource) {
        var retry = 0
        while (running.get() && !Thread.currentThread().isInterrupted) {
            try {
                runSession(source)
                retry = 0
            } catch (e: Exception) {
                if (!running.get()) break
                Log.e(TAG, "Camera ${source.nodeId} RTSP session failed: ${e.message}")
                retry = (retry + 1).coerceAtMost(6)
                try {
                    Thread.sleep((1_000L shl (retry - 1)).coerceAtMost(30_000L))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }

    private fun runSession(source: CameraSource) {
        validateSource(source)
        val address = InetAddress.getByName(source.host)
        val (rtpSocket, rtcpSocket) = openUdpPair()
        val connection = Connection(source, rtpSocket, rtcpSocket)
        activeConnections[source.nodeId] = connection
        try {
            connection.control.connect(InetSocketAddress(address, source.port), CONNECT_TIMEOUT_MS)
            connection.control.soTimeout = CONTROL_TIMEOUT_MS
            connection.control.keepAlive = true
            connection.input = BufferedInputStream(connection.control.getInputStream())
            connection.output = BufferedOutputStream(connection.control.getOutputStream())

            val baseUri = buildBaseUri(source)
            val options = connection.request("OPTIONS", baseUri)
            RtspProtocol.requireSuccess(options)
            val publicMethods = options.header("public")
                ?: throw RtspProtocol.ProtocolException("OPTIONS response has no Public header")
            listOf("OPTIONS", "DESCRIBE", "SETUP", "PLAY", "TEARDOWN").forEach { method ->
                if (!publicMethods.uppercase(Locale.US).split(',').map { it.trim() }.contains(method)) {
                    throw RtspProtocol.ProtocolException("Camera does not advertise $method")
                }
            }

            val describe = connection.request("DESCRIBE", baseUri, acceptSdp = true)
            RtspProtocol.requireSuccess(describe)
            if (!describe.header("content-type").orEmpty().lowercase(Locale.US)
                    .startsWith("application/sdp")) {
                throw RtspProtocol.ProtocolException("DESCRIBE response is not SDP")
            }
            val sdp = SdpParser.parse(describe.body)
            SdpParser.requireIdentity(sdp, source.nodeId, source.roomId)
            val mediaUri = RtspProtocol.resolveControlUrl(baseUri, sdp.mediaControl)
            val transportRequest = "RTP/AVP;unicast;client_port=${rtpSocket.localPort}-${rtcpSocket.localPort}"
            val setup = connection.request("SETUP", mediaUri, transport = transportRequest)
            if (setup.statusCode == 461) {
                throw RtspProtocol.ProtocolException("Camera rejected UDP-unicast RTP transport")
            }
            RtspProtocol.requireSuccess(setup)
            connection.sessionId = RtspProtocol.requireSessionId(setup)
            connection.sessionTimeoutSeconds = parseSessionTimeout(setup.header("session"))
            val transport = RtspProtocol.parseTransport(
                setup,
                rtpSocket.localPort,
                rtcpSocket.localPort
            )
            connection.serverRtpPort = transport.serverRtpPort

            val playUri = RtspProtocol.resolveControlUrl(baseUri, sdp.sessionControl)
            val play = connection.request("PLAY", playUri, session = connection.sessionId)
            RtspProtocol.requireSuccess(play)

            val depacketizer = Rfc2435JpegDepacketizer(
                sdp.payloadType,
                MAX_FRAME_BYTES,
                MAX_PACKETS_PER_FRAME,
                FRAME_TIMEOUT_MS
            )
            depacketizer.setExpectedSsrc(transport.ssrc)
            receiveFrames(connection, address, depacketizer)
        } finally {
            try {
                connection.teardown()
            } catch (_: Exception) {
                // The connection may already have failed. Cleanup below is authoritative.
            }
            connection.close()
            activeConnections.remove(source.nodeId, connection)
        }
    }

    private fun receiveFrames(
        connection: Connection,
        expectedAddress: InetAddress,
        depacketizer: Rfc2435JpegDepacketizer
    ) {
        connection.rtp.soTimeout = RTP_RECEIVE_TIMEOUT_MS
        val storage = ByteArray(MAX_RTP_PACKET_BYTES)
        val packet = DatagramPacket(storage, storage.size)
        var lastPacketAt = System.currentTimeMillis()
        var invalidPackets = 0
        while (running.get() && !Thread.currentThread().isInterrupted) {
            try {
                packet.length = storage.size
                connection.rtp.receive(packet)
                val receivedAt = System.currentTimeMillis()
                if (packet.length == storage.size) {
                    throw RtspProtocol.ProtocolException("RTP datagram exceeds packet limit")
                }
                if (packet.address != expectedAddress || packet.port != connection.serverRtpPort) {
                    Log.w(TAG, "Dropping RTP datagram from an unnegotiated source")
                    continue
                }
                lastPacketAt = receivedAt
                try {
                    val frame = depacketizer.accept(packet.data, packet.length, receivedAt)
                    invalidPackets = 0
                    if (frame != null) emitFrame(connection.source, frame)
                } catch (e: Rfc2435JpegDepacketizer.PacketException) {
                    invalidPackets++
                    Log.w(TAG, "Rejected RTP/JPEG packet for ${connection.source.nodeId}: ${e.message}")
                    if (invalidPackets >= MAX_INVALID_PACKETS) {
                        throw RtspProtocol.ProtocolException("Too many invalid RTP/JPEG packets")
                    }
                }
            } catch (_: SocketTimeoutException) {
                val now = System.currentTimeMillis()
                depacketizer.discardExpired(now)
                val negotiatedIdleLimit = min(
                    RTP_IDLE_TIMEOUT_MS,
                    connection.sessionTimeoutSeconds * 1_000L
                )
                if (now - lastPacketAt >= negotiatedIdleLimit) {
                    throw RtspProtocol.ProtocolException("RTP stream timed out")
                }
            }
        }
    }

    private fun emitFrame(source: CameraSource, frame: Rfc2435JpegDepacketizer.Frame) {
        if (frame.jpeg.size < 4 || frame.jpeg[0] != 0xff.toByte() ||
            frame.jpeg[1] != 0xd8.toByte() ||
            frame.jpeg[frame.jpeg.size - 2] != 0xff.toByte() ||
            frame.jpeg[frame.jpeg.size - 1] != 0xd9.toByte()
        ) {
            return
        }
        val bitmap = BitmapFactory.decodeByteArray(frame.jpeg, 0, frame.jpeg.size) ?: return
        if (bitmap.width != frame.width || bitmap.height != frame.height) {
            bitmap.recycle()
            Log.w(TAG, "Decoded JPEG dimensions do not match RTP/JPEG metadata")
            return
        }
        listener.onFrameReceived(
            CameraIdentity(source.nodeId, source.roomId),
            bitmap,
            FrameMetadata(frame.ssrc, frame.rtpTimestamp, frame.receivedAtEpochMs)
        )
    }

    private fun openUdpPair(): Pair<DatagramSocket, DatagramSocket> {
        repeat(128) {
            val span = (CLIENT_PORT_LAST - CLIENT_PORT_FIRST) / 2
            val firstPort = CLIENT_PORT_FIRST + random.nextInt(span) * 2
            val rtp = DatagramSocket(null)
            val rtcp = DatagramSocket(null)
            try {
                rtp.reuseAddress = false
                rtcp.reuseAddress = false
                rtp.bind(InetSocketAddress(firstPort))
                rtcp.bind(InetSocketAddress(firstPort + 1))
                return Pair(rtp, rtcp)
            } catch (_: Exception) {
                rtp.close()
                rtcp.close()
            }
        }
        throw IllegalStateException("Could not allocate consecutive RTP/RTCP UDP ports")
    }

    private fun validateSource(source: CameraSource) {
        require(RtspProtocol.isValidIdentifier(source.nodeId)) { "invalid camera node ID" }
        require(RtspProtocol.isValidIdentifier(source.roomId)) { "invalid camera room ID" }
        require(source.host.isNotBlank() && source.host.length <= 253 &&
            source.host.none { it == '\r' || it == '\n' || it == '\u0000' }) {
            "invalid camera host"
        }
        require(source.port in 1..65535) { "invalid RTSP port" }
        require(source.streamPath.startsWith('/') && source.streamPath.length <= 255 &&
            source.streamPath.none { it == '\r' || it == '\n' || it == '\u0000' }) {
            "invalid RTSP stream path"
        }
        RtspProtocol.requireAuthKey(source.authKey)
    }

    private fun buildBaseUri(source: CameraSource): String = URI(
        "rtsp",
        null,
        source.host,
        source.port,
        source.streamPath,
        null,
        null
    ).toASCIIString()

    private fun parseSessionTimeout(sessionHeader: String?): Long {
        val timeout = sessionHeader
            ?.split(';')
            ?.drop(1)
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("timeout=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.toLongOrNull()
            ?: 30L
        if (timeout !in 5L..3_600L) {
            throw RtspProtocol.ProtocolException("Invalid RTSP session timeout")
        }
        return timeout
    }

    private inner class Connection(
        val source: CameraSource,
        val rtp: DatagramSocket,
        val rtcp: DatagramSocket
    ) : Closeable {
        val control = Socket()
        lateinit var input: BufferedInputStream
        lateinit var output: BufferedOutputStream
        var cSeq = 1
        var sessionId: String? = null
        var sessionTimeoutSeconds = 30L
        var serverRtpPort = -1

        fun request(
            method: String,
            uri: String,
            session: String? = null,
            transport: String? = null,
            acceptSdp: Boolean = false
        ): RtspProtocol.Response {
            val requestCSeq = cSeq++
            val request = RtspProtocol.buildRequest(
                method,
                uri,
                requestCSeq,
                source.authKey,
                session,
                transport,
                acceptSdp
            )
            output.write(request.toByteArray(StandardCharsets.ISO_8859_1))
            output.flush()
            return RtspProtocol.readResponse(input, requestCSeq)
        }

        fun teardown() {
            val session = sessionId ?: return
            if (!control.isConnected || control.isClosed || !::input.isInitialized ||
                !::output.isInitialized
            ) return
            val response = request("TEARDOWN", buildBaseUri(source), session = session)
            RtspProtocol.requireSuccess(response)
            sessionId = null
        }

        override fun close() {
            rtp.close()
            rtcp.close()
            try {
                control.close()
            } catch (_: Exception) {
            }
        }
    }
}
