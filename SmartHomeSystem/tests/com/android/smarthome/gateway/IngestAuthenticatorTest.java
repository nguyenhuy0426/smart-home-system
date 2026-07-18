package com.android.smarthome.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class IngestAuthenticatorTest {
    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final long NOW = 1_751_800_000L;
    private static final byte[] BODY =
            "{\"nodeId\":\"env_node_1\",\"roomId\":\"living_room\",\"metrics\":{}}"
                    .getBytes(StandardCharsets.UTF_8);

    private static IngestAuthenticator authenticator(String secret) {
        return new IngestAuthenticator(() -> secret, () -> NOW);
    }

    private static String sign(String secret, String timestamp, String nonce, byte[] body)
            throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
        mac.update((timestamp + "\n" + nonce + "\n").getBytes(StandardCharsets.US_ASCII));
        mac.update(body);
        StringBuilder hex = new StringBuilder();
        for (byte value : mac.doFinal()) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private static IngestAuthenticator.Failure failureOf(IngestAuthenticator.Result result) {
        assertTrue(result instanceof IngestAuthenticator.Result.Rejected);
        return ((IngestAuthenticator.Result.Rejected) result).getFailure();
    }

    @Test
    public void validSignatureIsAccepted() throws Exception {
        String timestamp = Long.toString(NOW);
        String nonce = "a3f09b21c47d58e6a3f09b21c47d58e6";
        IngestAuthenticator.Result result = authenticator(SECRET)
                .verify(timestamp, nonce, sign(SECRET, timestamp, nonce, BODY), BODY);
        assertTrue(result instanceof IngestAuthenticator.Result.Success);
    }

    @Test
    public void replayedNonceIsRejected() throws Exception {
        String timestamp = Long.toString(NOW);
        String nonce = "b4e18c32d58f69a7b4e18c32d58f69a7";
        String signature = sign(SECRET, timestamp, nonce, BODY);
        IngestAuthenticator authenticator = authenticator(SECRET);
        assertTrue(authenticator.verify(timestamp, nonce, signature, BODY)
                instanceof IngestAuthenticator.Result.Success);
        assertEquals(IngestAuthenticator.Failure.REPLAYED_NONCE,
                failureOf(authenticator.verify(timestamp, nonce, signature, BODY)));
    }

    @Test
    public void wrongSignatureIsRejected() throws Exception {
        String timestamp = Long.toString(NOW);
        String nonce = "c5f29d43e69a70b8c5f29d43e69a70b8";
        String signature = sign(SECRET, timestamp, nonce, BODY);
        String tampered = (signature.charAt(0) == '0' ? "1" : "0") + signature.substring(1);
        assertEquals(IngestAuthenticator.Failure.BAD_SIGNATURE,
                failureOf(authenticator(SECRET).verify(timestamp, nonce, tampered, BODY)));
    }

    @Test
    public void tamperedBodyIsRejected() throws Exception {
        String timestamp = Long.toString(NOW);
        String nonce = "d6a30e54f70b81c9d6a30e54f70b81c9";
        String signature = sign(SECRET, timestamp, nonce, BODY);
        byte[] tamperedBody =
                "{\"nodeId\":\"attacker\",\"roomId\":\"living_room\",\"metrics\":{}}"
                        .getBytes(StandardCharsets.UTF_8);
        assertEquals(IngestAuthenticator.Failure.BAD_SIGNATURE,
                failureOf(authenticator(SECRET)
                        .verify(timestamp, nonce, signature, tamperedBody)));
    }

    @Test
    public void staleTimestampIsRejected() throws Exception {
        String timestamp = Long.toString(NOW - 301);
        String nonce = "e7b41f65a81c92d0e7b41f65a81c92d0";
        assertEquals(IngestAuthenticator.Failure.STALE_TIMESTAMP,
                failureOf(authenticator(SECRET)
                        .verify(timestamp, nonce, sign(SECRET, timestamp, nonce, BODY), BODY)));
    }

    @Test
    public void futureTimestampIsRejected() throws Exception {
        String timestamp = Long.toString(NOW + 301);
        String nonce = "f8c52076b92da3e1f8c52076b92da3e1";
        assertEquals(IngestAuthenticator.Failure.STALE_TIMESTAMP,
                failureOf(authenticator(SECRET)
                        .verify(timestamp, nonce, sign(SECRET, timestamp, nonce, BODY), BODY)));
    }

    @Test
    public void missingHeadersAreRejected() {
        assertEquals(IngestAuthenticator.Failure.MISSING_HEADER,
                failureOf(authenticator(SECRET).verify(null, null, null, BODY)));
    }

    @Test
    public void malformedHeadersAreRejected() throws Exception {
        String timestamp = Long.toString(NOW);
        String nonce = "a9d63187ca3eb4f2a9d63187ca3eb4f2";
        String signature = sign(SECRET, timestamp, nonce, BODY);
        assertEquals(IngestAuthenticator.Failure.MALFORMED_HEADER,
                failureOf(authenticator(SECRET).verify("not-a-number", nonce, signature, BODY)));
        assertEquals(IngestAuthenticator.Failure.MALFORMED_HEADER,
                failureOf(authenticator(SECRET).verify(timestamp, "bad nonce!", signature, BODY)));
        assertEquals(IngestAuthenticator.Failure.MALFORMED_HEADER,
                failureOf(authenticator(SECRET).verify(timestamp, nonce, "zz", BODY)));
    }

    @Test
    public void missingSecretFailsClosed() throws Exception {
        String timestamp = Long.toString(NOW);
        String nonce = "ba74298dcb4fc5a3ba74298dcb4fc5a3";
        String signature = sign(SECRET, timestamp, nonce, BODY);
        assertEquals(IngestAuthenticator.Failure.NOT_CONFIGURED,
                failureOf(authenticator(null).verify(timestamp, nonce, signature, BODY)));
        assertEquals(IngestAuthenticator.Failure.NOT_CONFIGURED,
                failureOf(authenticator("short").verify(timestamp, nonce, signature, BODY)));
    }
}
