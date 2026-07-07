#ifndef NVS_CONFIG_H
#define NVS_CONFIG_H

#include <stddef.h>
#include <stdint.h>

/*
 * TODO_USER_CONFIG: all keys below must be provisioned into the NVS
 * namespace "storage" before the node will start (fail closed otherwise).
 * CSV template and flashing commands: smart_home/CONFIG_REQUIRED.md §4.3.
 */
#define NVS_KEY_WIFI_SSID      "wifi_ssid"
#define NVS_KEY_WIFI_PASS      "wifi_pass"
#define NVS_KEY_GATEWAY_IP     "gateway_ip"
#define NVS_KEY_GATEWAY_PORT   "gateway_port"
#define NVS_KEY_NODE_ID        "node_id"
#define NVS_KEY_ROOM_ID        "room_id"
#define NVS_KEY_SNAPSHOT_PORT  "snapshot_port"
#define NVS_KEY_RTSP_PORT      "rtsp_port"
#define NVS_KEY_AUTH_KEY       "auth_key"
#define NVS_KEY_PROVISIONED    "provisioned"

typedef struct {
    char wifi_ssid[33];
    char wifi_pass[65];
    char gateway_ip[16];
    uint16_t gateway_port;
    char node_id[64];
    char room_id[64];
    uint16_t snapshot_port;
    uint16_t rtsp_port;
    char auth_key[65];
    uint8_t provisioned;
} app_config_t;

int nvs_config_init(void);
int nvs_config_load(app_config_t *config);
int nvs_config_save(const app_config_t *config);
int nvs_config_reset(void);

#endif /* NVS_CONFIG_H */
