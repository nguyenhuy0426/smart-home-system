/*
 * Responsibility: models the self-describing node capability descriptor used
 * by the mobile app for generic node rendering.
 */
package com.example.smart_home_mobile_app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CapabilityDescriptor {
    public final String nodeId;
    public final String nodeType;
    public final String displayName;
    public final int descriptorSchemaVersion;
    public final List<Metric> metrics;
    public final List<Action> actions;

    public CapabilityDescriptor(String nodeId, String nodeType, String displayName,
            int descriptorSchemaVersion, List<Metric> metrics, List<Action> actions) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.displayName = displayName;
        this.descriptorSchemaVersion = descriptorSchemaVersion;
        this.metrics = Collections.unmodifiableList(new ArrayList<>(metrics));
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
    }

    public static final class Metric {
        public final String key;
        public final String unit;
        public final String source;
        public final String role;

        public Metric(String key, String unit, String source, String role) {
            this.key = key;
            this.unit = unit;
            this.source = source;
            this.role = role;
        }
    }

    public static final class Action {
        public final String key;
        public final String requiredRole;

        public Action(String key, String requiredRole) {
            this.key = key;
            this.requiredRole = requiredRole;
        }
    }
}
