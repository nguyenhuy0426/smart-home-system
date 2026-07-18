package com.android.smarthome.video;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Strict parser for the single JPEG video media section emitted by the camera node. */
public final class SdpParser {
    private static final int MAX_SDP_BYTES = 16 * 1024;

    private SdpParser() {}

    public static void requireIdentity(SessionDescription description, String expectedNodeId,
            String expectedRoomId) throws RtspProtocol.ProtocolException {
        if (description == null || !description.nodeId.equals(expectedNodeId) ||
                !description.roomId.equals(expectedRoomId)) {
            throw new RtspProtocol.ProtocolException(
                    "SDP camera identity does not match deployment config");
        }
    }

    public static SessionDescription parse(byte[] bytes) throws RtspProtocol.ProtocolException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_SDP_BYTES) {
            throw new RtspProtocol.ProtocolException("SDP size is invalid");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('\0') >= 0 || !text.startsWith("v=0\r\n")) {
            throw new RtspProtocol.ProtocolException("SDP preamble is invalid");
        }

        String nodeId = null;
        String roomId = null;
        String sessionControl = null;
        String mediaControl = null;
        int payloadType = -1;
        int clockRate = -1;
        boolean inVideo = false;
        boolean videoSeen = false;
        boolean jpegMappingSeen = false;

        for (String line : text.split("\\r\\n")) {
            if (line.isEmpty()) continue;
            if (line.length() > 1024 || line.length() < 2 || line.charAt(1) != '=') {
                throw new RtspProtocol.ProtocolException("Malformed SDP line");
            }
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if ((ch < 0x20 && ch != '\t') || ch == 0x7f) {
                    throw new RtspProtocol.ProtocolException("SDP contains control characters");
                }
            }
            if (line.startsWith("m=")) {
                String[] parts = line.substring(2).trim().split("[ \\t]+");
                inVideo = parts.length >= 4 && "video".equals(parts[0]);
                if (inVideo) {
                    if (videoSeen) throw new RtspProtocol.ProtocolException("Multiple video media sections");
                    videoSeen = true;
                    if (!"RTP/AVP".equalsIgnoreCase(parts[2]) || !parts[3].matches("[0-9]{1,3}")) {
                        throw new RtspProtocol.ProtocolException("Unsupported SDP video transport");
                    }
                    payloadType = Integer.parseInt(parts[3]);
                    if (payloadType < 0 || payloadType > 127) {
                        throw new RtspProtocol.ProtocolException("Invalid SDP payload type");
                    }
                }
                continue;
            }
            if (line.startsWith("a=x-node-id:")) {
                nodeId = uniqueIdentifier(nodeId, line.substring(12), "node ID");
            } else if (line.startsWith("a=x-room-id:")) {
                roomId = uniqueIdentifier(roomId, line.substring(12), "room ID");
            } else if (line.startsWith("a=control:")) {
                String value = line.substring(10).trim();
                if (value.isEmpty()) throw new RtspProtocol.ProtocolException("Empty SDP control attribute");
                if (inVideo) {
                    if (mediaControl != null) throw new RtspProtocol.ProtocolException("Duplicate media control");
                    mediaControl = value;
                } else {
                    if (sessionControl != null) throw new RtspProtocol.ProtocolException("Duplicate session control");
                    sessionControl = value;
                }
            } else if (inVideo && line.regionMatches(true, 0, "a=rtpmap:", 0, 9)) {
                String value = line.substring(9).trim();
                String[] fields = value.split("[ \\t]+", 2);
                if (fields.length != 2 || !fields[0].matches("[0-9]{1,3}")) {
                    throw new RtspProtocol.ProtocolException("Malformed SDP rtpmap");
                }
                int mappedPayload = Integer.parseInt(fields[0]);
                String[] encoding = fields[1].split("/");
                if (mappedPayload == payloadType && encoding.length >= 2 &&
                        "JPEG".equals(encoding[0].toUpperCase(Locale.US)) &&
                        "90000".equals(encoding[1])) {
                    jpegMappingSeen = true;
                    clockRate = 90000;
                }
            }
        }
        if (!videoSeen || payloadType != 26 || !jpegMappingSeen || clockRate != 90000 ||
                nodeId == null || roomId == null || mediaControl == null) {
            throw new RtspProtocol.ProtocolException("SDP is not the expected RTP/JPEG stream");
        }
        return new SessionDescription(payloadType, clockRate, mediaControl, sessionControl,
                nodeId, roomId);
    }

    private static String uniqueIdentifier(String previous, String candidate, String label)
            throws RtspProtocol.ProtocolException {
        String value = candidate.trim();
        if (previous != null || !RtspProtocol.isValidIdentifier(value)) {
            throw new RtspProtocol.ProtocolException("Invalid or duplicate SDP " + label);
        }
        return value;
    }

    public static final class SessionDescription {
        public final int payloadType;
        public final int clockRate;
        public final String mediaControl;
        public final String sessionControl;
        public final String nodeId;
        public final String roomId;

        SessionDescription(int payloadType, int clockRate, String mediaControl,
                String sessionControl, String nodeId, String roomId) {
            this.payloadType = payloadType;
            this.clockRate = clockRate;
            this.mediaControl = mediaControl;
            this.sessionControl = sessionControl;
            this.nodeId = nodeId;
            this.roomId = roomId;
        }
    }
}
