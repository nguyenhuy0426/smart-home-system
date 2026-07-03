#include "access_credential_privacy.h"

#include <stdio.h>

#ifdef ESP_PLATFORM
#include "psa/crypto.h"
#else
#include <openssl/evp.h>
#include <openssl/hmac.h>
#endif

int access_credential_hmac_sha256(const uint8_t *raw_value,
                                  size_t raw_value_len,
                                  const uint8_t *key,
                                  size_t key_len,
                                  char *out_hash,
                                  size_t out_hash_size)
{
    unsigned char digest[32];
    size_t offset;

    if (raw_value == NULL || raw_value_len == 0 || key == NULL ||
            key_len != ACCESS_HMAC_KEY_SIZE ||
            out_hash == NULL || out_hash_size < ACCESS_HASH_STRING_SIZE) {
        return 0;
    }

#ifdef ESP_PLATFORM
    psa_key_attributes_t attributes = PSA_KEY_ATTRIBUTES_INIT;
    psa_key_id_t key_id = 0;
    size_t digest_len = 0;
    psa_set_key_type(&attributes, PSA_KEY_TYPE_HMAC);
    psa_set_key_bits(&attributes, key_len * 8);
    psa_set_key_usage_flags(&attributes, PSA_KEY_USAGE_SIGN_MESSAGE);
    psa_set_key_algorithm(&attributes, PSA_ALG_HMAC(PSA_ALG_SHA_256));
    psa_status_t status = psa_crypto_init();
    if (status == PSA_SUCCESS) {
        status = psa_import_key(&attributes, key, key_len, &key_id);
    }
    if (status == PSA_SUCCESS) {
        status = psa_mac_compute(key_id, PSA_ALG_HMAC(PSA_ALG_SHA_256),
                raw_value, raw_value_len, digest, sizeof(digest), &digest_len);
    }
    if (key_id != 0) (void)psa_destroy_key(key_id);
    psa_reset_key_attributes(&attributes);
    if (status != PSA_SUCCESS || digest_len != sizeof(digest)) {
        return 0;
    }
#else
    unsigned int digest_len = sizeof(digest);
    if (HMAC(EVP_sha256(), key, (int)key_len, raw_value,
            raw_value_len, digest, &digest_len) == NULL || digest_len != sizeof(digest)) {
        return 0;
    }
#endif

    offset = (size_t)snprintf(out_hash, out_hash_size, "sha256:");
    for (size_t i = 0; i < sizeof(digest); i++) {
        int written = snprintf(out_hash + offset, out_hash_size - offset, "%02x", digest[i]);
        if (written != 2) {
            return 0;
        }
        offset += 2;
    }
    return offset == ACCESS_HASH_STRING_SIZE - 1;
}
