#ifndef ACCESS_CREDENTIAL_PRIVACY_H
#define ACCESS_CREDENTIAL_PRIVACY_H

#include <stddef.h>
#include <stdint.h>

#define ACCESS_HMAC_KEY_SIZE 32
#define ACCESS_HASH_STRING_SIZE 72

int access_credential_hmac_sha256(const uint8_t *raw_value,
                                  size_t raw_value_len,
                                  const uint8_t *key,
                                  size_t key_len,
                                  char *out_hash,
                                  size_t out_hash_size);

#endif
