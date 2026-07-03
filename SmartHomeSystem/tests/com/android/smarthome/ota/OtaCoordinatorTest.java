package com.android.smarthome.ota;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OtaCoordinatorTest {
    @Test
    public void apply_rejectsHashMismatchBeforeSignatureAcceptance() throws Exception {
        OtaCoordinator.MockBleMeshAnnouncer announcer = new OtaCoordinator.MockBleMeshAnnouncer();
        OtaCoordinator.MockWifiImageTransport transport = new OtaCoordinator.MockWifiImageTransport();
        transport.put("https://gateway/firmware.bin", new byte[] {1, 2, 3});
        OtaCoordinator coordinator = new OtaCoordinator(announcer, transport, (manifest, image) -> true);

        OtaCoordinator.UpdateState state = coordinator.apply(
                "node_1", manifest("sha256:incorrect"), true);

        assertEquals("rejected", state.status);
        assertEquals("hash_mismatch", state.rollbackReason);
        assertEquals(1, announcer.announceCount);
    }

    @Test
    public void apply_marksConfirmedOnlyAfterVerifiedImageAndBootHealth() throws Exception {
        byte[] image = new byte[] {1, 2, 3};
        OtaCoordinator.MockWifiImageTransport transport = new OtaCoordinator.MockWifiImageTransport();
        transport.put("https://gateway/firmware.bin", image);
        OtaCoordinator coordinator = new OtaCoordinator(
                new OtaCoordinator.MockBleMeshAnnouncer(), transport, (manifest, bytes) -> true);

        OtaCoordinator.UpdateState state = coordinator.apply(
                "node_1", manifest("sha256:" + OtaCoordinator.sha256Hex(image)), true);

        assertEquals("confirmed", state.status);
        assertEquals("1.1.0", state.targetVersion);
    }

    private static OtaCoordinator.Manifest manifest(String hash) {
        return new OtaCoordinator.Manifest(
                "environment.sensor",
                "rev-a",
                "1.0.0",
                "1.1.0",
                "https://gateway/firmware.bin",
                hash,
                "base64-signature",
                "automatic",
                "mains_powered");
    }
}
