#include "credential_store.h"
#include "access_authorization.h"

#include "esp_log.h"
#include "nvs.h"

#include <stdio.h>
#include <string.h>

#define TAG "CREDENTIAL_STORE"
#define AUTH_NAMESPACE "access_auth"
#define KEY_HMAC_KEY "hmac_key"
#define KEY_RFID_COUNT "rfid_count"
#define KEY_FINGER_COUNT "fp_count"

static bool valid_hash_string(const char *hash, size_t length)
{
    if (hash == NULL || length != ACCESS_HASH_STRING_SIZE ||
            memcmp(hash, "sha256:", 7) != 0 ||
            hash[ACCESS_HASH_STRING_SIZE - 1] != '\0') {
        return false;
    }
    for (size_t i = 7; i < ACCESS_HASH_STRING_SIZE - 1; i++) {
        char value = hash[i];
        if (!((value >= '0' && value <= '9') ||
                (value >= 'a' && value <= 'f'))) {
            return false;
        }
    }
    return true;
}

static bool load_hashes(nvs_handle_t handle,
                        const char *key_prefix,
                        char hashes[][ACCESS_HASH_STRING_SIZE],
                        size_t count)
{
    for (size_t i = 0; i < count; i++) {
        char key[16];
        size_t length = ACCESS_HASH_STRING_SIZE;
        int written = snprintf(key, sizeof(key), "%s%02u", key_prefix, (unsigned)i);
        if (written <= 0 || (size_t)written >= sizeof(key) ||
                nvs_get_str(handle, key, hashes[i], &length) != ESP_OK ||
                !valid_hash_string(hashes[i], length)) {
            ESP_LOGE(TAG, "Invalid allowlist entry %s", key);
            return false;
        }
    }
    return true;
}

bool credential_store_load(credential_store_t *store)
{
    nvs_handle_t handle;
    uint8_t rfid_count = 0;
    uint8_t fingerprint_count = 0;
    size_t key_length = ACCESS_HMAC_KEY_SIZE;

    if (store == NULL) return false;
    memset(store, 0, sizeof(*store));

    esp_err_t error = nvs_open(AUTH_NAMESPACE, NVS_READONLY, &handle);
    if (error != ESP_OK) {
        ESP_LOGE(TAG, "Credential NVS namespace is missing; access remains locked");
        return false;
    }

    error = nvs_get_blob(handle, KEY_HMAC_KEY, store->hmac_key, &key_length);
    uint8_t key_or = 0;
    for (size_t i = 0; i < sizeof(store->hmac_key); i++) key_or |= store->hmac_key[i];
    if (error != ESP_OK || key_length != ACCESS_HMAC_KEY_SIZE ||
            key_or == 0 ||
            nvs_get_u8(handle, KEY_RFID_COUNT, &rfid_count) != ESP_OK ||
            nvs_get_u8(handle, KEY_FINGER_COUNT, &fingerprint_count) != ESP_OK ||
            rfid_count > ACCESS_MAX_RFID_CREDENTIALS ||
            fingerprint_count > ACCESS_MAX_FINGERPRINT_CREDENTIALS) {
        ESP_LOGE(TAG, "Credential key or allowlist metadata is invalid; access remains locked");
        nvs_close(handle);
        credential_store_clear_memory(store);
        return false;
    }

    store->rfid_count = rfid_count;
    store->fingerprint_count = fingerprint_count;
    bool loaded = load_hashes(handle, "rfid", store->rfid_hashes, store->rfid_count) &&
            load_hashes(handle, "fp", store->fingerprint_hashes, store->fingerprint_count);
    nvs_close(handle);
    if (!loaded) {
        credential_store_clear_memory(store);
        return false;
    }
    store->ready = true;
    ESP_LOGI(TAG, "Loaded %u RFID and %u fingerprint allowlist entries",
            (unsigned)store->rfid_count, (unsigned)store->fingerprint_count);
    return true;
}

bool credential_store_authorize_rfid(const credential_store_t *store,
                                     const uint8_t *uid,
                                     size_t uid_len,
                                     char *out_hash,
                                     size_t out_hash_size)
{
    if (store == NULL || !store->ready || uid == NULL || uid_len == 0 || uid_len > 10 ||
            !access_credential_hmac_sha256(uid, uid_len, store->hmac_key,
                    sizeof(store->hmac_key), out_hash, out_hash_size)) {
        return false;
    }
    return access_authorization_hash_matches(
            out_hash, store->rfid_hashes, store->rfid_count);
}

bool credential_store_authorize_fingerprint(const credential_store_t *store,
                                            uint16_t page_id,
                                            char *out_hash,
                                            size_t out_hash_size)
{
    uint8_t raw_page_id[2] = {
        (uint8_t)(page_id >> 8),
        (uint8_t)(page_id & 0xFF),
    };
    if (store == NULL || !store->ready ||
            !access_credential_hmac_sha256(raw_page_id, sizeof(raw_page_id),
                    store->hmac_key, sizeof(store->hmac_key), out_hash, out_hash_size)) {
        return false;
    }
    return access_authorization_hash_matches(
            out_hash, store->fingerprint_hashes, store->fingerprint_count);
}

void credential_store_clear_memory(credential_store_t *store)
{
    if (store == NULL) return;
    volatile uint8_t *memory = (volatile uint8_t *)store;
    for (size_t i = 0; i < sizeof(*store); i++) memory[i] = 0;
}
