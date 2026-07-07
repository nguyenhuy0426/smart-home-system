#ifndef NVS_CONFIG_H
#define NVS_CONFIG_H

#include <stddef.h>
#include <stdint.h>

/*
 * TODO_USER_CONFIG: provision the keys below into NVS namespace "storage"
 * (CSV template: smart_home/CONFIG_REQUIRED.md §4.2). Do not provision
 * "access_seq" — it is a firmware-maintained counter.
 */
#define NVS_KEY_WIFI_SSID    "wifi_ssid"
#define NVS_KEY_WIFI_PASS    "wifi_pass"
#define NVS_KEY_GATEWAY_IP   "gateway_ip"
#define NVS_KEY_NODE_ID      "node_id"
#define NVS_KEY_ROOM_ID      "room_id"
#define NVS_KEY_AUTH_KEY     "auth_key"
#define NVS_KEY_PROVISIONED  "provisioned"
#define NVS_KEY_ACCESS_SEQ   "access_seq"

typedef struct {
    char wifi_ssid[32];
    char wifi_pass[64];
    char gateway_ip[16];
    char node_id[64];
    char room_id[64];
    char auth_key[65];
    uint8_t provisioned;
} app_config_t;

int nvs_config_init(void);
int nvs_config_load(app_config_t *config);
int nvs_config_save(const app_config_t *config);
int nvs_config_reset(void);
int nvs_config_next_access_sequence(uint32_t *out_sequence);

#endif /* NVS_CONFIG_H */
