package com.android.smarthome.video;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public final class SdpParserTest {
    private static final String VALID_SDP =
            "v=0\r\n"
            + "o=- 42 1 IN IP4 192.0.2.10\r\n"
            + "s=SmartHome Camera camera-01\r\n"
            + "c=IN IP4 192.0.2.10\r\n"
            + "t=0 0\r\n"
            + "a=control:*\r\n"
            + "a=x-node-id:camera-01\r\n"
            + "a=x-room-id:living-room\r\n"
            + "m=video 0 RTP/AVP 26\r\n"
            + "a=rtpmap:26 JPEG/90000\r\n"
            + "a=control:trackID=0\r\n";

    @Test
    public void parsesExpectedJpegStreamAndIdentityMapping() throws Exception {
        SdpParser.SessionDescription parsed = SdpParser.parse(
                VALID_SDP.getBytes(StandardCharsets.UTF_8));
        assertEquals(26, parsed.payloadType);
        assertEquals(90000, parsed.clockRate);
        assertEquals("trackID=0", parsed.mediaControl);
        assertEquals("camera-01", parsed.nodeId);
        assertEquals("living-room", parsed.roomId);
        SdpParser.requireIdentity(parsed, "camera-01", "living-room");
        TestAssertions.expectThrows(RtspProtocol.ProtocolException.class,
                () -> SdpParser.requireIdentity(parsed, "camera-02", "living-room"));
        assertEquals("rtsp://192.0.2.10:8554/camera/trackID=0",
                RtspProtocol.resolveControlUrl(
                        "rtsp://192.0.2.10:8554/camera", parsed.mediaControl));
    }

    @Test
    public void rejectsWrongPayloadClockOrMissingIdentity() {
        String wrongPayload = VALID_SDP.replace("RTP/AVP 26", "RTP/AVP 96")
                .replace("rtpmap:26", "rtpmap:96");
        TestAssertions.expectThrows(RtspProtocol.ProtocolException.class,
                () -> SdpParser.parse(wrongPayload.getBytes(StandardCharsets.UTF_8)));
        String wrongClock = VALID_SDP.replace("JPEG/90000", "JPEG/8000");
        TestAssertions.expectThrows(RtspProtocol.ProtocolException.class,
                () -> SdpParser.parse(wrongClock.getBytes(StandardCharsets.UTF_8)));
        String noRoom = VALID_SDP.replace("a=x-room-id:living-room\r\n", "");
        TestAssertions.expectThrows(RtspProtocol.ProtocolException.class,
                () -> SdpParser.parse(noRoom.getBytes(StandardCharsets.UTF_8)));
    }
}
