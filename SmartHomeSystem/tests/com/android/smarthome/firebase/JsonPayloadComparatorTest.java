package com.android.smarthome.firebase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

public final class JsonPayloadComparatorTest {
    private static final java.util.Set<String> IGNORED_GATEWAY_TIME =
            Collections.singleton("gatewayReceivedAtEpochMs");

    @Test
    public void equivalent_ignoresObjectOrderNumericRepresentationAndGatewayTimestamp() {
        String queued = "{\"nodeId\":\"node_1\",\"value\":1," +
                "\"gatewayReceivedAtEpochMs\":{\".sv\":\"timestamp\"}}";
        String existing = "{\"gatewayReceivedAtEpochMs\":1783000000000," +
                "\"value\":1.0,\"nodeId\":\"node_1\"}";

        assertTrue(JsonPayloadComparator.INSTANCE.equivalentIgnoringRootFields(
                queued, existing, IGNORED_GATEWAY_TIME));
    }

    @Test
    public void equivalent_rejectsChangedMeasurement() {
        String queued = "{\"nodeId\":\"node_1\",\"metrics\":{\"co\":1.0}," +
                "\"gatewayReceivedAtEpochMs\":1}";
        String existing = "{\"nodeId\":\"node_1\",\"metrics\":{\"co\":2.0}," +
                "\"gatewayReceivedAtEpochMs\":2}";

        assertFalse(JsonPayloadComparator.INSTANCE.equivalentIgnoringRootFields(
                queued, existing, IGNORED_GATEWAY_TIME));
    }

    @Test
    public void equivalent_rejectsMalformedJson() {
        assertFalse(JsonPayloadComparator.INSTANCE.equivalentIgnoringRootFields(
                "not-json", "{}", IGNORED_GATEWAY_TIME));
    }
}
