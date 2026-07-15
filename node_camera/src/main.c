#include "ble_mesh_handler.h"
#include "camera_capture.h"
#include "camera_server.h"
#include "nvs_config.h"
#include "wifi_manager.h"

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include <stdbool.h>
#include <string.h>

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
    ESP_LOGI(TAG, "Initializing local-first camera node");
    bool nvs_ready = nvs_config_init();
    if (!nvs_ready) {
        ESP_LOGE(TAG, "NVS initialization failed; provisioning and network "
                "services disabled, local capture continues");
    }
    bool provisioned = nvs_ready && nvs_config_load(&s_config);
    if (!provisioned) memset(&s_config, 0, sizeof(s_config));

    /* Local core: bring up the physical camera first, independent of
     * provisioning, Wi-Fi, and the gateway. A camera failure is reported and
     * must not block provisioning or network bring-up; camera_server_start()
     * itself refuses to expose endpoints while capture is not ready. No
     * frames are ever fabricated. */
    if (!camera_capture_init()) {
        ESP_LOGE(TAG, "Camera hardware initialization failed; local capture unavailable");
    }

    /* Network services start separately, each only when its real
     * prerequisites hold. Wi-Fi and BLE Mesh remain mutually exclusive by
     * provisioning state, exactly as before. */
    if (provisioned) {
        wifi_manager_init();
        wifi_manager_connect(s_config.wifi_ssid, s_config.wifi_pass);
        if (xTaskCreate(server_start_task, "camera_server_start", 4096, NULL, 5,
                NULL) != pdPASS) {
            ESP_LOGE(TAG, "Could not create camera server startup task");
        }
    } else if (nvs_ready) {
        ESP_LOGW(TAG, "No complete provisioning record; enabling BLE Mesh "
                "provisioning, local capture continues");
        ble_mesh_handler_init();
    }
}
