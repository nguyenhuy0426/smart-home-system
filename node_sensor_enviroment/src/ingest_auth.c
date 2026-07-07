#include "ingest_auth.h"

#include "esp_log.h"
#include "esp_random.h"
#include "esp_sntp.h"
#include "mbedtls/md.h"

#include <stdio.h>
#include <string.h>
#include <time.h>

#define TAG "INGEST_AUTH"
/* 2024-01-01T00:00:00Z: any earlier wall clock means SNTP has not synced. */
#define MIN_VALID_EPOCH 1704067200LL
#define MIN_AUTH_KEY_LENGTH 32

static bool s_sntp_started = false;

void ingest_auth_time_sync_start(void)
{
    if (s_sntp_started) return;
    esp_sntp_setoperatingmode(ESP_SNTP_OPMODE_POLL);
    esp_sntp_setservername(0, "pool.ntp.org");
    esp_sntp_init();
    s_sntp_started = true;
    ESP_LOGI(TAG, "SNTP time synchronization started");
}

bool ingest_auth_time_is_valid(void)
{
    return (long long)time(NULL) >= MIN_VALID_EPOCH;
}

static void to_lower_hex(const unsigned char *input, size_t length, char *output)
{
    static const char digits[] = "0123456789abcdef";
    for (size_t i = 0; i < length; i++) {
        output[2 * i] = digits[input[i] >> 4];
        output[2 * i + 1] = digits[input[i] & 0x0F];
    }
    output[2 * length] = '\0';
}

bool ingest_auth_sign(const char *auth_key,
                      const char *body,
                      size_t body_length,
                      ingest_auth_headers_t *out_headers)
{
    if (auth_key == NULL || body == NULL || out_headers == NULL ||
            strlen(auth_key) < MIN_AUTH_KEY_LENGTH) {
        return false;
    }
    if (!ingest_auth_time_is_valid()) {
        ESP_LOGW(TAG, "Wall clock not yet SNTP-synced; refusing to sign");
        return false;
    }
    memset(out_headers, 0, sizeof(*out_headers));

    long long now = (long long)time(NULL);
    int written = snprintf(out_headers->timestamp,
            sizeof(out_headers->timestamp), "%lld", now);
    if (written <= 0 || (size_t)written >= sizeof(out_headers->timestamp)) {
        return false;
    }

    unsigned char nonce_bytes[INGEST_AUTH_NONCE_HEX_LEN / 2];
    esp_fill_random(nonce_bytes, sizeof(nonce_bytes));
    to_lower_hex(nonce_bytes, sizeof(nonce_bytes), out_headers->nonce);

    char prefix[sizeof(out_headers->timestamp) + sizeof(out_headers->nonce) + 2];
    int prefix_length = snprintf(prefix, sizeof(prefix), "%s\n%s\n",
            out_headers->timestamp, out_headers->nonce);
    if (prefix_length <= 0 || (size_t)prefix_length >= sizeof(prefix)) {
        return false;
    }

    const mbedtls_md_info_t *md_info =
            mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
    if (md_info == NULL) return false;
    unsigned char digest[32];
    mbedtls_md_context_t context;
    mbedtls_md_init(&context);
    bool success = mbedtls_md_setup(&context, md_info, 1) == 0 &&
            mbedtls_md_hmac_starts(&context,
                    (const unsigned char *)auth_key, strlen(auth_key)) == 0 &&
            mbedtls_md_hmac_update(&context,
                    (const unsigned char *)prefix, (size_t)prefix_length) == 0 &&
            mbedtls_md_hmac_update(&context,
                    (const unsigned char *)body, body_length) == 0 &&
            mbedtls_md_hmac_finish(&context, digest) == 0;
    mbedtls_md_free(&context);
    if (!success) return false;
    to_lower_hex(digest, sizeof(digest), out_headers->signature);
    return true;
}
