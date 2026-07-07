#include "nvs_config.h"

#include "esp_log.h"
#include "nvs.h"
#include "nvs_flash.h"

#include <limits.h>
#include <string.h>

#define TAG "NVS_CONFIG"
#define CONFIG_NAMESPACE "storage"
#define CALIBRATION_NAMESPACE "sensor_cal"
#define SEQUENCE_RESERVATION_SIZE UINT64_C(1024)

static uint64_t s_sequence_next = 0;
static uint64_t s_sequence_end = 0;

int nvs_config_init(void)
{
    esp_err_t error = nvs_flash_init();
    if (error == ESP_ERR_NVS_NO_FREE_PAGES ||
            error == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        error = nvs_flash_erase();
        if (error == ESP_OK) error = nvs_flash_init();
    }
    return error == ESP_OK;
}

static bool get_required_string(nvs_handle_t handle,
                                const char *key,
                                char *destination,
                                size_t destination_size)
{
    size_t length = destination_size;
    return destination != NULL && destination_size > 1 &&
            nvs_get_str(handle, key, destination, &length) == ESP_OK &&
            length > 1 && length <= destination_size;
}

int nvs_config_load(app_config_t *config)
{
    if (config == NULL) return 0;
    memset(config, 0, sizeof(*config));
    nvs_handle_t handle;
    if (nvs_open(CONFIG_NAMESPACE, NVS_READONLY, &handle) != ESP_OK) return 0;
    bool loaded = get_required_string(handle, NVS_KEY_WIFI_SSID,
                    config->wifi_ssid, sizeof(config->wifi_ssid)) &&
            get_required_string(handle, NVS_KEY_WIFI_PASS,
                    config->wifi_pass, sizeof(config->wifi_pass)) &&
            get_required_string(handle, NVS_KEY_GATEWAY_IP,
                    config->gateway_ip, sizeof(config->gateway_ip)) &&
            get_required_string(handle, NVS_KEY_NODE_ID,
                    config->node_id, sizeof(config->node_id)) &&
            get_required_string(handle, NVS_KEY_ROOM_ID,
                    config->room_id, sizeof(config->room_id)) &&
            get_required_string(handle, NVS_KEY_AUTH_KEY,
                    config->auth_key, sizeof(config->auth_key)) &&
            nvs_get_u8(handle, NVS_KEY_PROVISIONED, &config->provisioned) == ESP_OK &&
            config->provisioned == 1;
    nvs_close(handle);
    if (!loaded) memset(config, 0, sizeof(*config));
    return loaded;
}

int nvs_config_save(const app_config_t *config)
{
    if (config == NULL || config->wifi_ssid[0] == '\0' ||
            config->wifi_pass[0] == '\0' || config->gateway_ip[0] == '\0' ||
            config->node_id[0] == '\0' || config->room_id[0] == '\0' ||
            config->auth_key[0] == '\0' || config->provisioned != 1) {
        return 0;
    }
    nvs_handle_t handle;
    esp_err_t error = nvs_open(CONFIG_NAMESPACE, NVS_READWRITE, &handle);
    if (error != ESP_OK) return 0;
    error = nvs_set_str(handle, NVS_KEY_WIFI_SSID, config->wifi_ssid);
    if (error == ESP_OK) error = nvs_set_str(handle, NVS_KEY_WIFI_PASS, config->wifi_pass);
    if (error == ESP_OK) error = nvs_set_str(handle, NVS_KEY_GATEWAY_IP, config->gateway_ip);
    if (error == ESP_OK) error = nvs_set_str(handle, NVS_KEY_NODE_ID, config->node_id);
    if (error == ESP_OK) error = nvs_set_str(handle, NVS_KEY_ROOM_ID, config->room_id);
    if (error == ESP_OK) error = nvs_set_str(handle, NVS_KEY_AUTH_KEY, config->auth_key);
    if (error == ESP_OK) error = nvs_set_u8(handle, NVS_KEY_PROVISIONED, 1);
    if (error == ESP_OK) error = nvs_commit(handle);
    nvs_close(handle);
    if (error != ESP_OK) {
        ESP_LOGE(TAG, "NVS configuration save failed: %s", esp_err_to_name(error));
        return 0;
    }
    return 1;
}

int nvs_config_reset(void)
{
    nvs_handle_t handle;
    esp_err_t error = nvs_open(CONFIG_NAMESPACE, NVS_READWRITE, &handle);
    if (error != ESP_OK) return 0;
    error = nvs_erase_all(handle);
    if (error == ESP_OK) error = nvs_commit(handle);
    nvs_close(handle);
    return error == ESP_OK;
}

int nvs_config_load_sensor_calibration(sensor_calibration_config_t *calibration)
{
    if (calibration == NULL) return 0;
    memset(calibration, 0, sizeof(*calibration));
    nvs_handle_t handle;
    if (nvs_open(CALIBRATION_NAMESPACE, NVS_READONLY, &handle) != ESP_OK) {
        ESP_LOGW(TAG, "Sensor calibration namespace is missing");
        return 0;
    }

    uint32_t mq7_r0_milliohm = 0;
    uint32_t mq7_rl_milliohm = 0;
    uint32_t mq7_supply_mv = 0;
    uint32_t mq7_divider_ppm = 0;
    uint32_t mq7_curve_a_milli = 0;
    int32_t mq7_curve_b_milli = 0;
    calibration->mq7_valid =
            nvs_get_u32(handle, "mq7_r0_mohm", &mq7_r0_milliohm) == ESP_OK &&
            nvs_get_u32(handle, "mq7_rl_mohm", &mq7_rl_milliohm) == ESP_OK &&
            nvs_get_u32(handle, "mq7_vc_mv", &mq7_supply_mv) == ESP_OK &&
            nvs_get_u32(handle, "mq7_div_ppm", &mq7_divider_ppm) == ESP_OK &&
            nvs_get_u32(handle, "mq7_a_milli", &mq7_curve_a_milli) == ESP_OK &&
            nvs_get_i32(handle, "mq7_b_milli", &mq7_curve_b_milli) == ESP_OK &&
            mq7_r0_milliohm > 0 && mq7_rl_milliohm > 0 &&
            mq7_supply_mv > 0 && mq7_divider_ppm > 0 &&
            mq7_curve_a_milli > 0 && mq7_curve_b_milli < 0;
    if (calibration->mq7_valid) {
        calibration->mq7_clean_air_resistance_ohm = mq7_r0_milliohm / 1000.0;
        calibration->mq7_load_resistor_ohm = mq7_rl_milliohm / 1000.0;
        calibration->mq7_sensor_supply_mv = mq7_supply_mv;
        calibration->mq7_adc_divider_ratio = mq7_divider_ppm / 1000000.0;
        calibration->mq7_curve_a = mq7_curve_a_milli / 1000.0;
        calibration->mq7_curve_b = mq7_curve_b_milli / 1000.0;
    }

    uint32_t gp2y_zero_mv = 0;
    uint32_t gp2y_sensitivity_uv = 0;
    uint32_t gp2y_divider_ppm = 0;
    calibration->gp2y_valid =
            nvs_get_u32(handle, "gp_zero_mv", &gp2y_zero_mv) == ESP_OK &&
            nvs_get_u32(handle, "gp_sens_uv", &gp2y_sensitivity_uv) == ESP_OK &&
            nvs_get_u32(handle, "gp_div_ppm", &gp2y_divider_ppm) == ESP_OK &&
            gp2y_sensitivity_uv > 0 && gp2y_divider_ppm > 0;
    if (calibration->gp2y_valid) {
        calibration->gp2y_clean_air_voltage_mv = gp2y_zero_mv;
        calibration->gp2y_sensitivity_mv_per_ug_m3 = gp2y_sensitivity_uv / 1000.0;
        calibration->gp2y_adc_divider_ratio = gp2y_divider_ppm / 1000000.0;
    }
    nvs_close(handle);
    return calibration->mq7_valid || calibration->gp2y_valid;
}

int nvs_config_next_sequence(uint64_t *sequence)
{
    if (sequence == NULL) return 0;
    if (s_sequence_next >= s_sequence_end) {
        nvs_handle_t handle;
        if (nvs_open(CONFIG_NAMESPACE, NVS_READWRITE, &handle) != ESP_OK) return 0;
        uint64_t previous_high = 0;
        esp_err_t error = nvs_get_u64(handle, "seq_high", &previous_high);
        if (error == ESP_ERR_NVS_NOT_FOUND) {
            previous_high = 0;
            error = ESP_OK;
        }
        if (error == ESP_OK && previous_high <= UINT64_MAX - SEQUENCE_RESERVATION_SIZE) {
            uint64_t new_high = previous_high + SEQUENCE_RESERVATION_SIZE;
            error = nvs_set_u64(handle, "seq_high", new_high);
            if (error == ESP_OK) error = nvs_commit(handle);
            if (error == ESP_OK) {
                s_sequence_next = previous_high;
                s_sequence_end = new_high;
            }
        } else if (error == ESP_OK) {
            error = ESP_ERR_INVALID_STATE;
        }
        nvs_close(handle);
        if (error != ESP_OK) return 0;
    }
    *sequence = s_sequence_next++;
    return 1;
}
