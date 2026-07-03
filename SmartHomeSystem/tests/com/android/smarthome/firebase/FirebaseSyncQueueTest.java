package com.android.smarthome.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class FirebaseSyncQueueTest {
    @Test
    public void replay_isDurableIdempotentAndCompactsUploadedRecords() throws Exception {
        Path queueFile = Files.createTempFile("smarthome-sync", ".queue");
        try {
            FirebaseSyncQueue.MockRealtimeDatabase writer =
                    new FirebaseSyncQueue.MockRealtimeDatabase();
            FirebaseSyncQueue queue = new FirebaseSyncQueue(queueFile, writer);
            queue.enqueue("home_1", "nodes/node_1/readings", "node_1", 7, "{\"v\":1}");
            queue.enqueue("home_1", "nodes/node_1/readings", "node_1", 7, "{\"v\":1}");

            FirebaseSyncQueue restored = new FirebaseSyncQueue(queueFile, writer);
            assertEquals(1, restored.pendingCount());
            assertEquals(1, restored.replay());
            assertEquals(1, writer.documentCount());
            assertEquals(0, restored.pendingCount());
            assertTrue(Files.readAllLines(queueFile).isEmpty());
        } finally {
            Files.deleteIfExists(queueFile);
        }
    }

    @Test(expected = IOException.class)
    public void enqueue_rejectsConflictingPayloadForSameIdempotencyKey() throws Exception {
        Path queueFile = Files.createTempFile("smarthome-sync", ".queue");
        try {
            FirebaseSyncQueue queue = new FirebaseSyncQueue(
                    queueFile, new FirebaseSyncQueue.MockRealtimeDatabase());
            queue.enqueue("home_1", "events", "node_1", 9, "{\"v\":1}");
            queue.enqueue("home_1", "events", "node_1", 9, "{\"v\":2}");
        } finally {
            Files.deleteIfExists(queueFile);
        }
    }

    @Test
    public void enqueue_serializesConcurrentNodeWriters() throws Exception {
        Path queueFile = Files.createTempFile("smarthome-sync", ".queue");
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            FirebaseSyncQueue queue = new FirebaseSyncQueue(
                    queueFile, new FirebaseSyncQueue.MockRealtimeDatabase());
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 50; index++) {
                final int sequence = index;
                futures.add(executor.submit(() -> {
                    try {
                        queue.enqueue(
                                "home_1",
                                "nodes/node_" + (sequence % 5) + "/readings",
                                "node_" + (sequence % 5),
                                sequence,
                                "{\"sequence\":" + sequence + "}");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Future<?> future : futures) future.get();
            assertEquals(50, queue.pendingCount());

            FirebaseSyncQueue restored = new FirebaseSyncQueue(
                    queueFile, new FirebaseSyncQueue.MockRealtimeDatabase());
            assertEquals(50, restored.pendingCount());
        } finally {
            executor.shutdownNow();
            Files.deleteIfExists(queueFile);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void enqueue_rejectsDatabasePathInjection() throws Exception {
        Path queueFile = Files.createTempFile("smarthome-sync", ".queue");
        try {
            FirebaseSyncQueue queue = new FirebaseSyncQueue(
                    queueFile, new FirebaseSyncQueue.MockRealtimeDatabase());
            queue.enqueue("../other-home", "events", "node_1", 1, "{\"v\":1}");
        } finally {
            Files.deleteIfExists(queueFile);
        }
    }

    @Test
    public void load_skipsCorruptRecordsAndKeepsValidRecords() throws Exception {
        Path queueFile = Files.createTempFile("smarthome-sync", ".queue");
        try {
            String payload = Base64.getEncoder().encodeToString(
                    "{\"v\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.write(queueFile, java.util.Arrays.asList(
                    "malformed",
                    "home_1|events|node_1|bad.record|false|" + payload,
                    "home_1|events|node_1|11|false|" + payload));

            FirebaseSyncQueue restored = new FirebaseSyncQueue(
                    queueFile, new FirebaseSyncQueue.MockRealtimeDatabase());
            assertEquals(1, restored.pendingCount());
            assertEquals(1, restored.replay());
        } finally {
            Files.deleteIfExists(queueFile);
        }
    }

    @Test
    public void enqueue_preservesProtocolRecordId() throws Exception {
        Path queueFile = Files.createTempFile("smarthome-sync", ".queue");
        try {
            FirebaseSyncQueue queue = new FirebaseSyncQueue(
                    queueFile, new FirebaseSyncQueue.MockRealtimeDatabase());
            FirebaseSyncQueue.QueuedRecord record = queue.enqueue(
                    "home_1", "events", "node_1", "node_1_00000042", "{\"v\":1}");
            assertEquals("node_1_00000042", record.idempotencyKey());
            assertEquals("homes/home_1/events/node_1_00000042", record.documentPath());
        } finally {
            Files.deleteIfExists(queueFile);
        }
    }
}
