/*
 * Responsibility: placeholder UI boundary for rendering future node types from
 * capability descriptors without mobile app code changes.
 */
package com.example.smart_home_mobile_app.ui;

import com.example.smart_home_mobile_app.model.CapabilityDescriptor;
import com.example.smart_home_mobile_app.model.NodeSummary;
import com.example.smart_home_mobile_app.model.ReadingEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GenericNodeRenderer {
    public RenderedCard render(NodeSummary node, CapabilityDescriptor descriptor,
            ReadingEnvelope latestReading) {
        List<String> lines = new ArrayList<>();
        for (CapabilityDescriptor.Metric metric : descriptor.metrics) {
            ReadingEnvelope.MetricValue value = latestReading == null ? null : latestReading.metrics.get(metric.key);
            if (value == null) {
                lines.add(metric.key + " -- " + metric.unit);
            } else {
                lines.add(metric.key + " " + value.value + " " + value.unit);
            }
        }
        List<String> actions = new ArrayList<>();
        for (CapabilityDescriptor.Action action : descriptor.actions) {
            actions.add(action.key);
        }
        return new RenderedCard(
                descriptor.displayName == null || descriptor.displayName.isEmpty()
                        ? node.nodeType : descriptor.displayName,
                node.label,
                node.status,
                lines,
                actions);
    }

    public static final class RenderedCard {
        public final String title;
        public final String subtitle;
        public final String status;
        public final List<String> metricLines;
        public final List<String> actionKeys;

        RenderedCard(String title, String subtitle, String status,
                List<String> metricLines, List<String> actionKeys) {
            this.title = title;
            this.subtitle = subtitle;
            this.status = status;
            this.metricLines = metricLines;
            this.actionKeys = actionKeys;
        }

        public boolean containsMetric(String key) {
            for (String line : metricLines) {
                if (line.startsWith(key + " ")) {
                    return true;
                }
            }
            return false;
        }
    }
}
