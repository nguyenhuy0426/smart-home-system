/*
 * Responsibility: placeholder registry for raw node capability descriptors,
 * descriptor hashes, and generic rendering metadata.
 * Per ARCHITECTURE.md §4, descriptors contain only capability fields —
 * nodeId and homeId come from the provisioning coordinator, not the descriptor.
 */
package com.android.smarthome.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DescriptorRegistry {
    private final Map<String, DescriptorRecord> recordsByHash = new LinkedHashMap<>();

    /**
     * Parses, validates, and stores a descriptor JSON.
     * The descriptor must contain only §4 capability fields (schemaVersion, nodeType, etc.).
     * nodeId and homeId are NOT expected inside the descriptor — they come from
     * the provisioning coordinator and are stored separately in Node Identity.
     */
    public DescriptorRecord parseValidateAndStore(String rawJson) {
        Object parsed = new JsonParser(rawJson).parse();
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("descriptor root must be a JSON object");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        validate(root);

        String descriptorHash = "sha256:" + sha256Hex(rawJson);

        DescriptorRecord record = new DescriptorRecord(
                valueAsString(root.get("nodeType")),
                ((Number) root.get("schemaVersion")).intValue(),
                valueAsString(root.get("displayName")),
                descriptorHash,
                rawJson,
                root);
        recordsByHash.put(descriptorHash, record);
        return record;
    }

    public DescriptorRecord getByHash(String descriptorHash) {
        return recordsByHash.get(descriptorHash);
    }

    public int size() {
        return recordsByHash.size();
    }

    /**
     * Validates the §4 required core fields: schemaVersion, nodeType,
     * firmware, transports, metrics, events, actions.
     * Unknown fields are ignored per §4 policy.
     */
    private static void validate(Map<String, Object> root) {
        requireNumber(root, "schemaVersion");
        requireString(root, "nodeType");
        requireMap(root, "firmware");
        requireMap(root, "transports");
        requireList(root, "metrics");
        requireList(root, "events");
        requireList(root, "actions");

        Number version = (Number) root.get("schemaVersion");
        if (version.intValue() != 1) {
            throw new IllegalArgumentException("unsupported schemaVersion: " + version);
        }
    }

    private static void requireString(Map<String, Object> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException("missing required string field: " + field);
        }
    }

    private static void requireNumber(Map<String, Object> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("missing required number field: " + field);
        }
    }

    private static void requireMap(Map<String, Object> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("missing required object field: " + field);
        }
    }

    private static void requireList(Map<String, Object> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("missing required array field: " + field);
        }
    }

    private static String valueAsString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static final class DescriptorRecord {
        public final String nodeType;
        public final int schemaVersion;
        public final String displayName;
        public final String descriptorHash;
        public final String rawJson;
        public final Map<String, Object> fields;

        private DescriptorRecord(String nodeType, int schemaVersion, String displayName,
                String descriptorHash, String rawJson, Map<String, Object> fields) {
            this.nodeType = nodeType;
            this.schemaVersion = schemaVersion;
            this.displayName = displayName;
            this.descriptorHash = descriptorHash;
            this.rawJson = rawJson;
            this.fields = Collections.unmodifiableMap(fields);
        }
    }

    /* ===================== Dependency-free JSON parser ===================== */

    static final class JsonParser {
        private final String json;
        private int index;

        JsonParser(String json) {
            if (json == null) {
                throw new IllegalArgumentException("json must not be null");
            }
            this.json = json;
        }

        Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (index != json.length()) {
                throw new IllegalArgumentException("trailing JSON data at " + index);
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= json.length()) {
                throw new IllegalArgumentException("unexpected end of JSON");
            }
            char c = json.charAt(index);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't') {
                expectLiteral("true");
                return Boolean.TRUE;
            }
            if (c == 'f') {
                expectLiteral("false");
                return Boolean.FALSE;
            }
            if (c == 'n') {
                expectLiteral("null");
                return null;
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> object = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek('}')) {
                expect('}');
                return object;
            }
            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            List<Object> array = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek(']')) {
                expect(']');
                return array;
            }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() {
            StringBuilder builder = new StringBuilder();
            expect('"');
            while (index < json.length()) {
                char c = json.charAt(index++);
                if (c == '"') {
                    return builder.toString();
                }
                if (c == '\\') {
                    if (index >= json.length()) {
                        throw new IllegalArgumentException("unterminated escape");
                    }
                    char escaped = json.charAt(index++);
                    switch (escaped) {
                        case '"':
                        case '\\':
                        case '/':
                            builder.append(escaped);
                            break;
                        case 'b':
                            builder.append('\b');
                            break;
                        case 'f':
                            builder.append('\f');
                            break;
                        case 'n':
                            builder.append('\n');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        case 't':
                            builder.append('\t');
                            break;
                        case 'u':
                            builder.append(parseUnicodeEscape());
                            break;
                        default:
                            throw new IllegalArgumentException("bad escape: " + escaped);
                    }
                } else {
                    builder.append(c);
                }
            }
            throw new IllegalArgumentException("unterminated string");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > json.length()) {
                throw new IllegalArgumentException("short unicode escape");
            }
            String hex = json.substring(index, index + 4);
            index += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < json.length() && Character.isDigit(json.charAt(index))) {
                index++;
            }
            boolean decimal = false;
            if (index < json.length() && json.charAt(index) == '.') {
                decimal = true;
                index++;
                while (index < json.length() && Character.isDigit(json.charAt(index))) {
                    index++;
                }
            }
            if (index < json.length() && (json.charAt(index) == 'e' || json.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (index < json.length() && (json.charAt(index) == '+' || json.charAt(index) == '-')) {
                    index++;
                }
                while (index < json.length() && Character.isDigit(json.charAt(index))) {
                    index++;
                }
            }
            if (start == index) {
                throw new IllegalArgumentException("expected number at " + index);
            }
            String token = json.substring(start, index);
            return decimal ? Double.valueOf(token) : Long.valueOf(token);
        }

        private void expectLiteral(String literal) {
            if (!json.startsWith(literal, index)) {
                throw new IllegalArgumentException("expected " + literal + " at " + index);
            }
            index += literal.length();
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= json.length() || json.charAt(index) != expected) {
                throw new IllegalArgumentException("expected " + expected + " at " + index);
            }
            index++;
        }

        private boolean peek(char expected) {
            skipWhitespace();
            return index < json.length() && json.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }
    }
}
