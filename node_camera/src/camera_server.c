#include "camera_server.h"

#include "camera_capture.h"
#include "camera_protocol.h"
#include "wifi_manager.h"

#include "esp_http_server.h"
#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "lwip/inet.h"
#include "lwip/sockets.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TAG "CAMERA_SERVER"
#define RTSP_MAX_CLIENTS 2
#define RTSP_SESSION_TIMEOUT_MS 30000ULL
#define RTSP_FRAME_INTERVAL_MS 100ULL
#define RTSP_RECEIVE_TIMEOUT_MS 40
#define RTSP_TASK_STACK_BYTES 10240
#define RTP_PACKET_BYTES 1400
#define UDP_PORT_FIRST 6970
#define UDP_PORT_LAST 7070

typedef enum {
    SESSION_CONNECTED,
    SESSION_SETUP,
    SESSION_PLAYING,
} session_state_t;

typedef struct {
    int control_socket;
    int rtp_socket;
    int rtcp_socket;
    struct sockaddr_in peer;
    struct sockaddr_in rtp_destination;
    char session_id[RTSP_SESSION_ID_MAX_BYTES];
    char request_buffer[RTSP_REQUEST_MAX_BYTES];
    size_t request_used;
    app_config_t config;
    session_state_t state;
    uint64_t last_activity_ms;
    uint64_t next_frame_ms;
    rtp_jpeg_state_t rtp;
} rtsp_session_t;

typedef struct {
    int socket;
    struct sockaddr_in destination;
} udp_sender_context_t;

static app_config_t s_config;
static httpd_handle_t s_http_server;
static SemaphoreHandle_t s_client_count_lock;
static unsigned s_client_count;

static uint64_t monotonic_ms(void)
{
    return (uint64_t)esp_timer_get_time() / 1000ULL;
}

static bool send_all(int socket_fd, const char *data, size_t length)
{
    size_t sent = 0;
    while (sent < length) {
        int result = send(socket_fd, data + sent, length - sent, 0);
        if (result <= 0) return false;
        sent += (size_t)result;
    }
    return true;
}

static bool send_response(int socket_fd, uint32_t cseq, int status,
                          const char *reason, const char *extra_headers,
                          const char *body)
{
    char response[1536];
    size_t body_length = body == NULL ? 0 : strlen(body);
    int length = snprintf(response, sizeof(response),
            "RTSP/1.0 %d %s\r\nCSeq: %u\r\n%s%s%s%zu\r\n\r\n%s",
            status, reason, cseq,
            extra_headers == NULL ? "" : extra_headers,
            body == NULL ? "" : "Content-Length: ",
            body == NULL ? "" : "",
            body_length,
            body == NULL ? "" : body);
    if (body == NULL) {
        length = snprintf(response, sizeof(response),
                "RTSP/1.0 %d %s\r\nCSeq: %u\r\n%s\r\n",
                status, reason, cseq,
                extra_headers == NULL ? "" : extra_headers);
    }
    return length > 0 && (size_t)length < sizeof(response) &&
            send_all(socket_fd, response, (size_t)length);
}

static bool request_is_authorized(const rtsp_request_t *request,
                                  const app_config_t *config)
{
    static const char prefix[] = "Bearer ";
    return request->has_authorization &&
            strncmp(request->authorization, prefix, sizeof(prefix) - 1) == 0 &&
            constant_time_secret_matches(config->auth_key,
                    request->authorization + sizeof(prefix) - 1,
                    sizeof(config->auth_key));
}

static bool http_request_is_authorized(httpd_req_t *request)
{
    size_t length = httpd_req_get_hdr_value_len(request, "X-SmartHome-Auth");
    if (length == 0 || length >= sizeof(s_config.auth_key)) return false;
    char provided[sizeof(s_config.auth_key)];
    if (httpd_req_get_hdr_value_str(request, "X-SmartHome-Auth", provided,
            sizeof(provided)) != ESP_OK) return false;
    return constant_time_secret_matches(s_config.auth_key, provided,
                                        sizeof(s_config.auth_key));
}

static esp_err_t send_service_unavailable(httpd_req_t *request,
                                          const char *message)
{
    httpd_resp_set_status(request, "503 Service Unavailable");
    httpd_resp_set_type(request, "text/plain");
    httpd_resp_set_hdr(request, "Retry-After", "1");
    return httpd_resp_sendstr(request, message);
}

static esp_err_t snapshot_get_handler(httpd_req_t *request)
{
    if (!http_request_is_authorized(request)) {
        httpd_resp_set_hdr(request, "WWW-Authenticate", "SmartHomeKey");
        return httpd_resp_send_err(request, HTTPD_401_UNAUTHORIZED,
                                   "Authentication required");
    }
    if (!wifi_manager_is_connected() || !camera_capture_is_ready()) {
        return send_service_unavailable(request, "Camera unavailable");
    }
    camera_frame_t frame;
    if (!camera_capture_acquire(&frame)) {
        return send_service_unavailable(request, "Frame capture failed");
    }
    httpd_resp_set_type(request, "image/jpeg");
    httpd_resp_set_hdr(request, "Cache-Control", "no-store");
    httpd_resp_set_hdr(request, "X-SmartHome-Node-Id", s_config.node_id);
    httpd_resp_set_hdr(request, "X-SmartHome-Room-Id", s_config.room_id);
    esp_err_t result = httpd_resp_send(request, (const char *)frame.data,
                                       frame.length);
    camera_capture_release(&frame);
    return result;
}

static const httpd_uri_t s_snapshot_uri = {
    .uri = "/api/snapshot",
    .method = HTTP_GET,
    .handler = snapshot_get_handler,
    .user_ctx = NULL,
};

static bool start_snapshot_server(uint16_t port)
{
    httpd_config_t http_config = HTTPD_DEFAULT_CONFIG();
    http_config.server_port = port;
    http_config.max_open_sockets = 4;
    http_config.lru_purge_enable = true;
    if (httpd_start(&s_http_server, &http_config) != ESP_OK) return false;
    if (httpd_register_uri_handler(s_http_server, &s_snapshot_uri) != ESP_OK) {
        httpd_stop(s_http_server);
        s_http_server = NULL;
        return false;
    }
    return true;
}

static bool bind_udp_pair(rtsp_session_t *session, uint16_t *rtp_port,
                          uint16_t *rtcp_port)
{
    for (uint16_t port = UDP_PORT_FIRST; port + 1 <= UDP_PORT_LAST; port += 2) {
        int rtp = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        int rtcp = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        if (rtp < 0 || rtcp < 0) {
            if (rtp >= 0) close(rtp);
            if (rtcp >= 0) close(rtcp);
            return false;
        }
        struct sockaddr_in address = {
            .sin_family = AF_INET,
            .sin_addr.s_addr = htonl(INADDR_ANY),
            .sin_port = htons(port),
        };
        bool bound = bind(rtp, (struct sockaddr *)&address, sizeof(address)) == 0;
        address.sin_port = htons((uint16_t)(port + 1));
        bound = bound && bind(rtcp, (struct sockaddr *)&address, sizeof(address)) == 0;
        if (bound) {
            struct timeval timeout = {.tv_sec = 0, .tv_usec = 200000};
            (void)setsockopt(rtp, SOL_SOCKET, SO_SNDTIMEO, &timeout,
                             sizeof(timeout));
            session->rtp_socket = rtp;
            session->rtcp_socket = rtcp;
            *rtp_port = port;
            *rtcp_port = (uint16_t)(port + 1);
            return true;
        }
        close(rtp);
        close(rtcp);
    }
    return false;
}

static int poll_request(rtsp_session_t *session, const uint8_t **request,
                        size_t *request_length)
{
    for (size_t i = 0; i + 3 < session->request_used; ++i) {
        if (memcmp(session->request_buffer + i, "\r\n\r\n", 4) == 0) {
            *request = (const uint8_t *)session->request_buffer;
            *request_length = i + 4;
            return 1;
        }
    }
    if (session->request_used == sizeof(session->request_buffer)) return -2;
    int received = recv(session->control_socket,
            session->request_buffer + session->request_used,
            sizeof(session->request_buffer) - session->request_used, 0);
    if (received == 0) return -1;
    if (received < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) return 0;
        return -1;
    }
    session->request_used += (size_t)received;
    for (size_t i = 0; i + 3 < session->request_used; ++i) {
        if (memcmp(session->request_buffer + i, "\r\n\r\n", 4) == 0) {
            *request = (const uint8_t *)session->request_buffer;
            *request_length = i + 4;
            return 1;
        }
    }
    return session->request_used == sizeof(session->request_buffer) ? -2 : 0;
}

static void consume_request(rtsp_session_t *session, size_t length)
{
    if (length > session->request_used) length = session->request_used;
    memmove(session->request_buffer, session->request_buffer + length,
            session->request_used - length);
    session->request_used -= length;
}

static bool udp_packet_sender(const uint8_t *packet, size_t length,
                              void *opaque)
{
    udp_sender_context_t *context = opaque;
    int sent = sendto(context->socket, packet, length, 0,
            (const struct sockaddr *)&context->destination,
            sizeof(context->destination));
    return sent == (int)length;
}

static bool stream_one_frame(rtsp_session_t *session)
{
    camera_frame_t frame;
    if (!camera_capture_acquire(&frame)) return false;
    udp_sender_context_t sender = {
        .socket = session->rtp_socket,
        .destination = session->rtp_destination,
    };
    bool result = rtp_jpeg_packetize(frame.data, frame.length,
            RTP_PACKET_BYTES, &session->rtp, udp_packet_sender, &sender);
    camera_capture_release(&frame);
    return result;
}

static bool session_matches(const rtsp_session_t *session,
                            const rtsp_request_t *request)
{
    return request->has_session &&
            strcmp(request->session_id, session->session_id) == 0;
}

static bool get_server_ipv4(int socket_fd, char *output, size_t capacity)
{
    struct sockaddr_in local;
    socklen_t length = sizeof(local);
    if (getsockname(socket_fd, (struct sockaddr *)&local, &length) != 0)
        return false;
    return inet_ntoa_r(local.sin_addr, output, capacity) != NULL;
}

static bool handle_request(rtsp_session_t *session,
                           const rtsp_request_t *request, bool *close_session)
{
    *close_session = false;
    if (request->method == RTSP_METHOD_OPTIONS) {
        return send_response(session->control_socket, request->cseq, 200, "OK",
                "Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN\r\n", NULL);
    }
    if (!request_is_authorized(request, &session->config)) {
        return send_response(session->control_socket, request->cseq, 401,
                "Unauthorized", "WWW-Authenticate: Bearer\r\n", NULL);
    }

    if (request->method == RTSP_METHOD_DESCRIBE) {
        char server_ip[INET_ADDRSTRLEN];
        char sdp[768];
        if (!get_server_ipv4(session->control_socket, server_ip,
                sizeof(server_ip)) || !rtsp_generate_sdp(sdp, sizeof(sdp),
                server_ip, session->config.node_id, session->config.room_id,
                session->rtp.ssrc, 10)) {
            return send_response(session->control_socket, request->cseq, 500,
                                 "Internal Server Error", NULL, NULL);
        }
        return send_response(session->control_socket, request->cseq, 200, "OK",
                "Content-Type: application/sdp\r\n", sdp);
    }

    if (request->method == RTSP_METHOD_SETUP) {
        if (!request->transport_supported) {
            return send_response(session->control_socket, request->cseq, 461,
                                 "Unsupported Transport", NULL, NULL);
        }
        if (session->state != SESSION_CONNECTED ||
                strstr(request->uri, "trackID=0") == NULL) {
            return send_response(session->control_socket, request->cseq, 455,
                                 "Method Not Valid in This State", NULL, NULL);
        }
        uint16_t server_rtp_port;
        uint16_t server_rtcp_port;
        if (!bind_udp_pair(session, &server_rtp_port, &server_rtcp_port)) {
            return send_response(session->control_socket, request->cseq, 453,
                                 "Not Enough Bandwidth", NULL, NULL);
        }
        session->rtp_destination = session->peer;
        session->rtp_destination.sin_port = htons(request->client_rtp_port);
        char headers[320];
        int length = snprintf(headers, sizeof(headers),
                "Transport: RTP/AVP;unicast;client_port=%u-%u;server_port=%u-%u;ssrc=%08X\r\n"
                "Session: %s;timeout=30\r\n",
                (unsigned)request->client_rtp_port,
                (unsigned)request->client_rtcp_port,
                (unsigned)server_rtp_port, (unsigned)server_rtcp_port,
                (unsigned)session->rtp.ssrc, session->session_id);
        if (length <= 0 || (size_t)length >= sizeof(headers)) return false;
        session->state = SESSION_SETUP;
        return send_response(session->control_socket, request->cseq, 200, "OK",
                             headers, NULL);
    }

    if (request->method == RTSP_METHOD_PLAY) {
        if (session->state != SESSION_SETUP || !session_matches(session, request)) {
            return send_response(session->control_socket, request->cseq, 454,
                                 "Session Not Found", NULL, NULL);
        }
        char headers[512];
        int length = snprintf(headers, sizeof(headers),
                "Session: %s;timeout=30\r\nRange: npt=0.000-\r\n"
                "RTP-Info: url=%s/trackID=0;seq=%u;rtptime=%u\r\n",
                session->session_id, request->uri,
                (unsigned)session->rtp.sequence,
                (unsigned)session->rtp.timestamp);
        if (length <= 0 || (size_t)length >= sizeof(headers) ||
                !send_response(session->control_socket, request->cseq, 200,
                               "OK", headers, NULL)) return false;
        session->state = SESSION_PLAYING;
        session->next_frame_ms = monotonic_ms();
        return true;
    }

    if (request->method == RTSP_METHOD_TEARDOWN) {
        if (session->state == SESSION_CONNECTED || !session_matches(session, request)) {
            return send_response(session->control_socket, request->cseq, 454,
                                 "Session Not Found", NULL, NULL);
        }
        bool sent = send_response(session->control_socket, request->cseq, 200,
                "OK", NULL, NULL);
        *close_session = true;
        return sent;
    }
    return send_response(session->control_socket, request->cseq, 501,
                         "Not Implemented", NULL, NULL);
}

static void release_client_slot(void)
{
    xSemaphoreTake(s_client_count_lock, portMAX_DELAY);
    if (s_client_count > 0) --s_client_count;
    xSemaphoreGive(s_client_count_lock);
}

static void rtsp_client_task(void *opaque)
{
    rtsp_session_t *session = opaque;
    struct timeval receive_timeout = {
        .tv_sec = 0,
        .tv_usec = RTSP_RECEIVE_TIMEOUT_MS * 1000,
    };
    (void)setsockopt(session->control_socket, SOL_SOCKET, SO_RCVTIMEO,
                     &receive_timeout, sizeof(receive_timeout));
    session->last_activity_ms = monotonic_ms();
    ESP_LOGI(TAG, "RTSP client connected");

    bool running = true;
    while (running) {
        uint64_t now = monotonic_ms();
        if (!wifi_manager_is_connected()) {
            ESP_LOGW(TAG, "RTSP session closed after Wi-Fi disconnect");
            break;
        }
        if (session->state == SESSION_PLAYING && now >= session->next_frame_ms) {
            if (!stream_one_frame(session)) {
                ESP_LOGE(TAG, "RTP JPEG transmission failed; closing session");
                break;
            }
            session->last_activity_ms = now;
            session->next_frame_ms = now + RTSP_FRAME_INTERVAL_MS;
        }

        const uint8_t *request_data = NULL;
        size_t request_length = 0;
        int poll = poll_request(session, &request_data, &request_length);
        if (poll < 0) {
            if (poll == -2) (void)send_response(session->control_socket, 0, 400,
                    "Bad Request", NULL, NULL);
            break;
        }
        if (poll == 1) {
            rtsp_request_t request;
            bool close_session = false;
            if (!rtsp_parse_request(request_data, request_length, &request)) {
                (void)send_response(session->control_socket, 0, 400,
                                    "Bad Request", NULL, NULL);
                break;
            }
            consume_request(session, request_length);
            session->last_activity_ms = now;
            if (!handle_request(session, &request, &close_session)) break;
            if (close_session) running = false;
        } else if (session->state != SESSION_PLAYING &&
                rtsp_session_is_expired(now, session->last_activity_ms,
                                        RTSP_SESSION_TIMEOUT_MS)) {
            ESP_LOGW(TAG, "Inactive RTSP client timed out");
            break;
        }
        vTaskDelay(pdMS_TO_TICKS(5));
    }

    if (session->rtp_socket >= 0) close(session->rtp_socket);
    if (session->rtcp_socket >= 0) close(session->rtcp_socket);
    close(session->control_socket);
    free(session);
    release_client_slot();
    vTaskDelete(NULL);
}

static bool reserve_client_slot(void)
{
    bool reserved = false;
    xSemaphoreTake(s_client_count_lock, portMAX_DELAY);
    if (s_client_count < RTSP_MAX_CLIENTS) {
        ++s_client_count;
        reserved = true;
    }
    xSemaphoreGive(s_client_count_lock);
    return reserved;
}

static void rtsp_listener_task(void *opaque)
{
    uint16_t port = (uint16_t)(uintptr_t)opaque;
    int listen_socket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listen_socket < 0) {
        ESP_LOGE(TAG, "RTSP socket creation failed: errno=%d", errno);
        vTaskDelete(NULL);
        return;
    }
    int reuse = 1;
    (void)setsockopt(listen_socket, SOL_SOCKET, SO_REUSEADDR, &reuse,
                     sizeof(reuse));
    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_ANY),
        .sin_port = htons(port),
    };
    if (bind(listen_socket, (struct sockaddr *)&address, sizeof(address)) != 0 ||
            listen(listen_socket, RTSP_MAX_CLIENTS) != 0) {
        ESP_LOGE(TAG, "RTSP bind/listen failed on port %u: errno=%d",
                 (unsigned)port, errno);
        close(listen_socket);
        vTaskDelete(NULL);
        return;
    }
    ESP_LOGI(TAG, "RTSP/RTP JPEG server listening on port %u", (unsigned)port);
    while (true) {
        rtsp_session_t *session = calloc(1, sizeof(*session));
        if (session == NULL) {
            vTaskDelay(pdMS_TO_TICKS(100));
            continue;
        }
        socklen_t peer_length = sizeof(session->peer);
        int client = accept(listen_socket, (struct sockaddr *)&session->peer,
                            &peer_length);
        if (client < 0) {
            free(session);
            if (errno == EINTR) continue;
            ESP_LOGE(TAG, "RTSP accept failed: errno=%d", errno);
            break;
        }
        if (!reserve_client_slot()) {
            (void)send_response(client, 0, 453, "Not Enough Bandwidth", NULL,
                                NULL);
            close(client);
            free(session);
            continue;
        }
        session->control_socket = client;
        session->rtp_socket = -1;
        session->rtcp_socket = -1;
        session->config = s_config;
        session->state = SESSION_CONNECTED;
        session->rtp.sequence = (uint16_t)esp_random();
        session->rtp.timestamp = esp_random();
        session->rtp.timestamp_step = 9000;
        session->rtp.ssrc = esp_random();
        if (session->rtp.ssrc == 0) session->rtp.ssrc = 1;
        (void)snprintf(session->session_id, sizeof(session->session_id),
                       "%08X", (unsigned)esp_random());
        if (xTaskCreate(rtsp_client_task, "rtsp_client", RTSP_TASK_STACK_BYTES,
                session, 5, NULL) != pdPASS) {
            close(client);
            free(session);
            release_client_slot();
        }
    }
    close(listen_socket);
    vTaskDelete(NULL);
}

bool camera_server_start(const app_config_t *config)
{
    if (config == NULL || !camera_capture_is_ready()) return false;
    s_config = *config;
    if (s_client_count_lock == NULL) {
        s_client_count_lock = xSemaphoreCreateMutex();
        if (s_client_count_lock == NULL) return false;
    }
    if (!start_snapshot_server(config->snapshot_port)) {
        ESP_LOGE(TAG, "Snapshot HTTP server failed on port %u",
                 (unsigned)config->snapshot_port);
        return false;
    }
    if (xTaskCreate(rtsp_listener_task, "rtsp_listener", 6144,
            (void *)(uintptr_t)config->rtsp_port, 5, NULL) != pdPASS) {
        httpd_stop(s_http_server);
        s_http_server = NULL;
        return false;
    }
    ESP_LOGI(TAG, "Snapshot endpoint ready on port %u for node=%s room=%s",
             (unsigned)config->snapshot_port, config->node_id,
             config->room_id);
    return true;
}
