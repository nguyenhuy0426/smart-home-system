package com.android.smarthome.video;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Validates RTP/JPEG packets and reconstructs complete baseline JPEG frames. */
public final class Rfc2435JpegDepacketizer {
    private static final int RTP_HEADER_BYTES = 12;
    private static final int JPEG_HEADER_BYTES = 8;
    private static final int MAX_ACTIVE_FRAMES = 8;

    private static final byte[] LUM_DC_CODE_LENGTHS = bytes(
            0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0);
    private static final byte[] LUM_DC_SYMBOLS = bytes(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
    private static final byte[] LUM_AC_CODE_LENGTHS = bytes(
            0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d);
    private static final byte[] LUM_AC_SYMBOLS = bytes(
            0x01,0x02,0x03,0x00,0x04,0x11,0x05,0x12,0x21,0x31,0x41,0x06,0x13,0x51,0x61,0x07,
            0x22,0x71,0x14,0x32,0x81,0x91,0xa1,0x08,0x23,0x42,0xb1,0xc1,0x15,0x52,0xd1,0xf0,
            0x24,0x33,0x62,0x72,0x82,0x09,0x0a,0x16,0x17,0x18,0x19,0x1a,0x25,0x26,0x27,0x28,
            0x29,0x2a,0x34,0x35,0x36,0x37,0x38,0x39,0x3a,0x43,0x44,0x45,0x46,0x47,0x48,0x49,
            0x4a,0x53,0x54,0x55,0x56,0x57,0x58,0x59,0x5a,0x63,0x64,0x65,0x66,0x67,0x68,0x69,
            0x6a,0x73,0x74,0x75,0x76,0x77,0x78,0x79,0x7a,0x83,0x84,0x85,0x86,0x87,0x88,0x89,
            0x8a,0x92,0x93,0x94,0x95,0x96,0x97,0x98,0x99,0x9a,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,
            0xa8,0xa9,0xaa,0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8,0xb9,0xba,0xc2,0xc3,0xc4,0xc5,
            0xc6,0xc7,0xc8,0xc9,0xca,0xd2,0xd3,0xd4,0xd5,0xd6,0xd7,0xd8,0xd9,0xda,0xe1,0xe2,
            0xe3,0xe4,0xe5,0xe6,0xe7,0xe8,0xe9,0xea,0xf1,0xf2,0xf3,0xf4,0xf5,0xf6,0xf7,0xf8,
            0xf9,0xfa);
    private static final byte[] CHM_DC_CODE_LENGTHS = bytes(
            0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0);
    private static final byte[] CHM_DC_SYMBOLS = bytes(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
    private static final byte[] CHM_AC_CODE_LENGTHS = bytes(
            0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77);
    private static final byte[] CHM_AC_SYMBOLS = bytes(
            0x00,0x01,0x02,0x03,0x11,0x04,0x05,0x21,0x31,0x06,0x12,0x41,0x51,0x07,0x61,0x71,
            0x13,0x22,0x32,0x81,0x08,0x14,0x42,0x91,0xa1,0xb1,0xc1,0x09,0x23,0x33,0x52,0xf0,
            0x15,0x62,0x72,0xd1,0x0a,0x16,0x24,0x34,0xe1,0x25,0xf1,0x17,0x18,0x19,0x1a,0x26,
            0x27,0x28,0x29,0x2a,0x35,0x36,0x37,0x38,0x39,0x3a,0x43,0x44,0x45,0x46,0x47,0x48,
            0x49,0x4a,0x53,0x54,0x55,0x56,0x57,0x58,0x59,0x5a,0x63,0x64,0x65,0x66,0x67,0x68,
            0x69,0x6a,0x73,0x74,0x75,0x76,0x77,0x78,0x79,0x7a,0x82,0x83,0x84,0x85,0x86,0x87,
            0x88,0x89,0x8a,0x92,0x93,0x94,0x95,0x96,0x97,0x98,0x99,0x9a,0xa2,0xa3,0xa4,0xa5,
            0xa6,0xa7,0xa8,0xa9,0xaa,0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8,0xb9,0xba,0xc2,0xc3,
            0xc4,0xc5,0xc6,0xc7,0xc8,0xc9,0xca,0xd2,0xd3,0xd4,0xd5,0xd6,0xd7,0xd8,0xd9,0xda,
            0xe2,0xe3,0xe4,0xe5,0xe6,0xe7,0xe8,0xe9,0xea,0xf2,0xf3,0xf4,0xf5,0xf6,0xf7,0xf8,
            0xf9,0xfa);

    private final int payloadType;
    private final int maxFrameBytes;
    private final int maxPacketsPerFrame;
    private final long frameTimeoutMs;
    private final LinkedHashMap<FrameKey, Assembly> assemblies = new LinkedHashMap<>();
    private long expectedSsrc = -1L;

    public Rfc2435JpegDepacketizer(int payloadType, int maxFrameBytes,
            int maxPacketsPerFrame, long frameTimeoutMs) {
        if (payloadType < 0 || payloadType > 127 || maxFrameBytes < 1024 ||
                maxPacketsPerFrame < 1 || maxPacketsPerFrame > 4096 || frameTimeoutMs < 1) {
            throw new IllegalArgumentException("Invalid depacketizer limits");
        }
        this.payloadType = payloadType;
        this.maxFrameBytes = maxFrameBytes;
        this.maxPacketsPerFrame = maxPacketsPerFrame;
        this.frameTimeoutMs = frameTimeoutMs;
    }

    public synchronized void setExpectedSsrc(long ssrc) {
        if (ssrc < -1L || ssrc > 0xffffffffL) throw new IllegalArgumentException("Invalid SSRC");
        expectedSsrc = ssrc;
    }

    public synchronized Frame accept(byte[] packet, int length, long receivedAtEpochMs)
            throws PacketException {
        discardExpired(receivedAtEpochMs);
        ParsedPacket parsed = parsePacket(packet, length);
        if (expectedSsrc >= 0 && parsed.ssrc != expectedSsrc) {
            throw new PacketException("RTP SSRC does not match SETUP response");
        }
        FrameKey key = new FrameKey(parsed.ssrc, parsed.timestamp);
        Assembly assembly = assemblies.get(key);
        if (assembly == null) {
            if (assemblies.size() >= MAX_ACTIVE_FRAMES) {
                Iterator<FrameKey> iterator = assemblies.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
            assembly = new Assembly(parsed, receivedAtEpochMs);
            assemblies.put(key, assembly);
        }
        try {
            assembly.add(parsed, maxFrameBytes, maxPacketsPerFrame);
            Frame result = assembly.tryBuild(maxFrameBytes, maxPacketsPerFrame,
                    receivedAtEpochMs);
            if (result != null) assemblies.remove(key);
            return result;
        } catch (PacketException e) {
            assemblies.remove(key);
            throw e;
        }
    }

    public synchronized int discardExpired(long nowEpochMs) {
        int removed = 0;
        Iterator<Map.Entry<FrameKey, Assembly>> iterator = assemblies.entrySet().iterator();
        while (iterator.hasNext()) {
            Assembly assembly = iterator.next().getValue();
            if (nowEpochMs < assembly.firstReceivedAtMs ||
                    nowEpochMs - assembly.firstReceivedAtMs >= frameTimeoutMs) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public synchronized int pendingFrameCount() {
        return assemblies.size();
    }

    private ParsedPacket parsePacket(byte[] packet, int length) throws PacketException {
        if (packet == null || length < RTP_HEADER_BYTES + JPEG_HEADER_BYTES ||
                length > packet.length) throw new PacketException("RTP packet length is invalid");
        int first = packet[0] & 0xff;
        if ((first >>> 6) != 2) throw new PacketException("RTP version is not 2");
        boolean padding = (first & 0x20) != 0;
        boolean extension = (first & 0x10) != 0;
        int csrcCount = first & 0x0f;
        boolean marker = (packet[1] & 0x80) != 0;
        int actualPayloadType = packet[1] & 0x7f;
        if (actualPayloadType != payloadType) throw new PacketException("Unexpected RTP payload type");
        int sequence = u16(packet, 2);
        long timestamp = u32(packet, 4);
        long ssrc = u32(packet, 8);
        int offset = RTP_HEADER_BYTES + csrcCount * 4;
        if (offset > length) throw new PacketException("RTP CSRC list exceeds packet");
        if (extension) {
            if (offset + 4 > length) throw new PacketException("RTP extension is truncated");
            long extensionBytes = 4L + (long) u16(packet, offset + 2) * 4L;
            if (extensionBytes > length - offset) throw new PacketException("RTP extension exceeds packet");
            offset += (int) extensionBytes;
        }
        int paddingBytes = padding ? packet[length - 1] & 0xff : 0;
        if (paddingBytes == 0 && padding) throw new PacketException("RTP padding length is zero");
        int payloadEnd = length - paddingBytes;
        if (payloadEnd - offset < JPEG_HEADER_BYTES) {
            throw new PacketException("RTP/JPEG header is truncated");
        }

        int typeSpecific = packet[offset] & 0xff;
        int fragmentOffset = ((packet[offset + 1] & 0xff) << 16)
                | ((packet[offset + 2] & 0xff) << 8) | (packet[offset + 3] & 0xff);
        int type = packet[offset + 4] & 0xff;
        int q = packet[offset + 5] & 0xff;
        int width = (packet[offset + 6] & 0xff) * 8;
        int height = (packet[offset + 7] & 0xff) * 8;
        if (typeSpecific != 0 || (type != 0 && type != 1) || q != 255 ||
                width <= 0 || height <= 0) {
            throw new PacketException("Unsupported RTP/JPEG parameters");
        }
        int scanOffset = offset + JPEG_HEADER_BYTES;
        byte[] quantizationTables = null;
        if (fragmentOffset == 0) {
            if (payloadEnd - scanOffset < 4) throw new PacketException("Quantization header is missing");
            int mbz = packet[scanOffset] & 0xff;
            int precision = packet[scanOffset + 1] & 0xff;
            int tableLength = u16(packet, scanOffset + 2);
            scanOffset += 4;
            if (mbz != 0 || precision != 0 || tableLength != 128 ||
                    tableLength > payloadEnd - scanOffset) {
                throw new PacketException("Invalid RTP/JPEG quantization tables");
            }
            quantizationTables = Arrays.copyOfRange(packet, scanOffset,
                    scanOffset + tableLength);
            scanOffset += tableLength;
        }
        int scanLength = payloadEnd - scanOffset;
        if (scanLength <= 0 || (long) fragmentOffset + scanLength > maxFrameBytes) {
            throw new PacketException("RTP/JPEG fragment size is invalid");
        }
        return new ParsedPacket(sequence, timestamp, ssrc, marker, fragmentOffset, type,
                width, height, quantizationTables,
                Arrays.copyOfRange(packet, scanOffset, payloadEnd));
    }

    private static final class Assembly {
        final long ssrc;
        final long timestamp;
        final int type;
        final int width;
        final int height;
        final long firstReceivedAtMs;
        final TreeMap<Integer, Fragment> fragments = new TreeMap<>();
        final Map<Integer, Integer> sequenceOffsets = new HashMap<>();
        byte[] quantizationTables;
        Integer firstSequence;
        Integer markerSequence;
        Integer markerEndOffset;

        Assembly(ParsedPacket first, long firstReceivedAtMs) {
            this.ssrc = first.ssrc;
            this.timestamp = first.timestamp;
            this.type = first.type;
            this.width = first.width;
            this.height = first.height;
            this.firstReceivedAtMs = firstReceivedAtMs;
        }

        void add(ParsedPacket packet, int maxFrameBytes, int maxPackets) throws PacketException {
            if (packet.type != type || packet.width != width || packet.height != height) {
                throw new PacketException("RTP/JPEG parameters changed within a frame");
            }
            Integer oldOffset = sequenceOffsets.get(packet.sequence);
            if (oldOffset != null && oldOffset != packet.fragmentOffset) {
                throw new PacketException("RTP sequence number maps to multiple fragments");
            }
            Fragment previous = fragments.get(packet.fragmentOffset);
            if (previous != null) {
                if (previous.sequence == packet.sequence &&
                        Arrays.equals(previous.data, packet.scanData)) return;
                throw new PacketException("Conflicting duplicate RTP/JPEG fragment");
            }
            if (fragments.size() >= maxPackets ||
                    (long) packet.fragmentOffset + packet.scanData.length > maxFrameBytes) {
                throw new PacketException("RTP/JPEG frame exceeds limits");
            }
            Map.Entry<Integer, Fragment> lower = fragments.floorEntry(packet.fragmentOffset);
            if (lower != null && lower.getKey() + lower.getValue().data.length >
                    packet.fragmentOffset) {
                throw new PacketException("Overlapping RTP/JPEG fragments");
            }
            Map.Entry<Integer, Fragment> higher = fragments.ceilingEntry(packet.fragmentOffset);
            if (higher != null && packet.fragmentOffset + packet.scanData.length >
                    higher.getKey()) {
                throw new PacketException("Overlapping RTP/JPEG fragments");
            }
            fragments.put(packet.fragmentOffset, new Fragment(packet.sequence, packet.scanData));
            sequenceOffsets.put(packet.sequence, packet.fragmentOffset);
            if (packet.fragmentOffset == 0) {
                if (packet.quantizationTables == null) {
                    throw new PacketException("First JPEG fragment has no quantization tables");
                }
                quantizationTables = packet.quantizationTables;
                firstSequence = packet.sequence;
            }
            if (packet.marker) {
                int end = packet.fragmentOffset + packet.scanData.length;
                if (markerEndOffset != null &&
                        (markerEndOffset != end || markerSequence != packet.sequence)) {
                    throw new PacketException("Conflicting RTP marker packets");
                }
                markerEndOffset = end;
                markerSequence = packet.sequence;
            }
        }

        Frame tryBuild(int maxFrameBytes, int maxPackets, long receivedAtEpochMs)
                throws PacketException {
            if (quantizationTables == null || firstSequence == null || markerSequence == null ||
                    markerEndOffset == null) return null;
            int expectedOffset = 0;
            ByteArrayOutputStream scan = new ByteArrayOutputStream(markerEndOffset);
            for (Map.Entry<Integer, Fragment> entry : fragments.entrySet()) {
                if (entry.getKey() != expectedOffset) return null;
                if (entry.getKey() >= markerEndOffset) {
                    throw new PacketException("JPEG data follows marker fragment");
                }
                byte[] data = entry.getValue().data;
                int allowed = markerEndOffset - expectedOffset;
                if (data.length > allowed) throw new PacketException("JPEG fragments overlap marker end");
                scan.write(data, 0, data.length);
                expectedOffset += data.length;
            }
            if (expectedOffset != markerEndOffset) return null;
            int expectedPackets = ((markerSequence - firstSequence) & 0xffff) + 1;
            if (expectedPackets > maxPackets || expectedPackets != fragments.size()) {
                throw new PacketException("RTP packet loss or sequence discontinuity detected");
            }
            for (int i = 0; i < expectedPackets; i++) {
                if (!sequenceOffsets.containsKey((firstSequence + i) & 0xffff)) {
                    throw new PacketException("RTP packet loss detected");
                }
            }
            byte[] jpeg = buildJpeg(type, width, height, quantizationTables,
                    scan.toByteArray(), maxFrameBytes);
            return new Frame(jpeg, ssrc, timestamp, receivedAtEpochMs, width, height);
        }
    }

    private static byte[] buildJpeg(int type, int width, int height, byte[] tables,
            byte[] scan, int maxFrameBytes) throws PacketException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(scan.length + 1024);
            marker(output, 0xd8);
            quantizationHeader(output, tables, 0, 0);
            quantizationHeader(output, tables, 64, 1);
            marker(output, 0xc0);
            u16(output, 17);
            output.write(8);
            u16(output, height);
            u16(output, width);
            output.write(3);
            output.write(0); output.write(type == 0 ? 0x21 : 0x22); output.write(0);
            output.write(1); output.write(0x11); output.write(1);
            output.write(2); output.write(0x11); output.write(1);
            huffmanHeader(output, LUM_DC_CODE_LENGTHS, LUM_DC_SYMBOLS, 0, 0);
            huffmanHeader(output, LUM_AC_CODE_LENGTHS, LUM_AC_SYMBOLS, 0, 1);
            huffmanHeader(output, CHM_DC_CODE_LENGTHS, CHM_DC_SYMBOLS, 1, 0);
            huffmanHeader(output, CHM_AC_CODE_LENGTHS, CHM_AC_SYMBOLS, 1, 1);
            marker(output, 0xda);
            u16(output, 12);
            output.write(3);
            output.write(0); output.write(0);
            output.write(1); output.write(0x11);
            output.write(2); output.write(0x11);
            output.write(0); output.write(63); output.write(0);
            output.write(scan);
            if (scan.length < 2 || scan[scan.length - 2] != (byte) 0xff ||
                    scan[scan.length - 1] != (byte) 0xd9) marker(output, 0xd9);
            byte[] jpeg = output.toByteArray();
            if (jpeg.length > maxFrameBytes || jpeg.length < 4 ||
                    jpeg[0] != (byte) 0xff || jpeg[1] != (byte) 0xd8 ||
                    jpeg[jpeg.length - 2] != (byte) 0xff ||
                    jpeg[jpeg.length - 1] != (byte) 0xd9) {
                throw new PacketException("Reconstructed JPEG failed validation");
            }
            return jpeg;
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void quantizationHeader(ByteArrayOutputStream output, byte[] tables,
            int tableOffset, int tableNumber) {
        marker(output, 0xdb);
        u16(output, 67);
        output.write(tableNumber);
        output.write(tables, tableOffset, 64);
    }

    private static void huffmanHeader(ByteArrayOutputStream output, byte[] lengths,
            byte[] symbols, int tableNumber, int tableClass) {
        marker(output, 0xc4);
        u16(output, 3 + lengths.length + symbols.length);
        output.write((tableClass << 4) | tableNumber);
        output.write(lengths, 0, lengths.length);
        output.write(symbols, 0, symbols.length);
    }

    private static void marker(ByteArrayOutputStream output, int marker) {
        output.write(0xff);
        output.write(marker);
    }

    private static void u16(ByteArrayOutputStream output, int value) {
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
    }

    private static int u16(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    private static long u32(byte[] data, int offset) {
        return ((long) (data[offset] & 0xff) << 24)
                | ((long) (data[offset + 1] & 0xff) << 16)
                | ((long) (data[offset + 2] & 0xff) << 8)
                | (long) (data[offset + 3] & 0xff);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }

    private static final class FrameKey {
        final long ssrc;
        final long timestamp;

        FrameKey(long ssrc, long timestamp) {
            this.ssrc = ssrc;
            this.timestamp = timestamp;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof FrameKey)) return false;
            FrameKey key = (FrameKey) other;
            return ssrc == key.ssrc && timestamp == key.timestamp;
        }

        @Override public int hashCode() {
            return (int) (ssrc ^ (ssrc >>> 32) ^ timestamp ^ (timestamp >>> 32));
        }
    }

    private static final class ParsedPacket {
        final int sequence;
        final long timestamp;
        final long ssrc;
        final boolean marker;
        final int fragmentOffset;
        final int type;
        final int width;
        final int height;
        final byte[] quantizationTables;
        final byte[] scanData;

        ParsedPacket(int sequence, long timestamp, long ssrc, boolean marker,
                int fragmentOffset, int type, int width, int height,
                byte[] quantizationTables, byte[] scanData) {
            this.sequence = sequence;
            this.timestamp = timestamp;
            this.ssrc = ssrc;
            this.marker = marker;
            this.fragmentOffset = fragmentOffset;
            this.type = type;
            this.width = width;
            this.height = height;
            this.quantizationTables = quantizationTables;
            this.scanData = scanData;
        }
    }

    private static final class Fragment {
        final int sequence;
        final byte[] data;

        Fragment(int sequence, byte[] data) {
            this.sequence = sequence;
            this.data = data;
        }
    }

    public static final class Frame {
        public final byte[] jpeg;
        public final long ssrc;
        public final long rtpTimestamp;
        public final long receivedAtEpochMs;
        public final int width;
        public final int height;

        Frame(byte[] jpeg, long ssrc, long rtpTimestamp, long receivedAtEpochMs,
                int width, int height) {
            this.jpeg = jpeg;
            this.ssrc = ssrc;
            this.rtpTimestamp = rtpTimestamp;
            this.receivedAtEpochMs = receivedAtEpochMs;
            this.width = width;
            this.height = height;
        }
    }

    public static final class PacketException extends Exception {
        public PacketException(String message) {
            super(message);
        }
    }
}
