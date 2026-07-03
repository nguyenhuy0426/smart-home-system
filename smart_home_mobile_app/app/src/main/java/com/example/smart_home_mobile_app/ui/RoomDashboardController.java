/*
 * Responsibility: placeholder UI boundary for room dashboards, node status,
 * readings, access events, and video snapshots.
 */
package com.example.smart_home_mobile_app.ui;

import com.example.smart_home_mobile_app.model.CapabilityDescriptor;
import com.example.smart_home_mobile_app.model.NodeSummary;
import com.example.smart_home_mobile_app.model.ReadingEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RoomDashboardController {
    private final GenericNodeRenderer renderer;

    public RoomDashboardController(GenericNodeRenderer renderer) {
        this.renderer = renderer;
    }

    public List<GenericNodeRenderer.RenderedCard> renderRoom(List<NodeSummary> nodes,
            Map<String, CapabilityDescriptor> descriptorsByNodeId,
            Map<String, ReadingEnvelope> latestReadingsByNodeId) {
        List<GenericNodeRenderer.RenderedCard> cards = new ArrayList<>();
        for (NodeSummary node : nodes) {
            CapabilityDescriptor descriptor = descriptorsByNodeId.get(node.nodeId);
            if (descriptor != null) {
                cards.add(renderer.render(node, descriptor, latestReadingsByNodeId.get(node.nodeId)));
            }
        }
        return cards;
    }
}
