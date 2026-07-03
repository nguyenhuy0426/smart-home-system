/*
 * Durable queue boundary for Firebase Realtime Database writes after local
 * gateway persistence.
 */
package com.android.smarthome.firebase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class FirebaseSyncQueue {
    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");
    private final Path queueFile;
    private final RealtimeDatabaseWriter writer;
    private final List<QueuedRecord> records = new ArrayList<>();

    public FirebaseSyncQueue(Path queueFile, RealtimeDatabaseWriter writer) throws IOException {
        this.queueFile = queueFile;
        this.writer = writer;
        load();
    }

    public synchronized QueuedRecord enqueue(String homeId, String collection, String nodeId,
            long sequence, String jsonPayload) throws IOException {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence is required");
        }
        return enqueue(homeId, collection, nodeId, nodeId + "_" + sequence, jsonPayload);
    }

    public synchronized QueuedRecord enqueue(String homeId, String collection, String nodeId,
            String recordId, String jsonPayload) throws IOException {
        validateIdentifier("homeId", homeId);
        validatePath("collection", collection);
        validateIdentifier("nodeId", nodeId);
        validateIdentifier("recordId", recordId);
        if (jsonPayload == null || jsonPayload.isEmpty()) {
            throw new IllegalArgumentException("jsonPayload is required");
        }
        for (QueuedRecord existing : records) {
            if (existing.homeId.equals(homeId)
                    && existing.collection.equals(collection)
                    && existing.nodeId.equals(nodeId)
                    && existing.recordId.equals(recordId)) {
                if (!existing.jsonPayload.equals(jsonPayload)) {
                    throw new IOException("conflicting payload for " + existing.idempotencyKey());
                }
                return existing;
            }
        }
        QueuedRecord record = new QueuedRecord(
                homeId, collection, nodeId, recordId, jsonPayload, false);
        records.add(record);
        persist();
        return record;
    }

    public synchronized int replay() throws IOException {
        int uploaded = 0;
        for (QueuedRecord record : records) {
            if (!record.uploaded) {
                writer.write(record.documentPath(), record.idempotencyKey(), record.jsonPayload);
                record.uploaded = true;
                uploaded++;
            }
        }
        records.removeIf(record -> record.uploaded);
        persist();
        return uploaded;
    }

    public synchronized int pendingCount() {
        int pending = 0;
        for (QueuedRecord record : records) {
            if (!record.uploaded) {
                pending++;
            }
        }
        return pending;
    }

    public synchronized List<QueuedRecord> records() {
        return new ArrayList<>(records);
    }

    private void load() throws IOException {
        records.clear();
        if (!Files.exists(queueFile)) {
            return;
        }
        for (String line : Files.readAllLines(queueFile, StandardCharsets.UTF_8)) {
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\|", 6);
            if (parts.length != 6) {
                continue;
            }
            try {
                validateIdentifier("homeId", parts[0]);
                validatePath("collection", parts[1]);
                validateIdentifier("nodeId", parts[2]);
                String recordId = parts[3].matches("[0-9]+")
                        ? parts[2] + "_" + parts[3]
                        : parts[3];
                validateIdentifier("recordId", recordId);
                if (!("true".equals(parts[4]) || "false".equals(parts[4]))) {
                    continue;
                }
                String payload = new String(
                        Base64.getDecoder().decode(parts[5]), StandardCharsets.UTF_8);
                if (payload.isEmpty()) {
                    continue;
                }
                records.add(new QueuedRecord(
                        parts[0], parts[1], parts[2], recordId, payload,
                        Boolean.parseBoolean(parts[4])));
            } catch (IllegalArgumentException ignored) {
                // Preserve the remaining queue when one line is corrupt or has an unsafe path.
            }
        }
    }

    private void persist() throws IOException {
        List<String> lines = new ArrayList<>();
        if (queueFile.getParent() != null) {
            Files.createDirectories(queueFile.getParent());
        }
        for (QueuedRecord record : records) {
            lines.add(record.homeId + "|" +
                    record.collection + "|" +
                    record.nodeId + "|" +
                    record.recordId + "|" +
                    record.uploaded + "|" +
                    Base64.getEncoder().encodeToString(record.jsonPayload.getBytes(StandardCharsets.UTF_8)));
        }
        Path temporaryFile = queueFile.resolveSibling(queueFile.getFileName() + ".tmp");
        Files.write(temporaryFile, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temporaryFile, queueFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporaryFile, queueFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void validateIdentifier(String name, String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void validatePath(String name, String value) {
        if (value == null || value.length() > 512) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        String[] segments = value.split("/", -1);
        if (segments.length == 0) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        for (String segment : segments) {
            validateIdentifier(name, segment);
        }
    }

    public interface RealtimeDatabaseWriter {
        void write(String documentPath, String idempotencyKey, String jsonPayload) throws IOException;
    }

    public static final class QueuedRecord {
        public final String homeId;
        public final String collection;
        public final String nodeId;
        public final String recordId;
        public final String jsonPayload;
        public boolean uploaded;

        QueuedRecord(String homeId, String collection, String nodeId,
                String recordId, String jsonPayload, boolean uploaded) {
            this.homeId = homeId;
            this.collection = collection;
            this.nodeId = nodeId;
            this.recordId = recordId;
            this.jsonPayload = jsonPayload;
            this.uploaded = uploaded;
        }

        public String idempotencyKey() {
            return recordId;
        }

        public String documentPath() {
            return "homes/" + homeId + "/" + collection + "/" + idempotencyKey();
        }
    }

    public static final class MockRealtimeDatabase implements RealtimeDatabaseWriter {
        private final Map<String, String> documents = new LinkedHashMap<>();
        private boolean online = true;

        public void setOnline(boolean online) {
            this.online = online;
        }

        @Override
        public void write(String documentPath, String idempotencyKey, String jsonPayload) throws IOException {
            if (!online) {
                throw new IOException("Realtime Database emulator offline");
            }
            documents.putIfAbsent(documentPath, jsonPayload);
        }

        public int documentCount() {
            return documents.size();
        }

        public String get(String documentPath) {
            return documents.get(documentPath);
        }
    }
}
