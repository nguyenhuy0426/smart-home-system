package com.android.smarthome.video;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded RTSP/1.0 request and response codec for the ESP32-CAM transport. */
public final class RtspProtocol {
    public static final int MAX_HEADER_BYTES = 16 * 1024;
    public static final int MAX_BODY_BYTES = 16 * 1024;
    private static final Pattern STATUS_LINE =
            Pattern.compile("RTSP/1\\.0 ([1-5][0-9]{2})(?:[ \\t]+([^\\r\\n]*))?");
    private static final Pattern PORT_PAIR = Pattern.compile("([0-9]{1,5})-([0-9]{1,5})");
    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

    private RtspProtocol() {}

    public static String buildRequest(String method, String uri, int cSeq, String authKey,
            String sessionId, String transport, boolean acceptSdp) {
        requireToken("method", method);
        requireUri(uri);
        if (cSeq <= 0) throw new IllegalArgumentException("CSeq must be positive");
        requireAuthKey(authKey);
        if (sessionId != null) requireHeaderValue("session", sessionId);
        if (transport != null) requireHeaderValue("transport", transport);

        StringBuilder request = new StringBuilder(512)
                .append(method).append(' ').append(uri).append(" RTSP/1.0\r\n")
                .append("CSeq: ").append(cSeq).append("\r\n")
                .append("User-Agent: SmartHomeSystem/1\r\n")
                .append("Authorization: Bearer ").append(authKey).append("\r\n");
        if (sessionId != null) request.append("Session: ").append(sessionId).append("\r\n");
        if (transport != null) request.append("Transport: ").append(transport).append("\r\n");
        if (acceptSdp) request.append("Accept: application/sdp\r\n");
        return request.append("Content-Length: 0\r\n\r\n").toString();
    }

    public static Response readResponse(InputStream input, int expectedCSeq) throws IOException {
        if (input == null) throw new IllegalArgumentException("input is required");
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int matched = 0;
        while (header.size() < MAX_HEADER_BYTES) {
            int value = input.read();
            if (value < 0) throw new EOFException("RTSP response ended before headers");
            header.write(value);
            if ((matched == 0 || matched == 2) && value == '\r') {
                matched++;
            } else if ((matched == 1 || matched == 3) && value == '\n') {
                matched++;
                if (matched == 4) break;
            } else {
                matched = value == '\r' ? 1 : 0;
            }
        }
        if (matched != 4) throw new ProtocolException("RTSP headers exceed limit");
        Response parsed = parseResponseHeader(header.toByteArray());
        if (parsed.cSeq != expectedCSeq) {
            throw new ProtocolException("RTSP CSeq mismatch");
        }
        int contentLength = parseContentLength(parsed.headers.get("content-length"));
        byte[] body = readExactly(input, contentLength);
        return new Response(parsed.statusCode, parsed.reason, parsed.cSeq, parsed.headers, body);
    }

    public static Response parseResponse(byte[] data, int expectedCSeq) throws IOException {
        if (data == null || data.length == 0 ||
                data.length > MAX_HEADER_BYTES + MAX_BODY_BYTES) {
            throw new ProtocolException("RTSP response size is invalid");
        }
        int headerEnd = findHeaderEnd(data);
        if (headerEnd < 0 || headerEnd > MAX_HEADER_BYTES) {
            throw new ProtocolException("RTSP response has no bounded header terminator");
        }
        byte[] header = new byte[headerEnd];
        System.arraycopy(data, 0, header, 0, header.length);
        Response parsed = parseResponseHeader(header);
        if (parsed.cSeq != expectedCSeq) throw new ProtocolException("RTSP CSeq mismatch");
        int contentLength = parseContentLength(parsed.headers.get("content-length"));
        if (data.length - headerEnd != contentLength) {
            throw new ProtocolException("RTSP Content-Length mismatch");
        }
        byte[] body = new byte[contentLength];
        System.arraycopy(data, headerEnd, body, 0, contentLength);
        return new Response(parsed.statusCode, parsed.reason, parsed.cSeq, parsed.headers, body);
    }

    public static void requireSuccess(Response response) throws ProtocolException {
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new ProtocolException("RTSP returned " + response.statusCode + " "
                    + response.reason);
        }
    }

    public static String requireSessionId(Response response) throws ProtocolException {
        String value = response.header("session");
        if (value == null) throw new ProtocolException("RTSP Session header is missing");
        String token = value.split(";", 2)[0].trim();
        if (!IDENTIFIER.matcher(token).matches()) {
            throw new ProtocolException("RTSP Session header is invalid");
        }
        return token;
    }

    public static Transport parseTransport(Response response, int requestedRtpPort,
            int requestedRtcpPort) throws ProtocolException {
        String value = response.header("transport");
        if (value == null) throw new ProtocolException("RTSP Transport header is missing");
        String lower = value.toLowerCase(Locale.US);
        if (!lower.contains("rtp/avp") || !lower.contains("unicast") ||
                lower.contains("interleaved") || lower.contains("rtp/avp/tcp")) {
            throw new ProtocolException("RTSP transport is not UDP unicast");
        }
        int[] client = parseNamedPortPair(value, "client_port");
        if (client[0] != requestedRtpPort || client[1] != requestedRtcpPort) {
            throw new ProtocolException("RTSP server changed the client port pair");
        }
        int[] server = parseNamedPortPair(value, "server_port");
        long ssrc = -1L;
        for (String part : value.split(";")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "ssrc=", 0, 5)) {
                String hex = trimmed.substring(5);
                if (!hex.matches("[0-9A-Fa-f]{1,8}")) {
                    throw new ProtocolException("RTSP SSRC is malformed");
                }
                ssrc = Long.parseLong(hex, 16) & 0xffffffffL;
            }
        }
        if (ssrc < 0) throw new ProtocolException("RTSP Transport SSRC is missing");
        return new Transport(client[0], client[1], server[0], server[1], ssrc);
    }

    public static String resolveControlUrl(String baseUri, String control) throws ProtocolException {
        requireUri(baseUri);
        if (control == null || control.isEmpty() || "*".equals(control)) return baseUri;
        requireHeaderValue("control", control);
        try {
            URI controlUri = new URI(control);
            if (controlUri.isAbsolute()) return controlUri.toASCIIString();
            URI base = new URI(baseUri);
            if (control.startsWith("/")) {
                return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(),
                        control, null, null).toASCIIString();
            }
            return baseUri + (baseUri.endsWith("/") ? "" : "/") + control;
        } catch (URISyntaxException e) {
            throw new ProtocolException("Invalid RTSP control URL", e);
        }
    }

    public static boolean isValidIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    public static void requireAuthKey(String authKey) {
        if (authKey == null || !authKey.matches("[0-9A-Fa-f]{64}")) {
            throw new IllegalArgumentException("camera auth key must be 64 hexadecimal characters");
        }
    }

    private static Response parseResponseHeader(byte[] headerBytes) throws ProtocolException {
        String text = new String(headerBytes, StandardCharsets.ISO_8859_1);
        if (text.indexOf('\0') >= 0) throw new ProtocolException("RTSP response contains NUL");
        String[] lines = text.split("\\r\\n", -1);
        if (lines.length < 2) throw new ProtocolException("RTSP status line is missing");
        Matcher status = STATUS_LINE.matcher(lines[0]);
        if (!status.matches()) throw new ProtocolException("RTSP status line is malformed");
        int statusCode = Integer.parseInt(status.group(1));
        String reason = status.group(2) == null ? "" : status.group(2).trim();
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) throw new ProtocolException("Malformed RTSP header");
            String name = line.substring(0, colon).trim().toLowerCase(Locale.US);
            String value = line.substring(colon + 1).trim();
            if (value.isEmpty() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 ||
                    value.indexOf('\0') >= 0) {
                throw new ProtocolException("Malformed RTSP header value");
            }
            if (!name.matches("[a-z0-9-]+") || headers.put(name, value) != null) {
                throw new ProtocolException("Duplicate or malformed RTSP header");
            }
        }
        String cSeqText = headers.get("cseq");
        if (cSeqText == null || !cSeqText.matches("[1-9][0-9]{0,9}")) {
            throw new ProtocolException("RTSP CSeq is missing or malformed");
        }
        long cSeqLong = Long.parseLong(cSeqText);
        if (cSeqLong > Integer.MAX_VALUE) throw new ProtocolException("RTSP CSeq is too large");
        return new Response(statusCode, reason, (int) cSeqLong,
                Collections.unmodifiableMap(headers), new byte[0]);
    }

    private static int parseContentLength(String value) throws ProtocolException {
        if (value == null) return 0;
        if (!value.matches("[0-9]{1,5}")) throw new ProtocolException("Invalid Content-Length");
        int length = Integer.parseInt(value);
        if (length > MAX_BODY_BYTES) throw new ProtocolException("RTSP body exceeds limit");
        return length;
    }

    private static int[] parseNamedPortPair(String value, String key) throws ProtocolException {
        for (String part : value.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.regionMatches(true, 0, key + "=", 0, key.length() + 1)) continue;
            Matcher matcher = PORT_PAIR.matcher(trimmed.substring(key.length() + 1));
            if (!matcher.matches()) throw new ProtocolException("Malformed " + key);
            int first = Integer.parseInt(matcher.group(1));
            int second = Integer.parseInt(matcher.group(2));
            if (first < 1024 || first > 65534 || second != first + 1) {
                throw new ProtocolException("Invalid " + key);
            }
            return new int[]{first, second};
        }
        throw new ProtocolException("Missing " + key);
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(result, offset, length - offset);
            if (count < 0) throw new EOFException("RTSP response body is truncated");
            offset += count;
        }
        return result;
    }

    private static int findHeaderEnd(byte[] data) {
        for (int i = 0; i + 3 < data.length; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' &&
                    data[i + 2] == '\r' && data[i + 3] == '\n') return i + 4;
        }
        return -1;
    }

    private static void requireToken(String name, String value) {
        if (value == null || !value.matches("[A-Z]{3,16}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void requireUri(String uri) {
        requireHeaderValue("uri", uri);
        try {
            URI parsed = new URI(uri);
            if (!"rtsp".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null) {
                throw new IllegalArgumentException("RTSP URI is invalid");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("RTSP URI is invalid", e);
        }
    }

    private static void requireHeaderValue(String name, String value) {
        if (value == null || value.isEmpty() || value.indexOf('\r') >= 0 ||
                value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " contains invalid characters");
        }
    }

    public static final class Response {
        public final int statusCode;
        public final String reason;
        public final int cSeq;
        public final Map<String, String> headers;
        public final byte[] body;

        Response(int statusCode, String reason, int cSeq, Map<String, String> headers,
                byte[] body) {
            this.statusCode = statusCode;
            this.reason = reason;
            this.cSeq = cSeq;
            this.headers = headers;
            this.body = body.clone();
        }

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.US));
        }
    }

    public static final class Transport {
        public final int clientRtpPort;
        public final int clientRtcpPort;
        public final int serverRtpPort;
        public final int serverRtcpPort;
        public final long ssrc;

        Transport(int clientRtpPort, int clientRtcpPort, int serverRtpPort,
                int serverRtcpPort, long ssrc) {
            this.clientRtpPort = clientRtpPort;
            this.clientRtcpPort = clientRtcpPort;
            this.serverRtpPort = serverRtpPort;
            this.serverRtcpPort = serverRtcpPort;
            this.ssrc = ssrc;
        }
    }

    public static final class ProtocolException extends IOException {
        public ProtocolException(String message) {
            super(message);
        }

        public ProtocolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
