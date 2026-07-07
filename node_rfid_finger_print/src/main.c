#include "access_authorization.h"
#include "access_control_pipeline.h"
#include "ble_mesh_handler.h"
#include "credential_store.h"
#include "ingest_auth.h"
#include "mfrc522.h"
#include "nvs_config.h"
#include "relay.h"
#include "tzm1026.h"
#include "wifi_manager.h"

#include "driver/gpio.h"
#include "driver/uart.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include <string.h>
#include <time.h>

#define TAG "ACCESS_NODE_MAIN"

#define RELAY_GPIO_PIN GPIO_NUM_4
#define RELAY_ACTIVE_LEVEL 1
#define RELAY_UNLOCK_PULSE_MS 5000

#define RC522_MOSI_PIN GPIO_NUM_11
#define RC522_MISO_PIN GPIO_NUM_13
#define RC522_SCK_PIN GPIO_NUM_12
#define RC522_CS_PIN GPIO_NUM_10
#define SPI_HOST_ID SPI2_HOST

#define TZM_UART_NUM UART_NUM_1
#define TZM_TX_PIN GPIO_NUM_17
#define TZM_RX_PIN GPIO_NUM_18
#define TZM_BAUD_RATE 57600

/* On-board BOOT button; hold to open the RFID enrollment window. */
#define ENROLL_BUTTON_GPIO GPIO_NUM_0
#define ENROLL_BUTTON_HOLD_MS 3000
#define ENROLL_WINDOW_MS 30000

static app_config_t s_config;
static credential_store_t s_credential_store;
static bool s_relay_ready;
static bool s_rfid_ready;
static bool s_fingerprint_ready;

static void post_access_event(const char *json_payload)
{
    if (json_payload == NULL || !wifi_manager_is_connected()) {
        ESP_LOGW(TAG, "Wi-Fi offline; access event could not be posted");
        return;
    }

    char url[128];
    int written = snprintf(url, sizeof(url), "http://%s:8080/api/readings",
            s_config.gateway_ip);
    if (written <= 0 || (size_t)written >= sizeof(url)) return;
    size_t payload_length = strlen(json_payload);
    ingest_auth_headers_t auth_headers;
    if (!ingest_auth_sign(s_config.auth_key, json_payload, payload_length,
            &auth_headers)) {
        ESP_LOGW(TAG, "Skipping access event POST: request signing unavailable");
        return;
    }
    esp_http_client_config_t configuration = {
        .url = url,
        .method = HTTP_METHOD_POST,
        .timeout_ms = 5000,
    };
    esp_http_client_handle_t client = esp_http_client_init(&configuration);
    if (client == NULL) return;
    esp_http_client_set_post_field(client, json_payload, payload_length);
    esp_http_client_set_header(client, "Content-Type", "application/json");
    esp_http_client_set_header(client, "X-Auth-Timestamp", auth_headers.timestamp);
    esp_http_client_set_header(client, "X-Auth-Nonce", auth_headers.nonce);
    esp_http_client_set_header(client, "X-Auth-Signature", auth_headers.signature);
    esp_err_t error = esp_http_client_perform(client);
    int status = error == ESP_OK ? esp_http_client_get_status_code(client) : 0;
    if (error != ESP_OK || status < 200 || status >= 300) {
        ESP_LOGE(TAG, "Access event POST failed: transport=%s status=%d",
                esp_err_to_name(error), status);
    }
    esp_http_client_cleanup(client);
}

static void process_attempt(access_credential_kind_t kind,
                            access_sensor_status_t sensor_status,
                            const char *credential_hash,
                            bool allowlisted,
                            int confidence)
{
    access_authorization_decision_t decision =
            access_authorization_evaluate(sensor_status, allowlisted);
    uint32_t sequence;
    if (!nvs_config_next_access_sequence(&sequence)) {
        ESP_LOGE(TAG, "Could not persist access sequence; relay remains OFF");
        relay_force_off();
        return;
    }

    access_control_attempt_t attempt = {
        .credential_kind = kind,
        .hashed_credential_id = credential_hash,
        .result = decision.result,
        .confidence = confidence,
        .unlock_commanded = decision.should_unlock,
        .sequence = sequence,
        .observed_at_epoch_ms = ingest_auth_time_is_valid()
                ? (uint64_t)time(NULL) * 1000ULL : 0,
        .observed_at_uptime_ms = esp_timer_get_time() / 1000,
    };
    char event[1024];
    if (!access_control_pipeline_build_event(&attempt, event, sizeof(event))) {
        ESP_LOGE(TAG, "Could not build access audit event; relay remains OFF");
        relay_force_off();
        return;
    }
    if (decision.should_unlock && (!s_relay_ready || !relay_trigger_unlock())) {
        decision.result = ACCESS_RESULT_SENSOR_ERROR;
        attempt.result = decision.result;
        attempt.unlock_commanded = false;
        relay_force_off();
        if (!access_control_pipeline_build_event(&attempt, event, sizeof(event))) return;
    }
    ESP_LOGI(TAG, "Access attempt result: %s",
            decision.result == ACCESS_RESULT_GRANTED ? "granted" :
            (decision.result == ACCESS_RESULT_DENIED ? "denied" : "sensor_error"));
    post_access_event(event);
}

static void enroll_presented_card(const uint8_t *uid, uint8_t uid_length)
{
    relay_force_off();
    char hash[ACCESS_HASH_STRING_SIZE] = {0};
    bool already_enrolled = false;
    if (!credential_store_enroll_rfid(&s_credential_store, uid, uid_length,
            hash, sizeof(hash), &already_enrolled)) {
        ESP_LOGE(TAG, "RFID enrollment failed; allowlist unchanged");
        return;
    }
    ESP_LOGI(TAG, "RFID card %s: %s",
            already_enrolled ? "was already enrolled" : "enrolled", hash);
}

static void access_monitor_task(void *context)
{
    (void)context;
    int64_t last_rfid_error_log_ms = 0;
    int64_t last_fingerprint_error_log_ms = 0;
    int64_t enroll_button_pressed_since_ms = 0;
    int64_t enroll_window_deadline_ms = 0;
    while (true) {
        bool handled_credential = false;

        /* Enrollment window: hold BOOT >= 3 s, then present the new card. */
        int64_t now_ms = esp_timer_get_time() / 1000;
        if (s_rfid_ready && gpio_get_level(ENROLL_BUTTON_GPIO) == 0) {
            if (enroll_button_pressed_since_ms == 0) {
                enroll_button_pressed_since_ms = now_ms;
            }
            if (enroll_window_deadline_ms == 0 &&
                    now_ms - enroll_button_pressed_since_ms >= ENROLL_BUTTON_HOLD_MS) {
                enroll_window_deadline_ms = now_ms + ENROLL_WINDOW_MS;
                ESP_LOGI(TAG, "RFID enrollment window open for %d s; present a card",
                        ENROLL_WINDOW_MS / 1000);
            }
        } else {
            enroll_button_pressed_since_ms = 0;
        }
        if (enroll_window_deadline_ms != 0 && now_ms > enroll_window_deadline_ms) {
            enroll_window_deadline_ms = 0;
            ESP_LOGI(TAG, "RFID enrollment window closed without a card");
        }

        if (s_rfid_ready) {
            uint8_t uid[10];
            uint8_t uid_length = 0;
            mfrc522_status_t status = mfrc522_read_card_uid(
                    uid, sizeof(uid), &uid_length);
            if (status == MFRC522_UID_OK && enroll_window_deadline_ms != 0) {
                enroll_window_deadline_ms = 0;
                enroll_presented_card(uid, uid_length);
                handled_credential = true;
            } else if (status == MFRC522_UID_OK) {
                char hash[ACCESS_HASH_STRING_SIZE] = {0};
                bool allowlisted = credential_store_authorize_rfid(
                        &s_credential_store, uid, uid_length, hash, sizeof(hash));
                process_attempt(ACCESS_CREDENTIAL_RFID, ACCESS_SENSOR_CREDENTIAL,
                        hash[0] == '\0' ? NULL : hash, allowlisted, 100);
                handled_credential = true;
            } else if (status < MFRC522_NO_CARD) {
                int64_t now_ms = esp_timer_get_time() / 1000;
                if (now_ms - last_rfid_error_log_ms >= 5000) {
                    ESP_LOGE(TAG, "RFID reader error: %d", status);
                    last_rfid_error_log_ms = now_ms;
                }
            }
        }

        if (!handled_credential && s_fingerprint_ready) {
            uint16_t page_id = 0;
            uint16_t match_score = 0;
            tzm1026_scan_status_t status = tzm1026_scan_finger(&page_id, &match_score);
            if (status == TZM1026_SCAN_MATCH) {
                char hash[ACCESS_HASH_STRING_SIZE] = {0};
                bool allowlisted = credential_store_authorize_fingerprint(
                        &s_credential_store, page_id, hash, sizeof(hash));
                process_attempt(ACCESS_CREDENTIAL_FINGERPRINT, ACCESS_SENSOR_CREDENTIAL,
                        hash[0] == '\0' ? NULL : hash, allowlisted, match_score);
                handled_credential = true;
            } else if (status == TZM1026_SCAN_NO_MATCH) {
                process_attempt(ACCESS_CREDENTIAL_FINGERPRINT, ACCESS_SENSOR_NO_MATCH,
                        NULL, false, 0);
                handled_credential = true;
            } else if (status < TZM1026_SCAN_NO_FINGER) {
                int64_t now_ms = esp_timer_get_time() / 1000;
                if (now_ms - last_fingerprint_error_log_ms >= 5000) {
                    ESP_LOGE(TAG, "Fingerprint reader error: %d", status);
                    last_fingerprint_error_log_ms = now_ms;
                }
            }
        }

        vTaskDelay(pdMS_TO_TICKS(handled_credential ? 3000 : 200));
    }
}

void app_main(void)
{
    ESP_LOGI(TAG, "Initializing fail-closed access node");
    s_relay_ready = relay_init(
            RELAY_GPIO_PIN, RELAY_ACTIVE_LEVEL, RELAY_UNLOCK_PULSE_MS);
    if (!s_relay_ready) relay_force_off();

    if (!nvs_config_init()) {
        ESP_LOGE(TAG, "NVS initialization failed; relay remains OFF");
        relay_force_off();
        return;
    }
    if (!nvs_config_load(&s_config)) memset(&s_config, 0, sizeof(s_config));
    if (!s_config.provisioned) {
        relay_force_off();
        ESP_LOGI(TAG, "Node unprovisioned; starting BLE Mesh provisioning only");
        ble_mesh_handler_init();
        return;
    }

    if (!credential_store_load(&s_credential_store)) {
        ESP_LOGE(TAG, "Credential key/allowlist unavailable; all credentials will be denied");
    }
    s_rfid_ready = mfrc522_init(SPI_HOST_ID, RC522_MOSI_PIN, RC522_MISO_PIN,
            RC522_SCK_PIN, RC522_CS_PIN);

    gpio_config_t enroll_button_config = {
        .pin_bit_mask = 1ULL << ENROLL_BUTTON_GPIO,
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    if (gpio_config(&enroll_button_config) != ESP_OK) {
        ESP_LOGW(TAG, "Enrollment button unavailable; RFID enrollment disabled");
    }
    s_fingerprint_ready = tzm1026_init(
            TZM_UART_NUM, TZM_TX_PIN, TZM_RX_PIN, TZM_BAUD_RATE);

    wifi_manager_init();
    wifi_manager_connect(s_config.wifi_ssid, s_config.wifi_pass);
    ingest_auth_time_sync_start();
    if (!s_rfid_ready && !s_fingerprint_ready) {
        ESP_LOGE(TAG, "No credential reader is available; relay remains OFF");
        relay_force_off();
        return;
    }
    if (xTaskCreate(access_monitor_task, "access_monitor_task", 6144,
            NULL, 5, NULL) != pdPASS) {
        ESP_LOGE(TAG, "Access monitor task could not start; relay remains OFF");
        relay_force_off();
    }
}
