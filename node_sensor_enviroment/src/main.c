#include "nvs_config.h"
#include "wifi_manager.h"
#include "ble_mesh_handler.h"
#include "dht22.h"
#include "mq7.h"
#include "gp2y1014.h"
#include "bme680_i2c.h"
#include "environment_sensor_pipeline.h"
#include "environment_sensor_fusion.h"
#include "esp_log.h"
#include "esp_http_client.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <string.h>

#define TAG "NODE_SENSOR_MAIN"

#define DHT22_GPIO_PIN       GPIO_NUM_4
#define GP2Y_LED_GPIO_PIN    GPIO_NUM_5
#define BME680_SDA_PIN       GPIO_NUM_6
#define BME680_SCL_PIN       GPIO_NUM_7

#define ADC_UNIT             ADC_UNIT_1
#define MQ7_ADC_CHAN         ADC_CHANNEL_1 // GPIO 2 on ESP32-S3
#define GP2Y_ADC_CHAN        ADC_CHANNEL_0 // GPIO 1 on ESP32-S3

static app_config_t s_config;
static environment_sensor_fusion_state_t s_fusion_state;
static unsigned long s_sequence = 0;

static void send_telemetry_task(void *pvParameters)
{
    char json_buf[1024];
    char url_buf[128];
    snprintf(url_buf, sizeof(url_buf), "http://%s:8080/api/readings", s_config.gateway_ip);

    while (1) {
        if (wifi_manager_is_connected()) {
            double temp_dht = 0, hum_dht = 0;
            double co_ppm = 0, pm25 = 0;
            bme680_data_t bme_data;
            memset(&bme_data, 0, sizeof(bme_data));

            // Read sensors
            dht22_read(&temp_dht, &hum_dht);
            co_ppm = mq7_read_co_ppm();
            pm25 = gp2y1014_read_pm25();
            bme680_i2c_read(&bme_data);

            // Construct Raw Sensor Sample
            environment_raw_sensor_sample_t sample = {
                .dht22_temperature_degc = temp_dht,
                .dht22_humidity_percent = hum_dht,
                .cj_temperature_degc = bme_data.temperature,
                .cj_humidity_percent = bme_data.humidity,
                .pressure_hpa = bme_data.pressure,
                .mq7_co_ppm = co_ppm,
                .mq7_phase = ENV_MQ7_HEATER_SAMPLE,
                .pm25_ug_m3 = pm25,
                .eco2_ppm = 400.0, // baseline placeholder
                .tvoc_ppb = 5.0,   // baseline placeholder
                .steady_state = 1,
                .sequence = s_sequence++,
                .observed_at_epoch_ms = esp_timer_get_time() / 1000,
            };

            // Build payload using stub pipeline logic
            if (environment_sensor_pipeline_build_reading(s_config.node_id, s_config.room_id,
                                                           &sample, &s_fusion_state,
                                                           json_buf, sizeof(json_buf))) {
                ESP_LOGI(TAG, "Posting reading: %s", json_buf);

                esp_http_client_config_t http_config = {
                    .url = url_buf,
                    .method = HTTP_METHOD_POST,
                    .timeout_ms = 5000,
                };
                esp_http_client_handle_t client = esp_http_client_init(&http_config);
                if (client) {
                    esp_http_client_set_post_field(client, json_buf, strlen(json_buf));
                    esp_http_client_set_header(client, "Content-Type", "application/json");

                    esp_err_t err = esp_http_client_perform(client);
                    if (err == ESP_OK) {
                        ESP_LOGI(TAG, "Telemetry POST successful. Status: %d",
                                 esp_http_client_get_status_code(client));
                    } else {
                        ESP_LOGE(TAG, "Telemetry POST failed: %s", esp_err_to_name(err));
                    }
                    esp_http_client_cleanup(client);
                } else {
                    ESP_LOGE(TAG, "Failed to initialize HTTP client");
                }
            } else {
                ESP_LOGE(TAG, "Failed to build JSON reading payload");
            }
        } else {
            ESP_LOGW(TAG, "Wi-Fi not connected. Skipping telemetry POST.");
        }

        vTaskDelay(pdMS_TO_TICKS(5000)); // Sample every 5 seconds
    }
}

void app_main(void)
{
    ESP_LOGI(TAG, "Initializing Environmental Sensor Node...");

    // Initialize storage & configurations
    if (!nvs_config_init()) {
        ESP_LOGE(TAG, "NVS flash initialization failed");
        return;
    }

    if (!nvs_config_load(&s_config)) {
        ESP_LOGW(TAG, "Config load failed. Using unprovisioned defaults.");
        memset(&s_config, 0, sizeof(s_config));
    }

    // Initialize physical drivers
    dht22_init(DHT22_GPIO_PIN);
    mq7_init(ADC_UNIT, MQ7_ADC_CHAN);
    gp2y1014_init(GP2Y_LED_GPIO_PIN, ADC_UNIT, GP2Y_ADC_CHAN);
    bme680_i2c_init(BME680_SDA_PIN, BME680_SCL_PIN);

    // Initialize fusion logic state
    environment_sensor_fusion_init(&s_fusion_state, 2.0, 3);

    if (s_config.provisioned) {
        ESP_LOGI(TAG, "Device configured. Connecting to Wi-Fi SSID: %s", s_config.wifi_ssid);
        wifi_manager_init();
        wifi_manager_connect(s_config.wifi_ssid, s_config.wifi_pass);
        xTaskCreate(send_telemetry_task, "telemetry_task", 4096, NULL, 5, NULL);
    } else {
        ESP_LOGI(TAG, "Device unconfigured. Entering BLE Mesh provisioning mode.");
        ble_mesh_handler_init();
    }
}