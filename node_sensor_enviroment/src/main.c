#include "adc_reader.h"
#include "ble_mesh_handler.h"
#include "bme680_i2c.h"
#include "board_pins.h"
#include "dht22.h"
#include "display_st7789.h"
#include "environment_sensor_fusion.h"
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

static app_config_t s_config;
static environment_sensor_fusion_state_t s_fusion_state;

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
    char endpoint[128];
    int endpoint_length = snprintf(endpoint, sizeof(endpoint),
            "http://%s:8080/api/readings", s_config.gateway_ip);
    if (endpoint_length <= 0 || (size_t)endpoint_length >= sizeof(endpoint)) {
        ESP_LOGE(TAG, "Gateway endpoint is invalid");
        vTaskDelete(NULL);
        return;
    }

    while (true) {
        if (!wifi_manager_is_connected()) {
            ESP_LOGW(TAG, "Wi-Fi offline; sample publication skipped");
            /* Offline heartbeat: only the Wi-Fi status line changes; the
             * last real sensor values stay on screen (no placeholders). */
            display_st7789_render_sample(NULL, false);
            vTaskDelay(pdMS_TO_TICKS(TELEMETRY_INTERVAL_MS));
            continue;
        }

        double dht_temperature = NAN;
        double dht_humidity = NAN;
        sensor_status_t dht_status = dht22_read(
                &dht_temperature, &dht_humidity);

        mq7_reading_t mq7_reading;
        (void)mq7_read(&mq7_reading);

        gp2y1014_reading_t gp2y_reading;
        (void)gp2y1014_read(&gp2y_reading);

        bme680_data_t bme680_reading;
        (void)bme680_i2c_read(&bme680_reading);

        uint64_t sequence = 0;
        if (!nvs_config_next_sequence(&sequence)) {
            ESP_LOGE(TAG, "Could not reserve a persistent telemetry sequence");
            vTaskDelay(pdMS_TO_TICKS(TELEMETRY_INTERVAL_MS));
            continue;
        }
        environment_raw_sensor_sample_t sample = {
            .dht22_status = dht_status,
            .dht22_temperature_degc = dht_temperature,
            .dht22_humidity_percent = dht_humidity,
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
            .observed_at_epoch_ms = ingest_auth_time_is_valid()
                    ? (uint64_t)time(NULL) * 1000ULL : 0,
            .observed_at_uptime_ms = (uint64_t)esp_timer_get_time() / 1000,
            .steady_state = dht_status == SENSOR_STATUS_VALID &&
                    bme680_reading.status == SENSOR_STATUS_VALID,
        };

        char json[3072];
        if (!environment_sensor_pipeline_build_reading(
                s_config.node_id, s_config.room_id, &sample,
                &s_fusion_state, json, sizeof(json))) {
            ESP_LOGE(TAG, "Telemetry envelope construction failed");
        } else {
            post_telemetry(endpoint, json);
        }
        /* Local display mirrors the exact sample sent to the pipeline;
         * a failed/absent display is a silent no-op here. */
        display_st7789_render_sample(&sample, true);
        vTaskDelay(pdMS_TO_TICKS(TELEMETRY_INTERVAL_MS));
    }
}

void app_main(void)
{
    ESP_LOGI(TAG, "Initializing fail-closed environmental sensor node");
    if (!nvs_config_init()) {
        ESP_LOGE(TAG, "NVS initialization failed");
        return;
    }
    if (!nvs_config_load(&s_config)) {
        memset(&s_config, 0, sizeof(s_config));
        ESP_LOGI(TAG, "Node is unprovisioned; enabling BLE Mesh provisioning only");
        ble_mesh_handler_init();
        return;
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

    (void)dht22_init(BOARD_DHT22_DATA_GPIO);
    adc_oneshot_unit_handle_t adc_handle = NULL;
    if (adc_reader_create_unit(BOARD_ADC_UNIT, &adc_handle)) {
        /* Validate ADC-capable channels before any analog read is attempted. */
        if (board_pins_is_valid_adc1_channel(BOARD_MQ7_ADC_CHANNEL)) {
            (void)mq7_init(BOARD_ADC_UNIT, adc_handle, BOARD_MQ7_ADC_CHANNEL,
                    &mq7_calibration);
        } else {
            ESP_LOGE(TAG, "MQ7 ADC channel %d is not ADC1-capable; sensor disabled",
                    BOARD_MQ7_ADC_CHANNEL);
        }
        if (board_pins_is_valid_adc1_channel(BOARD_GP2Y_ADC_CHANNEL)) {
            (void)gp2y1014_init(BOARD_GP2Y_LED_GPIO, BOARD_ADC_UNIT, adc_handle,
                    BOARD_GP2Y_ADC_CHANNEL, &gp2y_calibration);
        } else {
            ESP_LOGE(TAG, "GP2Y ADC channel %d is not ADC1-capable; sensor disabled",
                    BOARD_GP2Y_ADC_CHANNEL);
        }
    } else {
        ESP_LOGE(TAG, "ADC1 unit creation failed; MQ7 and GP2Y disabled");
    }
    (void)bme680_i2c_init(BOARD_BME680_SDA_GPIO, BOARD_BME680_SCL_GPIO);
    environment_sensor_fusion_init(&s_fusion_state, 2.0, 3);

    if (!display_st7789_init()) {
        ESP_LOGW(TAG, "ST7789 display unavailable; continuing without local display");
    }

    wifi_manager_init();
    wifi_manager_connect(s_config.wifi_ssid, s_config.wifi_pass);
    ingest_auth_time_sync_start();
    if (xTaskCreate(sample_and_publish_task, "environment_telemetry",
            8192, NULL, 5, NULL) != pdPASS) {
        ESP_LOGE(TAG, "Telemetry task creation failed");
    }
}
