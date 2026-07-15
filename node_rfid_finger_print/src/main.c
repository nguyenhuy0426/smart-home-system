#include "access_authorization.h"
#include "access_control_pipeline.h"
#include "ble_mesh_handler.h"
#include "board_pins.h"
#include "credential_store.h"
#include "display_ssd1306.h"
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

/* All sensor/peripheral pins are centralized in board_pins.h. */

static app_config_t s_config;
static credential_store_t s_credential_store;
static bool s_relay_ready;
static bool s_rfid_ready;
static bool s_fingerprint_ready;
/* True only when BLE Mesh provisioning was actually started (unprovisioned
 * node with usable NVS). Provisioning ends with a device restart, so this
 * never needs clearing at runtime. */
static bool s_provisioning_active;

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
    /* Mirror the exact final decision (post relay outcome) on the OLED. */
    display_ssd1306_show_result(kind, decision.result);
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
        display_ssd1306_show_enroll_result(false, false);
        return;
    }
    ESP_LOGI(TAG, "RFID card %s: %s",
            already_enrolled ? "was already enrolled" : "enrolled", hash);
    display_ssd1306_show_enroll_result(true, already_enrolled);
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
        if (s_rfid_ready && gpio_get_level(BOARD_ENROLL_BUTTON_GPIO) == 0) {
            if (enroll_button_pressed_since_ms == 0) {
                enroll_button_pressed_since_ms = now_ms;
            }
            if (enroll_window_deadline_ms == 0 &&
                    now_ms - enroll_button_pressed_since_ms >= BOARD_ENROLL_BUTTON_HOLD_MS) {
                enroll_window_deadline_ms = now_ms + BOARD_ENROLL_WINDOW_MS;
                ESP_LOGI(TAG, "RFID enrollment window open for %d s; present a card",
                        BOARD_ENROLL_WINDOW_MS / 1000);
            }
        } else {
            enroll_button_pressed_since_ms = 0;
        }
        if (enroll_window_deadline_ms != 0 && now_ms > enroll_window_deadline_ms) {
            enroll_window_deadline_ms = 0;
            ESP_LOGI(TAG, "RFID enrollment window closed without a card");
        }

        /* Idle status screen. Cheap to call every iteration: the display module
         * only pushes over I2C when the composed screen actually changes, so a
         * lingering result/enroll screen is replaced here once its 3 s hold and
         * the enrollment window have both elapsed. */
        if (enroll_window_deadline_ms != 0) {
            display_ssd1306_show_enroll_open();
        } else if (s_provisioning_active) {
            /* Keep the truthful provisioning state visible while unprovisioned
             * instead of letting the generic ready screen replace it. */
            display_ssd1306_show_provisioning(s_rfid_ready, s_fingerprint_ready);
        } else {
            display_ssd1306_show_ready(s_rfid_ready, s_fingerprint_ready,
                    wifi_manager_is_connected());
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
    /* Status OLED is optional: a failed bring-up disables the display and never
     * affects the fail-closed access path (all show_* calls become no-ops). */
    (void)display_ssd1306_init();
    display_ssd1306_show_boot();
    s_relay_ready = relay_init(
            BOARD_RELAY_GPIO, BOARD_RELAY_ACTIVE_LEVEL, BOARD_RELAY_UNLOCK_PULSE_MS);
    if (!s_relay_ready) relay_force_off();

    bool nvs_ready = nvs_config_init();
    if (!nvs_ready) {
        /* Fail-closed: without NVS neither the credential store nor the audit
         * sequence can load, so every attempt below is denied. Local readers
         * and the OLED still come up to report that real state. */
        ESP_LOGE(TAG, "NVS initialization failed; relay remains OFF and all "
                "credentials will be denied");
        relay_force_off();
    }
    if (!nvs_ready || !nvs_config_load(&s_config)) {
        memset(&s_config, 0, sizeof(s_config));
    }
    bool provisioned = s_config.provisioned != 0;
    if (!provisioned) relay_force_off();

    /* Local access-control core: the credential store and both readers start
     * unconditionally. Authorization stays fail-closed on the store's own
     * state — a missing/invalid store denies every credential and blocks
     * enrollment, independent of provisioning or gateway availability. */
    if (!credential_store_load(&s_credential_store)) {
        ESP_LOGE(TAG, "Credential key/allowlist unavailable; all credentials will be denied");
    }
    s_rfid_ready = mfrc522_init(BOARD_SPI_HOST, BOARD_RC522_MOSI_GPIO,
            BOARD_RC522_MISO_GPIO, BOARD_RC522_SCK_GPIO, BOARD_RC522_CS_GPIO);

    gpio_config_t enroll_button_config = {
        .pin_bit_mask = 1ULL << BOARD_ENROLL_BUTTON_GPIO,
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    if (gpio_config(&enroll_button_config) != ESP_OK) {
        ESP_LOGW(TAG, "Enrollment button unavailable; RFID enrollment disabled");
    }
    /* tzm1026_init(uart, esp_tx_pin, esp_rx_pin, baud). The sensor's TX_OUT wire
     * lands on the ESP RX pin and its RX_IN wire on the ESP TX pin, so pass the
     * ESP-side TX/RX GPIOs (not the sensor-side labels). */
    s_fingerprint_ready = tzm1026_init(
            BOARD_TZM_UART, BOARD_TZM_ESP_TX_GPIO, BOARD_TZM_ESP_RX_GPIO,
            BOARD_TZM_BAUD_RATE);

    /* Network services start separately, each only when its real
     * prerequisites hold; none of them gate the local access path. Wi-Fi and
     * BLE Mesh remain mutually exclusive by provisioning state, as before. */
    if (provisioned) {
        wifi_manager_init();
        wifi_manager_connect(s_config.wifi_ssid, s_config.wifi_pass);
        ingest_auth_time_sync_start();
    } else if (nvs_ready) {
        ESP_LOGI(TAG, "Node unprovisioned; enabling BLE Mesh provisioning "
                "alongside local access control");
        display_ssd1306_show_provisioning(s_rfid_ready, s_fingerprint_ready);
        s_provisioning_active = true;
        ble_mesh_handler_init();
    }

    if (!s_rfid_ready && !s_fingerprint_ready) {
        ESP_LOGE(TAG, "No credential reader is available; relay remains OFF");
        relay_force_off();
        display_ssd1306_show_no_reader();
        return;
    }
    if (xTaskCreate(access_monitor_task, "access_monitor_task", 6144,
            NULL, 5, NULL) != pdPASS) {
        ESP_LOGE(TAG, "Access monitor task could not start; relay remains OFF");
        relay_force_off();
    }
}
