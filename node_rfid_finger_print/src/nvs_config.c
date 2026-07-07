#include "nvs_config.h"
#include "nvs_flash.h"
#include "nvs.h"
#include "esp_log.h"
#include <stdbool.h>
#include <string.h>

#define TAG "NVS_CONFIG"
#define CONFIG_NAMESPACE "storage"

int nvs_config_init(void)
{
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    return err == ESP_OK ? 1 : 0;
}

int nvs_config_load(app_config_t *config)
{
    nvs_handle_t handle;
    esp_err_t err;

    if (config == NULL) {
        return 0;
    }

    memset(config, 0, sizeof(app_config_t));

    err = nvs_open(CONFIG_NAMESPACE, NVS_READONLY, &handle);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "NVS namespace %s does not exist yet (not configured)", CONFIG_NAMESPACE);
        return 0;
    }

    size_t length = sizeof(config->wifi_ssid);
    bool loaded = nvs_get_str(handle, NVS_KEY_WIFI_SSID,
            config->wifi_ssid, &length) == ESP_OK;
    length = sizeof(config->wifi_pass);
    loaded = loaded && nvs_get_str(handle, NVS_KEY_WIFI_PASS,
            config->wifi_pass, &length) == ESP_OK;
    length = sizeof(config->gateway_ip);
    loaded = loaded && nvs_get_str(handle, NVS_KEY_GATEWAY_IP,
            config->gateway_ip, &length) == ESP_OK;
    length = sizeof(config->node_id);
    loaded = loaded && nvs_get_str(handle, NVS_KEY_NODE_ID,
            config->node_id, &length) == ESP_OK;
    length = sizeof(config->room_id);
    loaded = loaded && nvs_get_str(handle, NVS_KEY_ROOM_ID,
            config->room_id, &length) == ESP_OK;
    length = sizeof(config->auth_key);
    loaded = loaded && nvs_get_str(handle, NVS_KEY_AUTH_KEY,
            config->auth_key, &length) == ESP_OK;
    loaded = loaded && nvs_get_u8(handle, NVS_KEY_PROVISIONED,
            &config->provisioned) == ESP_OK;

    nvs_close(handle);
    return loaded ? 1 : 0;
}

int nvs_config_save(const app_config_t *config)
{
    nvs_handle_t handle;
    esp_err_t err;

    if (config == NULL) {
        return 0;
    }

    err = nvs_open(CONFIG_NAMESPACE, NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Failed to open NVS for writing: %s", esp_err_to_name(err));
        return 0;
    }

    bool saved = nvs_set_str(handle, NVS_KEY_WIFI_SSID, config->wifi_ssid) == ESP_OK &&
            nvs_set_str(handle, NVS_KEY_WIFI_PASS, config->wifi_pass) == ESP_OK &&
            nvs_set_str(handle, NVS_KEY_GATEWAY_IP, config->gateway_ip) == ESP_OK &&
            nvs_set_str(handle, NVS_KEY_NODE_ID, config->node_id) == ESP_OK &&
            nvs_set_str(handle, NVS_KEY_ROOM_ID, config->room_id) == ESP_OK &&
            nvs_set_str(handle, NVS_KEY_AUTH_KEY, config->auth_key) == ESP_OK &&
            nvs_set_u8(handle, NVS_KEY_PROVISIONED, config->provisioned) == ESP_OK;

    err = saved ? nvs_commit(handle) : ESP_FAIL;
    nvs_close(handle);

    if (err != ESP_OK) {
        ESP_LOGE(TAG, "NVS commit failed: %s", esp_err_to_name(err));
        return 0;
    }

    ESP_LOGI(TAG, "Configuration successfully saved to NVS.");
    return 1;
}

int nvs_config_next_access_sequence(uint32_t *out_sequence)
{
    if (out_sequence == NULL) return 0;
    nvs_handle_t handle;
    esp_err_t error = nvs_open(CONFIG_NAMESPACE, NVS_READWRITE, &handle);
    if (error != ESP_OK) return 0;
    uint32_t sequence = 0;
    error = nvs_get_u32(handle, NVS_KEY_ACCESS_SEQ, &sequence);
    if (error != ESP_OK && error != ESP_ERR_NVS_NOT_FOUND) {
        nvs_close(handle);
        return 0;
    }
    if (sequence == UINT32_MAX ||
            nvs_set_u32(handle, NVS_KEY_ACCESS_SEQ, sequence + 1) != ESP_OK ||
            nvs_commit(handle) != ESP_OK) {
        nvs_close(handle);
        return 0;
    }
    nvs_close(handle);
    *out_sequence = sequence;
    return 1;
}

int nvs_config_reset(void)
{
    nvs_handle_t handle;
    esp_err_t err = nvs_open(CONFIG_NAMESPACE, NVS_READWRITE, &handle);
    if (err == ESP_OK) {
        nvs_erase_all(handle);
        nvs_commit(handle);
        nvs_close(handle);
        ESP_LOGI(TAG, "Configuration reset successfully.");
        return 1;
    }
    return 0;
}
