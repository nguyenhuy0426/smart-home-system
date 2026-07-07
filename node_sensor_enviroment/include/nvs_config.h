#ifndef NVS_CONFIG_H
#define NVS_CONFIG_H

#include <stddef.h>
#include <stdbool.h>
#include <stdint.h>

/*
 * TODO_USER_CONFIG: provision the keys below into NVS namespace "storage"
 * (CSV template: smart_home/CONFIG_REQUIRED.md §4.1). Optional per-unit
 * calibration keys (mq7_*, gp_*) are read in nvs_config.c.
 */
#define NVS_KEY_WIFI_SSID    "wifi_ssid"
#define NVS_KEY_WIFI_PASS    "wifi_pass"
#define NVS_KEY_GATEWAY_IP   "gateway_ip"
#define NVS_KEY_NODE_ID      "node_id"
#define NVS_KEY_ROOM_ID      "room_id"
#define NVS_KEY_AUTH_KEY     "auth_key"
#define NVS_KEY_PROVISIONED  "provisioned"

typedef struct {
    char wifi_ssid[33];
    char wifi_pass[64];
    char gateway_ip[16];
    char node_id[64];
    char room_id[64];
    char auth_key[65];
    uint8_t provisioned;
} app_config_t;

typedef struct {
    bool mq7_valid;
    double mq7_sensor_supply_mv;
    double mq7_adc_divider_ratio;
    double mq7_load_resistor_ohm;
    double mq7_clean_air_resistance_ohm;
    double mq7_curve_a;
    double mq7_curve_b;
    bool gp2y_valid;
    double gp2y_adc_divider_ratio;
    double gp2y_clean_air_voltage_mv;
    double gp2y_sensitivity_mv_per_ug_m3;
} sensor_calibration_config_t;

int nvs_config_init(void);
int nvs_config_load(app_config_t *config);
int nvs_config_save(const app_config_t *config);
int nvs_config_reset(void);
int nvs_config_load_sensor_calibration(sensor_calibration_config_t *calibration);
int nvs_config_next_sequence(uint64_t *sequence);

#endif /* NVS_CONFIG_H */
