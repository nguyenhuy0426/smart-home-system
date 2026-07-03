/*
 * Responsibility: placeholder boundary for BLE Mesh provisioning/proxy duties
 * without implementing stack internals or vendor model IDs.
 */
package com.android.smarthome.mesh;

import java.util.LinkedList;
import java.util.Queue;

public final class BleMeshProvisionerStub {
    private final Queue<String> unprovisionedDescriptors = new LinkedList<>();

    public void enqueueUnprovisionedDescriptor(String descriptorJson) {
        unprovisionedDescriptors.add(descriptorJson);
    }

    public String nextUnprovisionedDescriptor() {
        return unprovisionedDescriptors.poll();
    }

    public boolean hasUnprovisionedNode() {
        return !unprovisionedDescriptors.isEmpty();
    }
}
