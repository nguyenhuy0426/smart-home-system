package com.android.smarthome.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public final class Rfc2435JpegDepacketizerTest {
    private static final long SSRC = 0x11223344L;
    private static final long TIMESTAMP = 90000L;

    @Test
    public void reconstructsReorderedRfc2435FrameAndIgnoresExactDuplicate() throws Exception {
        Rfc2435JpegDepacketizer depacketizer = depacketizer(4096);
        byte[] scan = scan(900);
        byte[] firstData = Arrays.copyOfRange(scan, 0, 400);
        byte[] secondData = Arrays.copyOfRange(scan, 400, scan.length);
        byte[] first = packet(65535, TIMESTAMP, SSRC, 0, false, firstData, true);
        byte[] second = packet(0, TIMESTAMP, SSRC, 400, true, secondData, false);

        assertNull(depacketizer.accept(second, second.length, 1000));
        assertNull(depacketizer.accept(second, second.length, 1001));
        Rfc2435JpegDepacketizer.Frame frame = depacketizer.accept(first, first.length, 1002);
        assertNotNull(frame);
        assertEquals(SSRC, frame.ssrc);
        assertEquals(TIMESTAMP, frame.rtpTimestamp);
        assertEquals(320, frame.width);
        assertEquals(240, frame.height);
        assertEquals((byte) 0xff, frame.jpeg[0]);
        assertEquals((byte) 0xd8, frame.jpeg[1]);
        assertEquals((byte) 0xff, frame.jpeg[frame.jpeg.length - 2]);
        assertEquals((byte) 0xd9, frame.jpeg[frame.jpeg.length - 1]);
        assertTrue(indexOf(frame.jpeg, scan) > 0);
    }

    @Test
    public void packetLossNeverProducesFrameAndTimesOut() throws Exception {
        Rfc2435JpegDepacketizer depacketizer = depacketizer(4096);
        byte[] first = packet(10, TIMESTAMP, SSRC, 0, false, scan(200), true);
        byte[] markerAfterGap = packet(12, TIMESTAMP, SSRC, 400, true, scan(100), false);
        assertNull(depacketizer.accept(first, first.length, 1000));
        assertNull(depacketizer.accept(markerAfterGap, markerAfterGap.length, 1001));
        assertEquals(1, depacketizer.pendingFrameCount());
        assertEquals(1, depacketizer.discardExpired(1600));
        assertEquals(0, depacketizer.pendingFrameCount());
    }

    @Test
    public void rejectsMalformedTablesWrongSsrcOverlapAndOversize() throws Exception {
        Rfc2435JpegDepacketizer depacketizer = depacketizer(2048);
        depacketizer.setExpectedSsrc(SSRC);
        byte[] malformed = packet(1, TIMESTAMP, SSRC, 0, true, scan(20), true);
        malformed[23] = 127; // Quantization table length must be exactly 128.
        TestAssertions.expectThrows(Rfc2435JpegDepacketizer.PacketException.class,
                () -> depacketizer.accept(malformed, malformed.length, 1000));

        byte[] wrongSsrc = packet(1, TIMESTAMP, 7, 0, true, scan(20), true);
        TestAssertions.expectThrows(Rfc2435JpegDepacketizer.PacketException.class,
                () -> depacketizer.accept(wrongSsrc, wrongSsrc.length, 1000));

        Rfc2435JpegDepacketizer overlap = depacketizer(2048);
        byte[] first = packet(10, TIMESTAMP, SSRC, 0, false, scan(200), true);
        byte[] overlapping = packet(11, TIMESTAMP, SSRC, 100, true, scan(200), false);
        assertNull(overlap.accept(first, first.length, 1000));
        TestAssertions.expectThrows(Rfc2435JpegDepacketizer.PacketException.class,
                () -> overlap.accept(overlapping, overlapping.length, 1001));

        Rfc2435JpegDepacketizer small = depacketizer(1024);
        byte[] oversized = packet(1, TIMESTAMP, SSRC, 1000, true, scan(100), false);
        TestAssertions.expectThrows(Rfc2435JpegDepacketizer.PacketException.class,
                () -> small.accept(oversized, oversized.length, 1000));
    }

    @Test
    public void validatesRtpVersionPayloadAndMarkerState() {
        Rfc2435JpegDepacketizer depacketizer = depacketizer(4096);
        byte[] valid = packet(1, TIMESTAMP, SSRC, 0, true, scan(20), true);
        byte[] badVersion = valid.clone();
        badVersion[0] = 0x40;
        TestAssertions.expectThrows(Rfc2435JpegDepacketizer.PacketException.class,
                () -> depacketizer.accept(badVersion, badVersion.length, 1000));
        byte[] badPayload = valid.clone();
        badPayload[1] = (byte) 0x80;
        TestAssertions.expectThrows(Rfc2435JpegDepacketizer.PacketException.class,
                () -> depacketizer.accept(badPayload, badPayload.length, 1000));
    }

    private static Rfc2435JpegDepacketizer depacketizer(int maxBytes) {
        return new Rfc2435JpegDepacketizer(26, maxBytes, 32, 500);
    }

    private static byte[] packet(int sequence, long timestamp, long ssrc, int fragmentOffset,
            boolean marker, byte[] scan, boolean first) {
        int extra = first ? 132 : 0;
        byte[] packet = new byte[12 + 8 + extra + scan.length];
        packet[0] = (byte) 0x80;
        packet[1] = (byte) ((marker ? 0x80 : 0) | 26);
        put16(packet, 2, sequence);
        put32(packet, 4, timestamp);
        put32(packet, 8, ssrc);
        packet[12] = 0;
        packet[13] = (byte) (fragmentOffset >>> 16);
        packet[14] = (byte) (fragmentOffset >>> 8);
        packet[15] = (byte) fragmentOffset;
        packet[16] = 1;
        packet[17] = (byte) 255;
        packet[18] = 40;
        packet[19] = 30;
        int offset = 20;
        if (first) {
            packet[offset++] = 0;
            packet[offset++] = 0;
            packet[offset++] = 0;
            packet[offset++] = (byte) 128;
            for (int i = 0; i < 128; i++) packet[offset++] = (byte) (i % 64 + 1);
        }
        System.arraycopy(scan, 0, packet, offset, scan.length);
        return packet;
    }

    private static byte[] scan(int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) result[i] = (byte) ((i % 250) + 1);
        return result;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    private static void put32(byte[] data, int offset, long value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }
}
