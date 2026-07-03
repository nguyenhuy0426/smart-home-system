/*
 * Responsibility: placeholder repository boundary for reading generic smart
 * home nodes, descriptors, readings, events, and snapshots from Firebase.
 */
package com.example.smart_home_mobile_app.repository;

import com.example.smart_home_mobile_app.model.AccessEventEnvelope;
import com.example.smart_home_mobile_app.model.CapabilityDescriptor;
import com.example.smart_home_mobile_app.model.NodeSummary;
import com.example.smart_home_mobile_app.model.ReadingEnvelope;
import com.example.smart_home_mobile_app.model.VideoSnapshotEnvelope;

import java.util.ArrayList;
import java.util.List;

public final class FirebaseSmartHomeRepository {
    private final DataSource dataSource;

    public FirebaseSmartHomeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void observeHome(String homeId, Listener listener) {
        listener.onSnapshot(new HomeSnapshot(
                dataSource.nodes(homeId),
                dataSource.descriptors(homeId),
                dataSource.readings(homeId),
                dataSource.accessEvents(homeId),
                dataSource.videoSnapshots(homeId)));
    }

    public interface Listener {
        void onSnapshot(HomeSnapshot snapshot);
    }

    public interface DataSource {
        List<NodeSummary> nodes(String homeId);
        List<CapabilityDescriptor> descriptors(String homeId);
        List<ReadingEnvelope> readings(String homeId);
        List<AccessEventEnvelope> accessEvents(String homeId);
        List<VideoSnapshotEnvelope> videoSnapshots(String homeId);
    }

    public static final class HomeSnapshot {
        public final List<NodeSummary> nodes;
        public final List<CapabilityDescriptor> descriptors;
        public final List<ReadingEnvelope> readings;
        public final List<AccessEventEnvelope> accessEvents;
        public final List<VideoSnapshotEnvelope> videoSnapshots;

        HomeSnapshot(List<NodeSummary> nodes, List<CapabilityDescriptor> descriptors,
                List<ReadingEnvelope> readings, List<AccessEventEnvelope> accessEvents,
                List<VideoSnapshotEnvelope> videoSnapshots) {
            this.nodes = nodes;
            this.descriptors = descriptors;
            this.readings = readings;
            this.accessEvents = accessEvents;
            this.videoSnapshots = videoSnapshots;
        }
    }

    public static final class InMemoryDataSource implements DataSource {
        public final List<NodeSummary> nodes = new ArrayList<>();
        public final List<CapabilityDescriptor> descriptors = new ArrayList<>();
        public final List<ReadingEnvelope> readings = new ArrayList<>();
        public final List<AccessEventEnvelope> accessEvents = new ArrayList<>();
        public final List<VideoSnapshotEnvelope> videoSnapshots = new ArrayList<>();

        @Override
        public List<NodeSummary> nodes(String homeId) {
            return new ArrayList<>(nodes);
        }

        @Override
        public List<CapabilityDescriptor> descriptors(String homeId) {
            return new ArrayList<>(descriptors);
        }

        @Override
        public List<ReadingEnvelope> readings(String homeId) {
            return new ArrayList<>(readings);
        }

        @Override
        public List<AccessEventEnvelope> accessEvents(String homeId) {
            return new ArrayList<>(accessEvents);
        }

        @Override
        public List<VideoSnapshotEnvelope> videoSnapshots(String homeId) {
            return new ArrayList<>(videoSnapshots);
        }
    }
}
