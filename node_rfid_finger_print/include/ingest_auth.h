#ifndef INGEST_AUTH_H
#define INGEST_AUTH_H

#include <stdbool.h>
#include <stddef.h>

#define INGEST_AUTH_NONCE_HEX_LEN 32
#define INGEST_AUTH_SIGNATURE_HEX_LEN 64

/*
 * Header values for one signed gateway ingest request. The gateway
 * (IngestAuthenticator.kt) verifies:
 *
 *   X-Auth-Timestamp: Unix epoch seconds, decimal ASCII
 *   X-Auth-Nonce:     32 lowercase hex chars from the hardware RNG
 *   X-Auth-Signature: lowercase hex HMAC-SHA256 over
 *                     "<timestamp>\n<nonce>\n" + raw request body,
 *                     keyed with the ASCII bytes of the shared secret
 *
 * The shared secret is the "auth_key" NVS value (== ingest_hmac_secret on
 * the gateway; see smart_home/CONFIG_REQUIRED.md).
 */
typedef struct {
    char timestamp[21];
    char nonce[INGEST_AUTH_NONCE_HEX_LEN + 1];
    char signature[INGEST_AUTH_SIGNATURE_HEX_LEN + 1];
} ingest_auth_headers_t;

/* Starts SNTP polling (pool.ntp.org). Idempotent; call once Wi-Fi is up. */
void ingest_auth_time_sync_start(void);

/* True once the wall clock is past 2024-01-01, i.e. SNTP has synced. */
bool ingest_auth_time_is_valid(void);

/*
 * Fills out_headers with a signature for body. Returns false (and sends
 * nothing usable) when the auth key is missing/short or the clock is not
 * yet synced — callers must skip the request in that case.
 */
bool ingest_auth_sign(const char *auth_key,
                      const char *body,
                      size_t body_length,
                      ingest_auth_headers_t *out_headers);

#endif /* INGEST_AUTH_H */
