#include "adc_reader.h"
#include "ble_mesh_handler.h"
#include "bme680_i2c.h"
#include "board_pins.h"
#include "display_st7789.h"
#include "environment_sensor_pipeline.h"
#include "gp2y1014.h"
#include "ingest_auth.h"
#include "mq7.h"
#include "nvs_config.h"
#include "wifi_manager.h"

#include "driver/gpio.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include <math.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#define TAG "NODE_SENSOR_MAIN"

/* All sensor/peripheral pins are centralized in board_pins.h. */
#define TELEMETRY_INTERVAL_MS 5000
/* Physical UI review mode: alternate the implemented dark and light themes
 * every 30 seconds so both can be inspected without adding a fake button or
 * repurposing an unwired GPIO. Sensor content remains unchanged. */
#define DISPLAY_THEME_PREVIEW_INTERVAL_MS UINT64_C(30000)

static app_config_t s_config;
static bool s_nvs_ready;
static bool s_provisioned;

static void post_telemetry(const char *url, const char *json)
{
    if (url == NULL || json == NULL) return;
    size_t json_length = strlen(json);
    ingest_auth_headers_t auth_headers;
    if (!ingest_auth_sign(s_config.auth_key, json, json_length,
            &auth_headers)) {
        ESP_LOGW(TAG, "Skipping telemetry send: request signing unavailable");
        return;
    }
    esp_http_client_config_t configuration = {
        .url = url,
        .method = HTTP_METHOD_POST,
        .timeout_ms = 5000,
    };
    esp_http_client_handle_t client = esp_http_client_init(&configuration);
    if (client == NULL) {
        ESP_LOGE(TAG, "Could not initialize telemetry HTTP client");
        return;
    }
    esp_http_client_set_post_field(client, json, json_length);
    esp_http_client_set_header(client, "Content-Type", "application/json");
    esp_http_client_set_header(client, "X-Auth-Timestamp", auth_headers.timestamp);
    esp_http_client_set_header(client, "X-Auth-Nonce", auth_headers.nonce);
    esp_http_client_set_header(client, "X-Auth-Signature", auth_headers.signature);
    esp_err_t error = esp_http_client_perform(client);
    int status_code = error == ESP_OK
            ? esp_http_client_get_status_code(client) : 0;
    if (error != ESP_OK || status_code < 200 || status_code >= 300) {
        ESP_LOGE(TAG, "Telemetry POST failed: transport=%s status=%d",
                esp_err_to_name(error), status_code);
    }
    esp_http_client_cleanup(client);
}

static void sample_and_publish_task(void *context)
{
    (void)context;
    /* Publishing prerequisites are resolved once: a gateway endpoint exists
     * only when the node holds a valid provisioning record. Local sampling
     * and the display below never depend on it. */
    char endpoint[128] = {0};
    bool publish_configured = false;
    if (s_provisioned) {
        int endpoint_length = snprintf(endpoint, sizeof(endpoint),
                "http://%s:8080/api/readings", s_config.gateway_ip);
        publish_configured = endpoint_length > 0 &&
                (size_t)endpoint_length < sizeof(endpoint);
        if (!publish_configured) {
            ESP_LOGE(TAG, "Gateway endpoint is invalid; publishing disabled, "
                    "local sampling continues");
        }
    }

    while (true) {
        /* Local core: the real sensors are sampled every cycle regardless of
         * provisioning, Wi-Fi, or gateway availability. */
        mq7_reading_t mq7_reading;
        (void)mq7_read(&mq7_reading);

        /* GP2Y is physically absent and has no ADC assignment. GPIO2/ADC1_CH1
         * belongs exclusively to MQ7, so the dust driver is never called. */
        gp2y1014_reading_t gp2y_reading;
        memset(&gp2y_reading, 0, sizeof(gp2y_reading));
        gp2y_reading.pm25_ug_m3 = NAN;
        gp2y_reading.status = SENSOR_STATUS_NOT_CONNECTED;

        bme680_data_t bme680_reading;
        (void)bme680_i2c_read(&bme680_reading);

        /* A persistent sequence is a publishing prerequisite only; when the
         * reservation fails the local sample still reaches the display. */
        uint64_t sequence = 0;
        bool sequence_reserved = s_nvs_ready &&
                nvs_config_next_sequence(&sequence);
        if (s_nvs_ready && !sequence_reserved) {
            ESP_LOGE(TAG, "Could not reserve a persistent telemetry sequence");
        }
        bool clock_valid = ingest_auth_time_is_valid();
        environment_raw_sensor_sample_t sample = {
            .bme680_status = bme680_reading.status,
            .bme680_gas_status = bme680_reading.gas_status,
            .bme680_temperature_degc = bme680_reading.temperature_degc,
            .bme680_humidity_percent = bme680_reading.humidity_percent,
            .pressure_hpa = bme680_reading.pressure_hpa,
            .gas_resistance_ohm = bme680_reading.gas_resistance_ohm,
            .mq7_status = mq7_reading.status,
            .mq7_phase = mq7_reading.phase,
            .mq7_co_ppm = mq7_reading.co_ppm,
            .mq7_adc_millivolts = mq7_reading.adc_millivolts,
            .gp2y_status = gp2y_reading.status,
            .pm25_ug_m3 = gp2y_reading.pm25_ug_m3,
            .gp2y_adc_millivolts = gp2y_reading.adc_millivolts,
            .sequence = sequence,
            .observed_at_epoch_ms = clock_valid
                    ? (uint64_t)time(NULL) * 1000ULL : 0,
            .observed_at_uptime_ms = (uint64_t)esp_timer_get_time() / 1000,
        };

        /* Runtime evidence: one consolidated line per cycle with the real
         * status and values of every local sensor, independent of any
         * network path. NAN prints as "nan" for non-valid channels. */
        bool ble_mesh_ready = ble_mesh_handler_is_ready();
        ESP_LOGI(TAG, "sensors: BME680[%s T=%.1fC RH=%.1f%% P=%.1fhPa "
                "gas=%s R=%.0fohm] "
                "MQ7[gpio=%d ch=%d %s phase=%s %umV CO=%.1fppm] "
                "BLE[ready=%s] TIME[synced=%s]",
                sensor_status_name(bme680_reading.status),
                bme680_reading.temperature_degc,
                bme680_reading.humidity_percent,
                bme680_reading.pressure_hpa,
                sensor_status_name(bme680_reading.gas_status),
                bme680_reading.gas_resistance_ohm,
                BOARD_MQ7_ADC_GPIO, BOARD_MQ7_ADC_CHANNEL,
                sensor_status_name(mq7_reading.status),
                mq7_heater_phase_name(mq7_reading.phase),
                (unsigned)mq7_reading.adc_millivolts,
                mq7_reading.co_ppm, ble_mesh_ready ? "yes" : "no",
                clock_valid ? "yes" : "no");

        /* Network path: publish only when the node is provisioned, the
         * endpoint is valid, Wi-Fi is really associated, and a persistent
         * sequence was reserved. Any failure here never blocks the local
         * display update below. */
        bool wifi_connected = wifi_manager_is_connected();
        if (publish_configured && wifi_connected && sequence_reserved) {
            char json[3072];
            if (!environment_sensor_pipeline_build_reading(
                    s_config.node_id, s_config.room_id, &sample,
                    json, sizeof(json))) {
                ESP_LOGE(TAG, "Telemetry envelope construction failed");
            } else {
                post_telemetry(endpoint, json);
            }
        } else if (publish_configured && !wifi_connected) {
            ESP_LOGW(TAG, "Wi-Fi offline; sample publication skipped");
        }
        /* Local display mirrors the exact sample just taken; a failed/absent
         * display is a silent no-op here. */
        display_st7789_theme_t theme =
                (sample.observed_at_uptime_ms /
                 DISPLAY_THEME_PREVIEW_INTERVAL_MS) % 2 == 0
                ? DISPLAY_ST7789_THEME_DARK : DISPLAY_ST7789_THEME_LIGHT;
        display_st7789_set_theme(theme);
        display_st7789_render_sample(
                &sample, wifi_connected, s_provisioned, ble_mesh_ready);
        vTaskDelay(pdMS_TO_TICKS(TELEMETRY_INTERVAL_MS));
    }
}

void app_main(void)
{
    ESP_LOGI(TAG, "Initializing local-first environmental sensor node");
    s_nvs_ready = nvs_config_init();
    if (!s_nvs_ready) {
        ESP_LOGE(TAG, "NVS initialization failed; provisioning, publishing and "
                "stored calibration unavailable, local sensing continues");
    }
    s_provisioned = s_nvs_ready && nvs_config_load(&s_config);
    if (!s_provisioned) {
        memset(&s_config, 0, sizeof(s_config));
        ESP_LOGW(TAG, "Node is unprovisioned; local sensing and display run "
                "without publishing");
    }

    sensor_calibration_config_t stored_calibration;
    (void)nvs_config_load_sensor_calibration(&stored_calibration);
    mq7_calibration_t mq7_calibration = {
        .valid = stored_calibration.mq7_valid,
        .sensor_supply_mv = stored_calibration.mq7_sensor_supply_mv,
        .adc_divider_ratio = stored_calibration.mq7_adc_divider_ratio,
        .load_resistor_ohm = stored_calibration.mq7_load_resistor_ohm,
        .clean_air_resistance_ohm = stored_calibration.mq7_clean_air_resistance_ohm,
        .curve_a = stored_calibration.mq7_curve_a,
        .curve_b = stored_calibration.mq7_curve_b,
    };
    gp2y1014_calibration_t gp2y_calibration = {
        .valid = stored_calibration.gp2y_valid,
        .adc_divider_ratio = stored_calibration.gp2y_adc_divider_ratio,
        .clean_air_voltage_mv = stored_calibration.gp2y_clean_air_voltage_mv,
        .sensitivity_mv_per_ug_m3 =
                stored_calibration.gp2y_sensitivity_mv_per_ug_m3,
    };

    board_pins_report_conflicts();

    adc_oneshot_unit_handle_t adc_handle = NULL;
    if (adc_reader_create_unit(BOARD_ADC_UNIT, &adc_handle)) {
        /* Validate ADC-capable channels before any analog read is attempted. */
        if (board_pins_is_valid_adc1_channel(BOARD_MQ7_ADC_CHANNEL) &&
                board_pins_adc1_channel_for_gpio(BOARD_MQ7_ADC_GPIO) ==
                BOARD_MQ7_ADC_CHANNEL) {
            (void)mq7_init(BOARD_ADC_UNIT, adc_handle, BOARD_MQ7_ADC_CHANNEL,
                    &mq7_calibration);
        } else {
            ESP_LOGE(TAG, "MQ7 GPIO%d/ADC1_CH%d mapping is invalid; sensor disabled",
                    BOARD_MQ7_ADC_GPIO, BOARD_MQ7_ADC_CHANNEL);
        }
        /* GP2Y1014 is not physically wired and has no ADC assignment.
         * GPIO6 stays undriven; GPIO2/ADC1_CH1 remains exclusively MQ7. */
        (void)gp2y_calibration;
        ESP_LOGW(TAG, "GP2Y1014 not connected (BOARD_GP2Y_CONNECTED=0); "
                "driver disabled and PM2.5 reported as not_connected");
    } else {
        ESP_LOGE(TAG, "ADC1 unit creation failed; MQ7 disabled "
                "(GP2Y already disabled)");
    }
    (void)bme680_i2c_init(BOARD_BME680_SDA_GPIO, BOARD_BME680_SCL_GPIO);

    if (!display_st7789_init()) {
        ESP_LOGW(TAG, "ST7789 display unavailable; continuing without local display");
    }

    /* Local core functionality starts unconditionally: sampling and the
     * display never wait for provisioning, Wi-Fi, or the gateway.
     * Pinned to core 1 while the BT controller (BLE Mesh on an unprovisioned
     * node) remains on the radio side/core 0. */
    if (xTaskCreatePinnedToCore(sample_and_publish_task,
            "environment_telemetry", 8192, NULL, 5, NULL, 1) != pdPASS) {
        ESP_LOGE(TAG, "Telemetry task creation failed");
    }

    /* Network services start separately, each only when its real
     * prerequisites hold. Wi-Fi and BLE Mesh remain mutually exclusive by
     * provisioning state, exactly as before. */
    if (s_provisioned) {
        wifi_manager_init();
        wifi_manager_connect(s_config.wifi_ssid, s_config.wifi_pass);
        ingest_auth_time_sync_start();
    } else if (s_nvs_ready) {
        ESP_LOGI(TAG, "Enabling BLE Mesh provisioning alongside local sensing");
        ble_mesh_handler_init();
    }
}
