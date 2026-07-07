#include "ble_mesh_handler.h"
#include "camera_capture.h"
#include "camera_server.h"
#include "nvs_config.h"
#include "wifi_manager.h"

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#define TAG "NODE_CAMERA_MAIN"

static app_config_t s_config;

static void server_start_task(void *context)
{
    (void)context;
    while (!wifi_manager_is_connected()) {
        ESP_LOGW(TAG, "Waiting for Wi-Fi before exposing camera services");
        vTaskDelay(pdMS_TO_TICKS(2000));
    }
    if (!camera_server_start(&s_config)) {
        ESP_LOGE(TAG, "Camera services failed to start; no endpoint is advertised");
    }
    vTaskDelete(NULL);
}

void app_main(void)
{
    ESP_LOGI(TAG, "Initializing fail-closed camera node");
    if (!nvs_config_init()) return;

    if (!nvs_config_load(&s_config)) {
        ESP_LOGW(TAG, "No complete provisioning record; camera and Wi-Fi remain off");
        ble_mesh_handler_init();
        return;
    }

    if (!camera_capture_init()) {
        ESP_LOGE(TAG, "Camera hardware initialization failed; services will not start");
        return;
    }

    wifi_manager_init();
    wifi_manager_connect(s_config.wifi_ssid, s_config.wifi_pass);
    if (xTaskCreate(server_start_task, "camera_server_start", 4096, NULL, 5,
            NULL) != pdPASS) {
        ESP_LOGE(TAG, "Could not create camera server startup task");
    }
}
