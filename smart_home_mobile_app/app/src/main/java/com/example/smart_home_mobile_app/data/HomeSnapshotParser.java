package com.example.smart_home_mobile_app.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class HomeSnapshotParser {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");

    private HomeSnapshotParser() {
    }

    public static HomeSnapshot parse(String homeId, String uid, Object raw) {
        requireIdentifier("homeId", homeId);
        if (uid == null || uid.trim().isEmpty()) {
            throw new IllegalArgumentException("uid is required");
        }
        Map<String, Object> root = asStringMap(raw);
        if (root == null) {
            root = new LinkedHashMap<>();
        }

        Map<String, Object> members = asStringMap(root.get("members"));
        Object memberRaw = members == null ? null : members.get(uid);
        String role = roleFromMember(memberRaw);
        if (role.isEmpty() && uid.equals(str(root, "owner"))) role = "device_admin";
        if (role.isEmpty() && uid.equals(str(root, "ownerUid"))) role = "device_admin";

        String displayName = str(root, "displayName");
        if (displayName == null) displayName = str(root, "name");
        if (displayName == null) displayName = homeId;

        List<NodeSummary> nodes = parseNodes(homeId, root.get("nodes"), root.get("descriptors"));
        List<AccessEvent> accessEvents = new ArrayList<>();
        List<DetectionEvent> detectionEvents = new ArrayList<>();
        parseEvents(root.get("events"), accessEvents, detectionEvents);
        List<CommandRequest> commands = parseCommands(homeId, root.get("commandRequests"));
        List<RoomSummary> rooms = parseRooms(root.get("rooms"), nodes, accessEvents, detectionEvents);

        List<NodeSummary> sortedNodes = new ArrayList<>(nodes);
        Collections.sort(sortedNodes, (a, b) -> {
            int c = a.roomId.compareTo(b.roomId);
            if (c != 0) return c;
            c = a.label.compareTo(b.label);
            if (c != 0) return c;
            return a.nodeId.compareTo(b.nodeId);
        });

        List<AccessEvent> sortedAccess = new ArrayList<>(accessEvents);
        Collections.sort(sortedAccess, (a, b) -> Long.compare(b.timestampEpochMs, a.timestampEpochMs));

        List<DetectionEvent> sortedDetection = new ArrayList<>(detectionEvents);
        Collections.sort(sortedDetection, (a, b) -> Long.compare(b.timestampEpochMs, a.timestampEpochMs));

        List<CommandRequest> sortedCommands = new ArrayList<>(commands);
        Collections.sort(sortedCommands, (a, b) -> Long.compare(b.createdAtEpochMs, a.createdAtEpochMs));

        return new HomeSnapshot(
                new HomeSummary(homeId, displayName, role),
                rooms, sortedNodes, sortedAccess, sortedDetection, sortedCommands);
    }

    private static List<NodeSummary> parseNodes(String homeId, Object raw, Object descriptorsRaw) {
        Map<String, Object> descriptors = asStringMap(descriptorsRaw);
        if (descriptors == null) descriptors = new LinkedHashMap<>();
        Map<String, Object> nodesMap = asStringMap(raw);
        if (nodesMap == null) nodesMap = new LinkedHashMap<>();

        List<NodeSummary> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : nodesMap.entrySet()) {
            Map<String, Object> node = asStringMap(entry.getValue());
            if (node == null) continue;
            String nodeId = str(node, "nodeId");
            if (nodeId == null) nodeId = entry.getKey();
            if (!isIdentifier(nodeId)) continue;

            Map<String, Object> readingsMap = asStringMap(node.get("readings"));
            if (readingsMap == null) readingsMap = new LinkedHashMap<>();
            List<TelemetryReading> readings = new ArrayList<>();
            for (Map.Entry<String, Object> r : readingsMap.entrySet()) {
                TelemetryReading reading = parseReading(homeId, nodeId, r.getKey(), r.getValue());
                if (reading != null) readings.add(reading);
            }
            Collections.sort(readings, (a, b) -> Long.compare(a.timestampEpochMs(), b.timestampEpochMs()));

            TelemetryReading latest = null;
            for (TelemetryReading r : readings) {
                if (latest == null || r.timestampEpochMs() > latest.timestampEpochMs()) latest = r;
            }

            Map<String, Object> location = asStringMap(node.get("location"));
            Map<String, Object> descriptor = null;
            String descriptorHash = str(node, "descriptorHash");
            if (descriptorHash != null) descriptor = asStringMap(descriptors.get(descriptorHash));

            String roomId = str(node, "roomId");
            if (roomId == null && location != null) roomId = str(location, "roomId");
            if (roomId == null && latest != null) roomId = latest.roomId;
            if (roomId == null) roomId = "unassigned";

            String nodeType = str(node, "nodeType");
            if (nodeType == null) nodeType = "unknown";

            String label = str(node, "label");
            if (label == null && location != null) label = str(location, "label");
            if (label == null) label = nodeId;

            Integer schemaVersion = integer(node, "schemaVersion");
            if (schemaVersion == null) schemaVersion = integer(node, "descriptorSchemaVersion");
            if (schemaVersion == null && latest != null) schemaVersion = latest.schemaVersion;
            if (schemaVersion == null) schemaVersion = 1;

            String status = str(node, "status");
            if (status == null) status = (latest == null) ? "no_data" : "reporting";

            Object actionsRaw = node.get("actions");
            if (actionsRaw == null && descriptor != null) actionsRaw = descriptor.get("actions");

            result.add(new NodeSummary(nodeId, homeId, roomId, nodeType, label,
                    schemaVersion, status, parseActions(actionsRaw), readings));
        }
        return result;
    }

    private static List<String> parseActions(Object raw) {
        List<String> empty = new ArrayList<>();
        if (!(raw instanceof List)) return empty;
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (Object actionRaw : (List<?>) raw) {
            String action = null;
            if (actionRaw instanceof String) {
                action = (String) actionRaw;
            } else if (actionRaw instanceof Map) {
                Object key = ((Map<?, ?>) actionRaw).get("key");
                if (key instanceof String) action = (String) key;
            }
            if (action != null && isIdentifier(action)) distinct.add(action);
        }
        return new ArrayList<>(distinct);
    }

    private static TelemetryReading parseReading(String homeId, String nodeId, String readingKey, Object raw) {
        Map<String, Object> reading = asStringMap(raw);
        if (reading == null) return null;
        String readingId = str(reading, "readingId");
        if (readingId == null) readingId = readingKey;
        if (!isIdentifier(readingId)) return null;

        Map<String, Object> metricsMap = asStringMap(reading.get("metrics"));
        if (metricsMap == null) metricsMap = new LinkedHashMap<>();
        Map<String, MetricReading> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, Object> m : metricsMap.entrySet()) {
            String key = m.getKey();
            Map<String, Object> metric = asStringMap(m.getValue());
            if (metric == null) continue;
            Boolean explicitValid = bool(metric, "valid");
            String quality = str(metric, "quality");
            if (quality == null) quality = str(metric, "status");
            Double value = dbl(metric, "value");
            if (value != null && !Double.isFinite(value)) value = null;
            String error = str(metric, "error");
            if (error == null) error = str(metric, "errorCode");

            String validity;
            if (Boolean.FALSE.equals(explicitValid)) {
                validity = quality != null ? quality : "invalid";
            } else if (error != null) {
                validity = quality != null ? quality : "error";
            } else if (Boolean.TRUE.equals(explicitValid) && value != null) {
                validity = quality != null ? quality : "valid";
            } else if (value != null && quality == null) {
                validity = "valid";
            } else {
                validity = quality != null ? quality : "missing";
            }

            metrics.put(key, new MetricReading(key, value,
                    orEmpty(str(metric, "unit")), orEmpty(str(metric, "source")),
                    validity, error, bool(metric, "calibrated")));
        }

        String readingNodeId = str(reading, "nodeId");
        if (readingNodeId == null) readingNodeId = nodeId;
        String readingRoomId = str(reading, "roomId");
        if (readingRoomId == null) readingRoomId = "unassigned";
        Integer schemaVersion = integer(reading, "schemaVersion");
        if (schemaVersion == null) schemaVersion = 1;
        Long sequence = lng(reading, "sequence");
        Long observed = lng(reading, "observedAtEpochMs");
        Long gateway = lng(reading, "gatewayReceivedAtEpochMs");
        return new TelemetryReading(readingId, homeId, readingNodeId, readingRoomId, schemaVersion,
                sequence == null ? -1L : sequence,
                observed == null ? 0L : observed,
                gateway == null ? 0L : gateway,
                metrics);
    }

    private static void parseEvents(Object raw, List<AccessEvent> accessEvents,
                                    List<DetectionEvent> detectionEvents) {
        Map<String, Object> eventsMap = asStringMap(raw);
        if (eventsMap == null) return;
        for (Map.Entry<String, Object> entry : eventsMap.entrySet()) {
            Map<String, Object> event = asStringMap(entry.getValue());
            if (event == null) continue;
            String eventId = str(event, "eventId");
            if (eventId == null) eventId = entry.getKey();
            if (!isIdentifier(eventId)) continue;
            String type = orEmpty(str(event, "eventType"));

            // Nodes send observedAtEpochMs = 0/null before their clock syncs;
            // fall through to the gateway receive time instead of epoch zero.
            long timestamp = firstPositive(
                    lng(event, "observedAtEpochMs"),
                    lng(event, "gatewayReceivedAtEpochMs"),
                    lng(event, "timestamp"));

            if (type.startsWith("video.") || event.get("className") != null) {
                BoundingBox box = null;
                Map<String, Object> bounds = asStringMap(event.get("boundingBox"));
                if (bounds != null) {
                    Double left = dbl(bounds, "left");
                    Double top = dbl(bounds, "top");
                    Double right = dbl(bounds, "right");
                    Double bottom = dbl(bounds, "bottom");
                    if (left != null && top != null && right != null && bottom != null
                            && right >= left && bottom >= top) {
                        box = new BoundingBox(left, top, right, bottom);
                    }
                }
                String className = str(event, "className");
                if (className == null) {
                    className = type.startsWith("video.") ? type.substring("video.".length()) : type;
                }
                Double confidence = dbl(event, "confidence");
                detectionEvents.add(new DetectionEvent(eventId,
                        orDefault(str(event, "nodeId"), "unknown"),
                        orDefault(str(event, "roomId"), "unassigned"),
                        timestamp, className,
                        confidence == null ? 0.0 : confidence, box));
            } else if (type.equals("access.attempt") || event.get("result") != null) {
                Map<String, Object> credential = asStringMap(event.get("credential"));
                String credentialType = credential == null ? null : str(credential, "kind");
                if (credentialType == null) credentialType = str(event, "credentialKind");
                if (credentialType == null) credentialType = "unknown";
                accessEvents.add(new AccessEvent(eventId,
                        orDefault(str(event, "nodeId"), "unknown"),
                        orDefault(str(event, "roomId"), "unassigned"),
                        orDefault(str(event, "result"), "unknown"),
                        credentialType, timestamp));
            }
        }
    }

    private static List<CommandRequest> parseCommands(String homeId, Object raw) {
        List<CommandRequest> result = new ArrayList<>();
        Map<String, Object> map = asStringMap(raw);
        if (map == null) return result;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Map<String, Object> request = asStringMap(entry.getValue());
            if (request == null) continue;
            String requestId = str(request, "requestId");
            if (requestId == null) requestId = entry.getKey();
            if (!isIdentifier(requestId)) continue;
            String commandType = str(request, "action");
            if (commandType == null) commandType = str(request, "commandType");
            Long createdAt = lng(request, "createdAtEpochMs");
            result.add(new CommandRequest(requestId,
                    orEmpty(str(request, "requestedBy")),
                    orDefault(str(request, "homeId"), homeId),
                    orEmpty(str(request, "nodeId")),
                    orEmpty(commandType),
                    createdAt == null ? 0L : createdAt,
                    orDefault(str(request, "status"), "pending")));
        }
        return result;
    }

    private static List<RoomSummary> parseRooms(Object raw, List<NodeSummary> nodes,
                                                List<AccessEvent> accessEvents,
                                                List<DetectionEvent> detectionEvents) {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        Map<String, Object> roomsMap = asStringMap(raw);
        if (roomsMap != null) {
            for (Map.Entry<String, Object> entry : roomsMap.entrySet()) {
                String roomId = entry.getKey();
                if (!isIdentifier(roomId)) continue;
                Object roomRaw = entry.getValue();
                Map<String, Object> room = asStringMap(roomRaw);
                String label = roomRaw instanceof String ? (String) roomRaw : null;
                if (label == null && room != null) label = str(room, "label");
                if (label == null && room != null) label = str(room, "name");
                if (label == null || label.trim().isEmpty()) label = roomId;
                labels.put(roomId, label);
            }
        }
        for (NodeSummary n : nodes) labels.putIfAbsent(n.roomId, n.roomId);
        for (AccessEvent a : accessEvents) labels.putIfAbsent(a.roomId, a.roomId);
        for (DetectionEvent d : detectionEvents) labels.putIfAbsent(d.roomId, d.roomId);

        List<RoomSummary> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            String roomId = entry.getKey();
            List<String> nodeIds = new ArrayList<>();
            for (NodeSummary n : nodes) {
                if (n.roomId.equals(roomId)) nodeIds.add(n.nodeId);
            }
            result.add(new RoomSummary(roomId, entry.getValue(), nodeIds));
        }
        Collections.sort(result, (a, b) -> a.label.compareTo(b.label));
        return result;
    }


    private static String roleFromMember(Object raw) {
        if (raw instanceof String) return normalizeRole((String) raw);
        Map<String, Object> member = asStringMap(raw);
        if (member == null) return "";
        String role = str(member, "role");
        if (role == null) role = str(member, "r");
        return normalizeRole(role);
    }

    private static String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) return "";
        if ("admin".equals(role) || "owner".equals(role)) return "device_admin";
        if ("member".equals(role)) return "home_member";
        return role;
    }

    private static long firstPositive(Long... candidates) {
        for (Long candidate : candidates) {
            if (candidate != null && candidate > 0L) return candidate;
        }
        return 0L;
    }

    private static void requireIdentifier(String name, String value) {
        if (!isIdentifier(value)) throw new IllegalArgumentException(name + " is invalid");
    }

    private static boolean isIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    private static Map<String, Object> asStringMap(Object raw) {
        if (!(raw instanceof Map)) return null;
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (entry.getKey() instanceof String) {
                result.put((String) entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String && !((String) value).trim().isEmpty()) {
            return (String) value;
        }
        return null;
    }

    private static Long lng(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private static Integer integer(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private static Double dbl(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private static Boolean bool(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
