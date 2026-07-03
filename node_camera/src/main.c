#include "nvs_config.h"
#include "wifi_manager.h"
#include "ble_mesh_handler.h"
#include "camera_node_identity.h"
#include "camera_motion_snapshot_policy.h"
#include "esp_log.h"
#include "esp_http_server.h"
#include "esp_timer.h"
#include "lwip/err.h"
#include "lwip/sockets.h"
#include "lwip/sys.h"
#include <lwip/netdb.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <string.h>

#define TAG "NODE_CAMERA_MAIN"
#define RTSP_PORT 554

static app_config_t s_config;

// Valid 1x1 dummy JPEG image for testing snapshot downloads
static const uint8_t dummy_jpeg[] = {
    0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x01, 0x00, 0x60,
    0x00, 0x60, 0x00, 0x00, 0xFF, 0xDB, 0x00, 0x43, 0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08,
    0x07, 0x07, 0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D, 0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12,
    0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F, 0x1E, 0x1D, 0x1A, 0x1C, 0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20,
    0x22, 0x2C, 0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C, 0x30, 0x31, 0x34, 0x34, 0x34, 0x1F, 0x27,
    0x39, 0x3D, 0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32, 0xFF, 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01,
    0x00, 0x01, 0x01, 0x01, 0x11, 0x00, 0xFF, 0xC4, 0x00, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xDA, 0x00, 0x08, 0x01,
    0x01, 0x00, 0x00, 0x3F, 0x00, 0x37, 0xFF, 0xD9
};

// Snapshot GET handler
static esp_err_t snapshot_get_handler(httpd_req_t *req)
{
    httpd_resp_set_type(req, "image/jpeg");
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    return httpd_resp_send(req, (const char *)dummy_jpeg, sizeof(dummy_jpeg));
}

static const httpd_uri_t snapshot_uri = {
    .uri       = "/api/snapshot",
    .method    = HTTP_GET,
    .handler   = snapshot_get_handler,
    .user_ctx  = NULL
};

static httpd_handle_t start_webserver(void)
{
    httpd_handle_t server = NULL;
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.server_port = 80;

    ESP_LOGI(TAG, "Starting REST snapshot API web server on port 80");
    if (httpd_start(&server, &config) == ESP_OK) {
        httpd_register_uri_handler(server, &snapshot_uri);
        return server;
    }

    ESP_LOGE(TAG, "Error starting REST web server!");
    return NULL;
}

// Helper to parse CSeq from RTSP request
static int parse_cseq(const char *req)
{
    const char *p = strstr(req, "CSeq:");
    if (p) {
        int cseq = 0;
        if (sscanf(p, "CSeq: %d", &cseq) == 1) {
            return cseq;
        }
    }
    return 0;
}

// RTSP Client connection handler
static void handle_rtsp_client(int sock)
{
    char rx_buf[1024];
    char tx_buf[1024];

    ESP_LOGI(TAG, "RTSP client connected (socket %d)", sock);

    while (1) {
        int len = recv(sock, rx_buf, sizeof(rx_buf) - 1, 0);
        if (len < 0) {
            ESP_LOGE(TAG, "RTSP recv failed: errno %d", errno);
            break;
        } else if (len == 0) {
            ESP_LOGI(TAG, "RTSP connection closed by client");
            break;
        }

        rx_buf[len] = '\0';
        int cseq = parse_cseq(rx_buf);

        if (strncmp(rx_buf, "OPTIONS", 7) == 0) {
            snprintf(tx_buf, sizeof(tx_buf),
                     "RTSP/1.0 200 OK\r\n"
                     "CSeq: %d\r\n"
                     "Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN\r\n\r\n",
                     cseq);
            send(sock, tx_buf, strlen(tx_buf), 0);
        } else if (strncmp(rx_buf, "DESCRIBE", 8) == 0) {
            const char *sdp = 
                "v=0\r\n"
                "o=- 0 0 IN IP4 0.0.0.0\r\n"
                "s=ESP32-CAM RTSP Stream\r\n"
                "c=IN IP4 0.0.0.0\r\n"
                "t=0 0\r\n"
                "m=video 0 RTP/AVP 26\r\n"
                "a=rtpmap:26 JPEG/90000\r\n";

            snprintf(tx_buf, sizeof(tx_buf),
                     "RTSP/1.0 200 OK\r\n"
                     "CSeq: %d\r\n"
                     "Content-Type: application/sdp\r\n"
                     "Content-Length: %d\r\n\r\n"
                     "%s",
                     cseq, (int)strlen(sdp), sdp);
            send(sock, tx_buf, strlen(tx_buf), 0);
        } else if (strncmp(rx_buf, "SETUP", 5) == 0) {
            snprintf(tx_buf, sizeof(tx_buf),
                     "RTSP/1.0 200 OK\r\n"
                     "CSeq: %d\r\n"
                     "Transport: RTP/AVP;unicast;client_port=5000-5001;server_port=6000-6001\r\n"
                     "Session: 98765432\r\n\r\n",
                     cseq);
            send(sock, tx_buf, strlen(tx_buf), 0);
        } else if (strncmp(rx_buf, "PLAY", 4) == 0) {
            snprintf(tx_buf, sizeof(tx_buf),
                     "RTSP/1.0 200 OK\r\n"
                     "CSeq: %d\r\n"
                     "Session: 98765432\r\n"
                     "Range: npt=0.000-\r\n\r\n",
                     cseq);
            send(sock, tx_buf, strlen(tx_buf), 0);
            ESP_LOGI(TAG, "RTSP PLAY started on socket %d", sock);
        } else if (strncmp(rx_buf, "TEARDOWN", 8) == 0) {
            snprintf(tx_buf, sizeof(tx_buf),
                     "RTSP/1.0 200 OK\r\n"
                     "CSeq: %d\r\n\r\n",
                     cseq);
            send(sock, tx_buf, strlen(tx_buf), 0);
            break;
        } else {
            // Unhandled request
            snprintf(tx_buf, sizeof(tx_buf),
                     "RTSP/1.0 501 Not Implemented\r\n"
                     "CSeq: %d\r\n\r\n",
                     cseq);
            send(sock, tx_buf, strlen(tx_buf), 0);
        }
    }

    close(sock);
    ESP_LOGI(TAG, "RTSP socket %d closed", sock);
}

// RTSP Server listener loop
static void rtsp_server_task(void *pvParameters)
{
    struct sockaddr_in dest_addr;
    dest_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    dest_addr.sin_family = AF_INET;
    dest_addr.sin_port = htons(RTSP_PORT);

    int listen_sock = socket(AF_INET, SOCK_STREAM, IPPROTO_IP);
    if (listen_sock < 0) {
        ESP_LOGE(TAG, "Unable to create socket: errno %d", errno);
        vTaskDelete(NULL);
        return;
    }

    int opt = 1;
    setsockopt(listen_sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    int err = bind(listen_sock, (struct sockaddr *)&dest_addr, sizeof(dest_addr));
    if (err != 0) {
        ESP_LOGE(TAG, "Socket unable to bind: errno %d", errno);
        close(listen_sock);
        vTaskDelete(NULL);
        return;
    }

    err = listen(listen_sock, 5);
    if (err != 0) {
        ESP_LOGE(TAG, "Error occurred during listen: errno %d", errno);
        close(listen_sock);
        vTaskDelete(NULL);
        return;
    }

    ESP_LOGI(TAG, "RTSP Server listening on port %d", RTSP_PORT);

    while (1) {
        struct sockaddr_storage source_addr;
        socklen_t addr_len = sizeof(source_addr);
        int sock = accept(listen_sock, (struct sockaddr *)&source_addr, &addr_len);
        if (sock < 0) {
            ESP_LOGE(TAG, "Unable to accept connection: errno %d", errno);
            break;
        }

        handle_rtsp_client(sock);
    }

    close(listen_sock);
    vTaskDelete(NULL);
}

void app_main(void)
{
    ESP_LOGI(TAG, "Initializing Camera Node...");

    // Initialize storage & configurations
    if (!nvs_config_init()) {
        ESP_LOGE(TAG, "NVS flash initialization failed");
        return;
    }

    if (!nvs_config_load(&s_config)) {
        ESP_LOGW(TAG, "Config load failed. Using unprovisioned defaults.");
        memset(&s_config, 0, sizeof(s_config));
    }

    if (s_config.provisioned) {
        ESP_LOGI(TAG, "Device configured. Connecting to Wi-Fi SSID: %s", s_config.wifi_ssid);
        wifi_manager_init();
        wifi_manager_connect(s_config.wifi_ssid, s_config.wifi_pass);

        // Start REST Web server for snapshots on port 80
        start_webserver();

        // Start RTSP Video server on port 554
        xTaskCreate(rtsp_server_task, "rtsp_server_task", 4096, NULL, 5, NULL);
    } else {
        ESP_LOGI(TAG, "Device unconfigured. Entering BLE Mesh provisioning mode.");
        ble_mesh_handler_init();
    }
}