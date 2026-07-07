#ifndef CAMERA_PROTOCOL_H
#define CAMERA_PROTOCOL_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define RTSP_REQUEST_MAX_BYTES 2048
#define RTSP_URI_MAX_BYTES 256
#define RTSP_SESSION_ID_MAX_BYTES 32
#define RTSP_AUTH_VALUE_MAX_BYTES 96
#define RTP_JPEG_MAX_PACKET_BYTES 1500
#define RTP_JPEG_PAYLOAD_TYPE 26

typedef enum {
    RTSP_METHOD_OPTIONS,
    RTSP_METHOD_DESCRIBE,
    RTSP_METHOD_SETUP,
    RTSP_METHOD_PLAY,
    RTSP_METHOD_TEARDOWN,
    RTSP_METHOD_UNKNOWN,
} rtsp_method_t;

typedef struct {
    rtsp_method_t method;
    char uri[RTSP_URI_MAX_BYTES];
    uint32_t cseq;
    bool has_transport;
    bool transport_supported;
    uint16_t client_rtp_port;
    uint16_t client_rtcp_port;
    bool has_session;
    char session_id[RTSP_SESSION_ID_MAX_BYTES];
    bool has_authorization;
    char authorization[RTSP_AUTH_VALUE_MAX_BYTES];
} rtsp_request_t;

typedef struct {
    uint16_t sequence;
    uint32_t timestamp;
    uint32_t timestamp_step;
    uint32_t ssrc;
} rtp_jpeg_state_t;

typedef bool (*rtp_packet_sender_t)(const uint8_t *packet, size_t length,
                                    void *context);

bool rtsp_parse_request(const uint8_t *data, size_t length,
                        rtsp_request_t *request);
bool rtsp_generate_sdp(char *output, size_t output_size,
                       const char *server_ipv4, const char *node_id,
                       const char *room_id, uint32_t session_id,
                       unsigned frames_per_second);
bool rtsp_session_is_expired(uint64_t now_ms, uint64_t last_activity_ms,
                             uint64_t timeout_ms);
bool camera_identity_value_is_valid(const char *value, size_t capacity);
bool constant_time_secret_matches(const char *expected, const char *provided,
                                  size_t capacity);

bool rtp_jpeg_packetize(const uint8_t *jpeg, size_t jpeg_length,
                        size_t maximum_packet_size, rtp_jpeg_state_t *state,
                        rtp_packet_sender_t sender, void *context);

#endif
