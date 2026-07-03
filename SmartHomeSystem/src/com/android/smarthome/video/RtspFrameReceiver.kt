package com.android.smarthome.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

class RtspFrameReceiver(
    private val sourceIdentities: Map<String, CameraIdentity>,
    private val listener: FrameListener
) {

    data class CameraIdentity(val nodeId: String, val roomId: String)

    interface FrameListener {
        fun onFrameReceived(camera: CameraIdentity, bitmap: Bitmap)
    }

    companion object {
        private const val TAG = "RtspFrameReceiver"
        private const val RTP_PORT = 5004
        private const val BUFFER_SIZE = 65535
        private const val JITTER_TIMEOUT_MS = 500L
        private const val MAX_PACKETS_PER_FRAME = 512
        private const val MAX_FRAME_BYTES = 4 * 1024 * 1024
    }

    private var socket: DatagramSocket? = null
    private var isRunning = false
    private val executor = Executors.newSingleThreadExecutor()

    // Source + SSRC + timestamp prevents simultaneous cameras from sharing a frame buffer.
    private data class FrameKey(val sourceAddress: String, val ssrc: Long, val timestamp: Long)
    private val frameBuffers = HashMap<FrameKey, MutableMap<Int, ByteArray>>()
    private val frameFirstSequences = HashMap<FrameKey, Int>()
    private val frameMarkerSequences = HashMap<FrameKey, Int>()
    private val frameTimestamps = HashMap<FrameKey, Long>()
    private val ignoredSourcesLogged = HashSet<String>()

    fun start() {
        if (isRunning) return
        isRunning = true
        executor.submit {
            runReceiverLoop()
        }
    }

    fun stop() {
        isRunning = false
        socket?.close()
        executor.shutdown()
        Log.i(TAG, "RTSP Frame Receiver stopped")
    }

    private fun runReceiverLoop() {
        var retryCount = 0
        val maxRetries = 5

        while (isRunning) {
            try {
                Log.i(TAG, "Opening RTP UDP socket on port $RTP_PORT")
                socket = DatagramSocket(RTP_PORT)
                socket?.soTimeout = 5000 // 5 seconds timeout
                retryCount = 0 // Reset on successful bind

                val buffer = ByteArray(BUFFER_SIZE)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isRunning) {
                    try {
                        packet.length = buffer.size
                        socket?.receive(packet)
                        handleRtpPacket(
                            packet.data,
                            packet.length,
                            packet.address.hostAddress.orEmpty()
                        )
                    } catch (e: SocketTimeoutException) {
                        Log.d(TAG, "RTP socket timeout (no data from ESP32-CAM). Continuing...")
                        cleanJitterBuffer()
                    } catch (e: SocketException) {
                        if (!isRunning) break
                        throw e
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in RTP receiver loop: ${e.message}")
                socket?.close()
                if (isRunning) {
                    if (retryCount < maxRetries) {
                        retryCount++
                        val delay = 2000L * retryCount
                        Log.i(TAG, "Retrying to bind RTP socket in ${delay}ms (attempt $retryCount/$maxRetries)")
                        try {
                            Thread.sleep(delay)
                        } catch (ie: InterruptedException) {
                            break
                        }
                    } else {
                        Log.e(TAG, "Max retries reached. Stopping RTP Frame Receiver.")
                        isRunning = false
                    }
                }
            }
        }
    }

    private fun handleRtpPacket(data: ByteArray, length: Int, sourceAddress: String) {
        if (length < 12) {
            Log.w(TAG, "Received packet too short for RTP header: $length bytes")
            return
        }
        val camera = sourceIdentities[sourceAddress]
        if (camera == null) {
            if (ignoredSourcesLogged.add(sourceAddress)) {
                Log.w(TAG, "Ignoring RTP packets from unprovisioned camera source $sourceAddress")
            }
            return
        }

        val version = (data[0].toInt() ushr 6) and 0x03
        if (version != 2) {
            Log.w(TAG, "Ignoring packet with RTP version $version")
            return
        }

        // Byte 0: V(2b) P(1b) X(1b) CC(4b)
        // Byte 1: M(1b) PT(7b)
        val marker = (data[1].toInt() and 0x80) != 0
        val payloadType = data[1].toInt() and 0x7F

        // Bytes 2-3: Sequence number
        val seqNum = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)

        // Bytes 4-7: RTP timestamp
        val timestamp = (
            ((data[4].toLong() and 0xFF) shl 24) or
            ((data[5].toLong() and 0xFF) shl 16) or
            ((data[6].toLong() and 0xFF) shl 8) or
            (data[7].toLong() and 0xFF)
        )
        val ssrc = (
            ((data[8].toLong() and 0xFF) shl 24) or
            ((data[9].toLong() and 0xFF) shl 16) or
            ((data[10].toLong() and 0xFF) shl 8) or
            (data[11].toLong() and 0xFF)
        )
        val frameKey = FrameKey(sourceAddress, ssrc, timestamp)

        // Only process JPEG payload (Payload Type 26) or dynamic/mock type
        if (payloadType != 26) {
            // Log.v(TAG, "Non-JPEG payload type: $payloadType")
        }

        val hasPadding = (data[0].toInt() and 0x20) != 0
        val hasExtension = (data[0].toInt() and 0x10) != 0
        val csrcCount = data[0].toInt() and 0x0F
        var payloadOffset = 12 + csrcCount * 4
        if (payloadOffset > length) return
        if (hasExtension) {
            if (payloadOffset + 4 > length) return
            val extensionWords = ((data[payloadOffset + 2].toInt() and 0xFF) shl 8) or
                (data[payloadOffset + 3].toInt() and 0xFF)
            payloadOffset += 4 + extensionWords * 4
            if (payloadOffset > length) return
        }
        val paddingBytes = if (hasPadding) data[length - 1].toInt() and 0xFF else 0
        if (paddingBytes > length - payloadOffset) return
        val payloadSize = length - payloadOffset - paddingBytes
        if (payloadSize <= 0) return

        val payload = ByteArray(payloadSize)
        System.arraycopy(data, payloadOffset, payload, 0, payloadSize)

        synchronized(frameBuffers) {
            val packets = frameBuffers.getOrPut(frameKey) {
                frameFirstSequences[frameKey] = seqNum
                frameTimestamps[frameKey] = System.currentTimeMillis()
                HashMap()
            }
            if (packets.size >= MAX_PACKETS_PER_FRAME && seqNum !in packets) {
                Log.w(TAG, "Discarding oversized RTP frame $frameKey")
                removeFrame(frameKey)
                return@synchronized
            }
            packets[seqNum] = payload
            if (payloadSize >= 2 && payload[0] == 0xFF.toByte() && payload[1] == 0xD8.toByte()) {
                frameFirstSequences[frameKey] = seqNum
            }

            if (marker) {
                frameMarkerSequences[frameKey] = seqNum
            }
            tryReassembleFrame(frameKey, camera)
        }

        cleanJitterBuffer()
    }

    private fun tryReassembleFrame(frameKey: FrameKey, camera: CameraIdentity) {
        val packets = frameBuffers[frameKey] ?: return
        val firstSequence = frameFirstSequences[frameKey] ?: return
        val markerSequence = frameMarkerSequences[frameKey] ?: return
        val expectedPacketCount = ((markerSequence - firstSequence) and 0xFFFF) + 1
        if (expectedPacketCount > MAX_PACKETS_PER_FRAME) {
            Log.w(TAG, "Discarding RTP frame $frameKey with invalid sequence span")
            removeFrame(frameKey)
            return
        }
        if (packets.size < expectedPacketCount) return

        val bos = ByteArrayOutputStream()
        for (offset in 0 until expectedPacketCount) {
            val sequence = (firstSequence + offset) and 0xFFFF
            val payload = packets[sequence] ?: return
            if (bos.size() + payload.size > MAX_FRAME_BYTES) {
                Log.w(TAG, "Discarding oversized JPEG frame $frameKey")
                removeFrame(frameKey)
                return
            }
            bos.write(payload)
        }

        val frameData = bos.toByteArray()
        removeFrame(frameKey)

        try {
            val bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.size)
            if (bitmap != null) {
                listener.onFrameReceived(camera, bitmap)
            } else {
                Log.w(TAG, "Failed to decode JPEG frame of size ${frameData.size} bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding frame", e)
        }
    }

    private fun cleanJitterBuffer() {
        val now = System.currentTimeMillis()
        synchronized(frameBuffers) {
            val it = frameTimestamps.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (now - entry.value > JITTER_TIMEOUT_MS) {
                    Log.d(TAG, "Jitter buffer timeout: discarding frame ${entry.key}")
                    frameBuffers.remove(entry.key)
                    frameFirstSequences.remove(entry.key)
                    frameMarkerSequences.remove(entry.key)
                    it.remove()
                }
            }
        }
    }

    private fun removeFrame(frameKey: FrameKey) {
        frameBuffers.remove(frameKey)
        frameFirstSequences.remove(frameKey)
        frameMarkerSequences.remove(frameKey)
        frameTimestamps.remove(frameKey)
    }
}
