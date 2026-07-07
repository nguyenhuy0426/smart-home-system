#include "nvs_config.h"

#include "provisioning_parser.h"
#include "esp_log.h"
#include "nvs.h"
#include "nvs_flash.h"

#include <string.h>

#define TAG "NVS_CONFIG"
#define CONFIG_NAMESPACE "storage"

int nvs_config_init(void)
{
    esp_err_t error = nvs_flash_init();
    if (error == ESP_ERR_NVS_NO_FREE_PAGES ||
            error == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        error = nvs_flash_erase();
        if (error == ESP_OK) error = nvs_flash_init();
    }
    if (error != ESP_OK) ESP_LOGE(TAG, "NVS initialization failed: %s",
                                  esp_err_to_name(error));
    return error == ESP_OK;
}

static bool get_required_string(nvs_handle_t handle, const char *key,
                                char *output, size_t capacity)
{
    size_t length = capacity;
    esp_err_t error = nvs_get_str(handle, key, output, &length);
    return error == ESP_OK && length > 1 && length <= capacity &&
            output[length - 1] == '\0';
}

int nvs_config_load(app_config_t *config)
{
    if (config == NULL) return 0;
    memset(config, 0, sizeof(*config));
    nvs_handle_t handle;
    esp_err_t error = nvs_open(CONFIG_NAMESPACE, NVS_READONLY, &handle);
    if (error != ESP_OK) return 0;

    bool loaded = get_required_string(handle, NVS_KEY_WIFI_SSID,
                    config->wifi_ssid, sizeof(config->wifi_ssid)) &&
            get_required_string(handle, NVS_KEY_WIFI_PASS,
                    config->wifi_pass, sizeof(config->wifi_pass)) &&
            get_required_string(handle, NVS_KEY_GATEWAY_IP,
                    config->gateway_ip, sizeof(config->gateway_ip)) &&
            nvs_get_u16(handle, NVS_KEY_GATEWAY_PORT,
                    &config->gateway_port) == ESP_OK &&
            get_required_string(handle, NVS_KEY_NODE_ID,
                    config->node_id, sizeof(config->node_id)) &&
            get_required_string(handle, NVS_KEY_ROOM_ID,
                    config->room_id, sizeof(config->room_id)) &&
            nvs_get_u16(handle, NVS_KEY_SNAPSHOT_PORT,
                    &config->snapshot_port) == ESP_OK &&
            nvs_get_u16(handle, NVS_KEY_RTSP_PORT,
                    &config->rtsp_port) == ESP_OK &&
            get_required_string(handle, NVS_KEY_AUTH_KEY,
                    config->auth_key, sizeof(config->auth_key)) &&
            nvs_get_u8(handle, NVS_KEY_PROVISIONED,
                    &config->provisioned) == ESP_OK;
    nvs_close(handle);
    if (!loaded || !app_config_is_valid(config)) {
        memset(config, 0, sizeof(*config));
        return 0;
    }
    return 1;
}

static bool set_string(nvs_handle_t handle, const char *key, const char *value)
{
    return nvs_set_str(handle, key, value) == ESP_OK;
}

int nvs_config_save(const app_config_t *config)
{
    if (!app_config_is_valid(config)) return 0;
    nvs_handle_t handle;
    esp_err_t error = nvs_open(CONFIG_NAMESPACE, NVS_READWRITE, &handle);
    if (error != ESP_OK) return 0;
    bool stored = set_string(handle, NVS_KEY_WIFI_SSID, config->wifi_ssid) &&
            set_string(handle, NVS_KEY_WIFI_PASS, config->wifi_pass) &&
            set_string(handle, NVS_KEY_GATEWAY_IP, config->gateway_ip) &&
            nvs_set_u16(handle, NVS_KEY_GATEWAY_PORT,
                    config->gateway_port) == ESP_OK &&
            set_string(handle, NVS_KEY_NODE_ID, config->node_id) &&
            set_string(handle, NVS_KEY_ROOM_ID, config->room_id) &&
            nvs_set_u16(handle, NVS_KEY_SNAPSHOT_PORT,
                    config->snapshot_port) == ESP_OK &&
            nvs_set_u16(handle, NVS_KEY_RTSP_PORT,
                    config->rtsp_port) == ESP_OK &&
            set_string(handle, NVS_KEY_AUTH_KEY, config->auth_key) &&
            nvs_set_u8(handle, NVS_KEY_PROVISIONED, 1) == ESP_OK;
    if (stored) stored = nvs_commit(handle) == ESP_OK;
    if (!stored) (void)nvs_erase_all(handle);
    nvs_close(handle);
    if (!stored) ESP_LOGE(TAG, "Configuration transaction failed");
    return stored;
}

int nvs_config_reset(void)
{
    nvs_handle_t handle;
    if (nvs_open(CONFIG_NAMESPACE, NVS_READWRITE, &handle) != ESP_OK) return 0;
    bool reset = nvs_erase_all(handle) == ESP_OK && nvs_commit(handle) == ESP_OK;
    nvs_close(handle);
    return reset;
}
