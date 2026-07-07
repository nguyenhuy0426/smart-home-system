#ifndef CREDENTIAL_STORE_H
#define CREDENTIAL_STORE_H

#include "access_control_pipeline.h"
#include "access_credential_privacy.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define ACCESS_MAX_RFID_CREDENTIALS 32
#define ACCESS_MAX_FINGERPRINT_CREDENTIALS 32

typedef struct {
    bool ready;
    uint8_t hmac_key[ACCESS_HMAC_KEY_SIZE];
    char rfid_hashes[ACCESS_MAX_RFID_CREDENTIALS][ACCESS_HASH_STRING_SIZE];
    size_t rfid_count;
    char fingerprint_hashes[ACCESS_MAX_FINGERPRINT_CREDENTIALS][ACCESS_HASH_STRING_SIZE];
    size_t fingerprint_count;
} credential_store_t;

bool credential_store_load(credential_store_t *store);

/*
 * Adds the HMAC hash of an RFID UID to the persistent allowlist. Only the
 * hash is stored, never the raw UID. Idempotent: re-presenting an enrolled
 * card succeeds and sets *out_already_enrolled. Fails when the store is not
 * ready (no HMAC key), the allowlist is full, or NVS writes fail.
 */
bool credential_store_enroll_rfid(credential_store_t *store,
                                  const uint8_t *uid,
                                  size_t uid_len,
                                  char *out_hash,
                                  size_t out_hash_size,
                                  bool *out_already_enrolled);
bool credential_store_authorize_rfid(const credential_store_t *store,
                                     const uint8_t *uid,
                                     size_t uid_len,
                                     char *out_hash,
                                     size_t out_hash_size);
bool credential_store_authorize_fingerprint(const credential_store_t *store,
                                            uint16_t page_id,
                                            char *out_hash,
                                            size_t out_hash_size);
void credential_store_clear_memory(credential_store_t *store);

#endif
