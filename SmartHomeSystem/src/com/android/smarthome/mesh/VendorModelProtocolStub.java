/*
 * Responsibility: placeholder boundary for the self-describing JSON protocol
 * exchanged over a future BLE Mesh vendor model.
 */
package com.android.smarthome.mesh;

public final class VendorModelProtocolStub {
    public String encodeDescriptorRequest(String nodeId) {
        return "{\"op\":\"descriptor.request\",\"nodeId\":\"" + escape(nodeId) + "\"}";
    }

    public String encodeDescriptorResponse(String descriptorJson) {
        return "{\"op\":\"descriptor.response\",\"descriptor\":" + descriptorJson + "}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
