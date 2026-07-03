package com.android.smarthome.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.smarthome.security.GatewaySecurityPolicy;

import org.junit.Test;

public final class NodeProvisioningCoordinatorTest {
    private static final String DESCRIPTOR = "{"
            + "\"schemaVersion\":1,"
            + "\"nodeType\":\"environment.sensor\","
            + "\"displayName\":\"Environment node\","
            + "\"firmware\":{},\"transports\":{},"
            + "\"metrics\":[],\"events\":[],\"actions\":[]"
            + "}";

    @Test
    public void provision_assignsUniqueStableIdsForMultipleIdenticalNodes() {
        NodeProvisioningCoordinator.InMemoryNodeIdentityStore store =
                new NodeProvisioningCoordinator.InMemoryNodeIdentityStore();
        NodeProvisioningCoordinator firstCoordinator = coordinator(store);
        NodeProvisioningCoordinator.ProvisionedNode first = firstCoordinator.provision(
                "home_1", DESCRIPTOR, "efuse-a", "room_kitchen", "Ceiling east", null);
        NodeProvisioningCoordinator.ProvisionedNode second = firstCoordinator.provision(
                "home_1", DESCRIPTOR, "efuse-b", "room_kitchen", "Ceiling west", null);

        assertNotEquals(first.nodeId, second.nodeId);
        assertTrue(first.nodeId.matches("node_[0-9a-f]{32}"));

        NodeProvisioningCoordinator afterRestart = coordinator(store);
        NodeProvisioningCoordinator.ProvisionedNode moved = afterRestart.provision(
                "home_1", DESCRIPTOR, "efuse-a", "room_bedroom", "Bedside", null);
        assertEquals(first.nodeId, moved.nodeId);
        assertEquals(first.installedAt, moved.installedAt);
        assertEquals("room_bedroom", moved.roomId);
        assertEquals("pending_first_heartbeat", moved.status);
    }

    private static NodeProvisioningCoordinator coordinator(
            NodeProvisioningCoordinator.NodeIdentityStore store) {
        return new NodeProvisioningCoordinator(
                new DescriptorRegistry(),
                new GatewaySecurityPolicy.MockHomeSecretProvider(
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                store);
    }
}
