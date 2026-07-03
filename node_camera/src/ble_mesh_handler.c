#include "ble_mesh_handler.h"
#include "nvs_config.h"
#include "esp_log.h"
#include "esp_bt.h"
#include "esp_bt_main.h"
#include "esp_bt_device.h"
#include "esp_ble_mesh_common_api.h"
#include "esp_ble_mesh_provisioning_api.h"
#include "esp_ble_mesh_networking_api.h"
#include "esp_ble_mesh_config_model_api.h"
#include "esp_ble_mesh_defs.h"
#include "esp_system.h"
#include <string.h>

#define TAG "BLE_MESH_HANDLER"

#define CID_ESP 0x02E5
#define ESP_BLE_MESH_VND_MODEL_ID_CLIENT 0x0000
#define ESP_BLE_MESH_VND_MODEL_ID_SERVER 0x0001

#define ESP_BLE_MESH_VND_MODEL_OP_SET_CONFIG ESP_BLE_MESH_MODEL_OP_3(0x01, CID_ESP)
#define ESP_BLE_MESH_VND_MODEL_OP_STATUS     ESP_BLE_MESH_MODEL_OP_3(0x02, CID_ESP)

static uint8_t dev_uuid[16] = {0xdd, 0xdd};
static bool s_is_provisioned = false;

static esp_ble_mesh_cfg_srv_t config_server = {
    .relay = ESP_BLE_MESH_RELAY_DISABLED,
    .beacon = ESP_BLE_MESH_BEACON_ENABLED,
    .friend_state = ESP_BLE_MESH_FRIEND_NOT_SUPPORTED,
    .gatt_proxy = ESP_BLE_MESH_GATT_PROXY_ENABLED,
    .default_ttl = 7,
    .net_transmit = ESP_BLE_MESH_TRANSMIT(2, 20),
    .relay_retransmit = ESP_BLE_MESH_TRANSMIT(2, 20),
};

static esp_ble_mesh_model_t root_models[] = {
    ESP_BLE_MESH_MODEL_CFG_SRV(&config_server),
};

static esp_ble_mesh_client_t config_client;
static esp_ble_mesh_model_t config_models[] = {
    ESP_BLE_MESH_MODEL_CFG_CLI(&config_client),
};

static esp_ble_mesh_model_pub_t vnd_pub;
static esp_ble_mesh_model_op_t vnd_op[] = {
    ESP_BLE_MESH_MODEL_OP(ESP_BLE_MESH_VND_MODEL_OP_SET_CONFIG, 1),
    ESP_BLE_MESH_MODEL_OP_END,
};

static esp_ble_mesh_model_t vnd_models[] = {
    ESP_BLE_MESH_VENDOR_MODEL(CID_ESP, ESP_BLE_MESH_VND_MODEL_ID_SERVER, vnd_op, &vnd_pub, NULL),
};

static esp_ble_mesh_elem_t elements[] = {
    ESP_BLE_MESH_ELEMENT(0, root_models, vnd_models),
    ESP_BLE_MESH_ELEMENT(0, config_models, ESP_BLE_MESH_MODEL_NONE),
};

static esp_ble_mesh_comp_t composition = {
    .cid = CID_ESP,
    .elements = elements,
    .element_count = ARRAY_SIZE(elements),
};

static esp_ble_mesh_prov_t provision = {
    .uuid = dev_uuid,
};

static void prov_callback(esp_ble_mesh_prov_cb_event_t event,
                                 esp_ble_mesh_prov_cb_param_t *param)
{
    switch (event) {
    case ESP_BLE_MESH_NODE_PROV_ENABLE_COMP_EVT:
        ESP_LOGI(TAG, "Provisioning enabled. BLE Mesh Device UUID: ");
        esp_log_buffer_hex(TAG, dev_uuid, 16);
        break;
    case ESP_BLE_MESH_NODE_PROV_LINK_OPEN_EVT:
        ESP_LOGI(TAG, "Provisioning link opened via %s",
                 param->node_prov_link_open.bearer == ESP_BLE_MESH_PROV_ADV ? "PB-ADV" : "PB-GATT");
        break;
    case ESP_BLE_MESH_NODE_PROV_LINK_CLOSE_EVT:
        ESP_LOGI(TAG, "Provisioning link closed");
        break;
    case ESP_BLE_MESH_NODE_PROV_COMPLETE_EVT:
        ESP_LOGI(TAG, "Node provisioning complete. Address: 0x%04x", param->node_prov_complete.addr);
        s_is_provisioned = true;
        break;
    case ESP_BLE_MESH_NODE_PROV_RESET_EVT:
        ESP_LOGI(TAG, "Node provisioning reset");
        s_is_provisioned = false;
        break;
    default:
        break;
    }
}

static void vnd_model_callback(esp_ble_mesh_model_cb_event_t event,
                                      esp_ble_mesh_model_cb_param_t *param)
{
    if (event == ESP_BLE_MESH_MODEL_OPERATION_EVT) {
        if (param->model_operation.opcode == ESP_BLE_MESH_VND_MODEL_OP_SET_CONFIG) {
            ESP_LOGI(TAG, "Received custom configuration from Gateway.");
            
            // Format: SSID\0PASSWORD\0GATEWAY_IP\0NODE_ID\0ROOM_ID\0
            char *data = (char *)param->model_operation.msg;
            uint16_t length = param->model_operation.length;

            if (length < 5) {
                ESP_LOGE(TAG, "Invalid configuration packet length: %d", length);
                return;
            }

            app_config_t config;
            memset(&config, 0, sizeof(config));

            char *ssid_ptr = data;
            size_t ssid_len = strlen(ssid_ptr);
            if (ssid_len >= sizeof(config.wifi_ssid)) {
                ESP_LOGE(TAG, "SSID too long: %d", ssid_len);
                return;
            }
            strcpy(config.wifi_ssid, ssid_ptr);

            char *pass_ptr = ssid_ptr + ssid_len + 1;
            size_t pass_len = strlen(pass_ptr);
            if (pass_len >= sizeof(config.wifi_pass)) {
                ESP_LOGE(TAG, "Password too long: %d", pass_len);
                return;
            }
            strcpy(config.wifi_pass, pass_ptr);

            char *gateway_ptr = pass_ptr + pass_len + 1;
            size_t gateway_len = strlen(gateway_ptr);
            if (gateway_len >= sizeof(config.gateway_ip)) {
                ESP_LOGE(TAG, "Gateway IP too long: %d", gateway_len);
                return;
            }
            strcpy(config.gateway_ip, gateway_ptr);

            char *node_ptr = gateway_ptr + gateway_len + 1;
            size_t node_len = strlen(node_ptr);
            if (node_len >= sizeof(config.node_id)) {
                ESP_LOGE(TAG, "Node ID too long: %d", node_len);
                return;
            }
            strcpy(config.node_id, node_ptr);

            char *room_ptr = node_ptr + node_len + 1;
            size_t room_len = strlen(room_ptr);
            if (room_len >= sizeof(config.room_id)) {
                ESP_LOGE(TAG, "Room ID too long: %d", room_len);
                return;
            }
            strcpy(config.room_id, room_ptr);

            config.provisioned = 1;

            ESP_LOGI(TAG, "Parsed config -> SSID: %s, Gateway IP: %s, Node ID: %s, Room: %s",
                     config.wifi_ssid, config.gateway_ip, config.node_id, config.room_id);

            // Save to NVS
            if (nvs_config_save(&config)) {
                ESP_LOGI(TAG, "Config saved. Sending confirmation response...");
                
                // Respond to Gateway
                uint8_t status = 0; // SUCCESS
                esp_ble_mesh_server_model_send_msg(param->model_operation.model,
                                                   param->model_operation.ctx,
                                                   ESP_BLE_MESH_VND_MODEL_OP_STATUS,
                                                   sizeof(status), &status);

                // Delay to allow response packet to go out, then reboot to join WiFi
                vTaskDelay(pdMS_TO_TICKS(1500));
                esp_restart();
            } else {
                ESP_LOGE(TAG, "Failed to save configuration to NVS.");
            }
        }
    }
}

void ble_mesh_handler_init(void)
{
    esp_err_t err;

    esp_bt_controller_config_t bt_cfg = BT_CONTROLLER_INIT_CONFIG_DEFAULT();
    err = esp_bt_controller_init(&bt_cfg);
    if (err) {
        ESP_LOGE(TAG, "BT controller init failed: %s", esp_err_to_name(err));
        return;
    }

    err = esp_bt_controller_enable(ESP_BT_MODE_BLE);
    if (err) {
        ESP_LOGE(TAG, "BT controller enable failed: %s", esp_err_to_name(err));
        return;
    }

    err = esp_bluedroid_init();
    if (err) {
        ESP_LOGE(TAG, "Bluedroid init failed: %s", esp_err_to_name(err));
        return;
    }

    err = esp_bluedroid_enable();
    if (err) {
        ESP_LOGE(TAG, "Bluedroid enable failed: %s", esp_err_to_name(err));
        return;
    }

    // Set device MAC address as UUID suffix to ensure uniqueness
    const uint8_t *mac = esp_bt_dev_get_address();
    if (mac) {
        memcpy(dev_uuid + 10, mac, 6);
    }

    esp_ble_mesh_register_prov_callback(prov_callback);
    esp_ble_mesh_register_custom_model_callback(vnd_model_callback);

    err = esp_ble_mesh_init(&provision, &composition);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "BLE Mesh init failed: %s", esp_err_to_name(err));
        return;
    }

    esp_ble_mesh_node_prov_enable(ESP_BLE_MESH_PROV_ADV | ESP_BLE_MESH_PROV_GATT);
    ESP_LOGI(TAG, "BLE Mesh initialized and provisioning enabled.");
}

bool ble_mesh_handler_is_provisioned(void)
{
    return s_is_provisioned;
}
