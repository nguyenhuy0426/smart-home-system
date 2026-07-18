package com.android.smarthome.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public final class RtspProtocolTest {
    private static final String KEY =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void requestIncludesAuthenticationAndNegotiationHeaders() {
        String request = RtspProtocol.buildRequest(
                "SETUP",
                "rtsp://192.0.2.10:8554/camera/trackID=0",
                3,
                KEY,
                null,
                "RTP/AVP;unicast;client_port=40000-40001",
                false);
        assertTrue(request.startsWith("SETUP rtsp://192.0.2.10:8554/camera/trackID=0 RTSP/1.0\r\n"));
        assertTrue(request.contains("CSeq: 3\r\n"));
        assertTrue(request.contains("Authorization: Bearer " + KEY + "\r\n"));
        assertTrue(request.contains("Transport: RTP/AVP;unicast;client_port=40000-40001\r\n"));
        assertTrue(request.endsWith("\r\n\r\n"));
        TestAssertions.expectThrows(IllegalArgumentException.class, () -> RtspProtocol.buildRequest(
                "PLAY", "rtsp://192.0.2.10/camera", 1, "hardcoded", null, null, false));
    }

    @Test
    public void responseParserValidatesCseqLengthAndStatus() throws Exception {
        byte[] body = "v=0\r\n".getBytes(StandardCharsets.US_ASCII);
        String header = "RTSP/1.0 200 OK\r\nCSeq: 2\r\nContent-Type: application/sdp\r\n"
                + "Content-Length: " + body.length + "\r\n\r\n";
        byte[] response = concat(header.getBytes(StandardCharsets.US_ASCII), body);
        RtspProtocol.Response parsed = RtspProtocol.parseResponse(response, 2);
        assertEquals(200, parsed.statusCode);
        assertEquals("application/sdp", parsed.header("Content-Type"));
        assertEquals("v=0\r\n", new String(parsed.body, StandardCharsets.US_ASCII));
        RtspProtocol.requireSuccess(parsed);

        TestAssertions.expectThrows(RtspProtocol.ProtocolException.class,
                () -> RtspProtocol.parseResponse(response, 3));
        byte[] truncated = concat(
                "RTSP/1.0 200 OK\r\nCSeq: 2\r\nContent-Length: 9\r\n\r\n"
                        .getBytes(StandardCharsets.US_ASCII), body);
        TestAssertions.expectThrows(RtspProtocol.ProtocolException.class,
                () -> RtspProtocol.parseResponse(truncated, 2));

        byte[] unsupported = "RTSP/1.0 461 Unsupported Transport\r\nCSeq: 4\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII);
        RtspProtocol.Response rejected = RtspProtocol.parseResponse(unsupported, 4);
        TestAssertions.expectThrows(RtspProtocol.ProtocolException.class,
                () -> RtspProtocol.requireSuccess(rejected));
    }

    @Test
    public void transportParserRejectsTcpInterleaving() throws Exception {
        byte[] valid = ("RTSP/1.0 200 OK\r\nCSeq: 3\r\n"
                + "Transport: RTP/AVP;unicast;client_port=40000-40001;"
                + "server_port=6970-6971;ssrc=11223344\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        RtspProtocol.Transport transport = RtspProtocol.parseTransport(
                RtspProtocol.parseResponse(valid, 3), 40000, 40001);
        assertEquals(6970, transport.serverRtpPort);
        assertEquals(0x11223344L, transport.ssrc);

        byte[] tcp = ("RTSP/1.0 200 OK\r\nCSeq: 3\r\n"
                + "Transport: RTP/AVP/TCP;unicast;interleaved=0-1;"
                + "client_port=40000-40001;server_port=6970-6971\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        RtspProtocol.Response tcpResponse = RtspProtocol.parseResponse(tcp, 3);
        TestAssertions.expectThrows(RtspProtocol.ProtocolException.class,
                () -> RtspProtocol.parseTransport(tcpResponse, 40000, 40001));
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
