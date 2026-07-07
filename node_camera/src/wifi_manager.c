#include "wifi_manager.h"
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <string.h>

#define TAG "WIFI_MANAGER"
#define MAX_RETRY_DELAY_MS 60000

static bool s_connected = false;
static int s_retry_delay_ms = 1000;
static char s_ssid[33] = {0};
static char s_pass[65] = {0};
static TaskHandle_t s_retry_task;
static bool s_should_connect;

static void reconnect_task(void *context)
{
    (void)context;
    int delay_ms = s_retry_delay_ms;
    vTaskDelay(pdMS_TO_TICKS(delay_ms));
    if (!s_connected && s_should_connect) {
        esp_err_t error = esp_wifi_connect();
        if (error != ESP_OK) {
            ESP_LOGE(TAG, "Wi-Fi reconnect failed: %s", esp_err_to_name(error));
        }
    }
    s_retry_task = NULL;
    vTaskDelete(NULL);
}

static void event_handler(void* arg, esp_event_base_t event_base,
                                int32_t event_id, void* event_data)
{
    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_START) {
        if (s_should_connect) (void)esp_wifi_connect();
    } else if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED) {
        s_connected = false;
        if (!s_should_connect) return;
        ESP_LOGI(TAG, "Disconnected from Wi-Fi. Retrying in %d ms...", s_retry_delay_ms);
        if (s_retry_task == NULL && xTaskCreate(reconnect_task, "wifi_retry", 2048,
                NULL, 4, &s_retry_task) != pdPASS) {
            ESP_LOGE(TAG, "Could not schedule Wi-Fi reconnect");
        }
        s_retry_delay_ms = (s_retry_delay_ms * 2 > MAX_RETRY_DELAY_MS) ? MAX_RETRY_DELAY_MS : s_retry_delay_ms * 2;
    } else if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP) {
        ip_event_got_ip_t* event = (ip_event_got_ip_t*) event_data;
        ESP_LOGI(TAG, "Got IP address: " IPSTR, IP2STR(&event->ip_info.ip));
        s_connected = true;
        s_retry_delay_ms = 1000; // reset retry delay
    }
}

void wifi_manager_init(void)
{
    static bool init_done = false;
    if (init_done) return;

    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));

    esp_event_handler_instance_t instance_any_id;
    esp_event_handler_instance_t instance_got_ip;
    ESP_ERROR_CHECK(esp_event_handler_instance_register(WIFI_EVENT,
                                                        ESP_EVENT_ANY_ID,
                                                        &event_handler,
                                                        NULL,
                                                        &instance_any_id));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(IP_EVENT,
                                                        IP_EVENT_STA_GOT_IP,
                                                        &event_handler,
                                                        NULL,
                                                        &instance_got_ip));

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_start());
    init_done = true;
}

void wifi_manager_connect(const char *ssid, const char *password)
{
    if (ssid == NULL || password == NULL) {
        ESP_LOGE(TAG, "SSID is empty. Cannot connect.");
        return;
    }
    size_t ssid_length = strnlen(ssid, sizeof(s_ssid));
    size_t password_length = strnlen(password, sizeof(s_pass));
    if (ssid_length == 0 || ssid_length >= sizeof(s_ssid) ||
            password_length < 8 || password_length > 63) {
        ESP_LOGE(TAG, "Invalid Wi-Fi credential lengths");
        return;
    }
    memcpy(s_ssid, ssid, ssid_length + 1);
    memcpy(s_pass, password, password_length + 1);

    wifi_config_t wifi_config = {
        .sta = {
            .threshold.authmode = WIFI_AUTH_WPA2_PSK,
            .pmf_cfg = {
                .capable = true,
                .required = false
            },
        },
    };
    memcpy(wifi_config.sta.ssid, s_ssid, ssid_length);
    memcpy(wifi_config.sta.password, s_pass, password_length);

    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wifi_config));
    ESP_LOGI(TAG, "Connecting to Wi-Fi SSID: %s", s_ssid);
    s_retry_delay_ms = 1000;
    s_should_connect = true;
    esp_wifi_connect();
}

void wifi_manager_disconnect(void)
{
    s_should_connect = false;
    esp_wifi_disconnect();
    s_connected = false;
}

bool wifi_manager_is_connected(void)
{
    return s_connected;
}
