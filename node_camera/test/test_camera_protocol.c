#include "camera_protocol.h"
#include "provisioning_parser.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

typedef struct {
    uint8_t packets[8][RTP_JPEG_MAX_PACKET_BYTES];
    size_t lengths[8];
    size_t count;
} packet_collector_t;

static bool collect_packet(const uint8_t *packet, size_t length, void *context)
{
    packet_collector_t *collector = context;
    if (collector->count >= 8 || length > sizeof(collector->packets[0]))
        return false;
    memcpy(collector->packets[collector->count], packet, length);
    collector->lengths[collector->count++] = length;
    return true;
}

static size_t build_test_jpeg(uint8_t *output, size_t capacity,
                              size_t scan_length)
{
    const size_t needed = 2 + 2 + 2 + 130 + 2 + 2 + 15 + 2 + 2 + 10 +
            scan_length + 2;
    assert(capacity >= needed);
    size_t position = 0;
    output[position++] = 0xff; output[position++] = 0xd8;
    output[position++] = 0xff; output[position++] = 0xdb;
    output[position++] = 0x00; output[position++] = 0x84;
    output[position++] = 0x00;
    for (unsigned i = 0; i < 64; ++i) output[position++] = (uint8_t)(i + 1);
    output[position++] = 0x01;
    for (unsigned i = 0; i < 64; ++i) output[position++] = (uint8_t)(64 - i);
    output[position++] = 0xff; output[position++] = 0xc0;
    output[position++] = 0x00; output[position++] = 0x11;
    output[position++] = 8;
    output[position++] = 0x00; output[position++] = 0xf0;
    output[position++] = 0x01; output[position++] = 0x40;
    output[position++] = 3;
    output[position++] = 1; output[position++] = 0x22; output[position++] = 0;
    output[position++] = 2; output[position++] = 0x11; output[position++] = 1;
    output[position++] = 3; output[position++] = 0x11; output[position++] = 1;
    output[position++] = 0xff; output[position++] = 0xda;
    output[position++] = 0x00; output[position++] = 0x0c;
    output[position++] = 3;
    output[position++] = 1; output[position++] = 0x00;
    output[position++] = 2; output[position++] = 0x11;
    output[position++] = 3; output[position++] = 0x11;
    output[position++] = 0; output[position++] = 63; output[position++] = 0;
    for (size_t i = 0; i < scan_length; ++i)
        output[position++] = (uint8_t)((i % 250) + 1);
    output[position++] = 0xff; output[position++] = 0xd9;
    assert(position == needed);
    return position;
}

static uint16_t be16(const uint8_t *value)
{
    return (uint16_t)(((uint16_t)value[0] << 8) | value[1]);
}

static uint32_t be32(const uint8_t *value)
{
    return ((uint32_t)value[0] << 24) | ((uint32_t)value[1] << 16) |
            ((uint32_t)value[2] << 8) | value[3];
}

static void test_rtsp_parser(void)
{
    static const char setup[] =
            "SETUP rtsp://192.168.1.9:8554/camera/trackID=0 RTSP/1.0\r\n"
            "CSeq: 3\r\n"
            "Transport: RTP/AVP;unicast;client_port=5000-5001\r\n"
            "Authorization: Bearer 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\r\n\r\n";
    rtsp_request_t request;
    assert(rtsp_parse_request((const uint8_t *)setup, sizeof(setup) - 1,
                              &request));
    assert(request.method == RTSP_METHOD_SETUP);
    assert(request.cseq == 3);
    assert(request.client_rtp_port == 5000);
    assert(request.client_rtcp_port == 5001);
    assert(request.transport_supported);
    assert(request.has_authorization);

    static const char play[] =
            "PLAY rtsp://camera/live RTSP/1.0\r\nCSeq: 4\r\n"
            "Session: A1B2C3D4;timeout=30\r\n\r\n";
    assert(rtsp_parse_request((const uint8_t *)play, sizeof(play) - 1,
                              &request));
    assert(request.method == RTSP_METHOD_PLAY);
    assert(strcmp(request.session_id, "A1B2C3D4") == 0);

    static const char no_cseq[] =
            "OPTIONS * RTSP/1.0\r\nUser-Agent: test\r\n\r\n";
    assert(!rtsp_parse_request((const uint8_t *)no_cseq,
                               sizeof(no_cseq) - 1, &request));
    static const char interleaved[] =
            "SETUP rtsp://camera/trackID=0 RTSP/1.0\r\nCSeq: 2\r\n"
            "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n\r\n";
    assert(rtsp_parse_request((const uint8_t *)interleaved,
                              sizeof(interleaved) - 1, &request));
    assert(request.has_transport && !request.transport_supported);
    static const char bad_ports[] =
            "SETUP rtsp://camera/trackID=0 RTSP/1.0\r\nCSeq: 2\r\n"
            "Transport: RTP/AVP;unicast;client_port=5000-5002\r\n\r\n";
    assert(!rtsp_parse_request((const uint8_t *)bad_ports,
                               sizeof(bad_ports) - 1, &request));
    static const char body[] =
            "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nContent-Length: 1\r\n\r\nX";
    assert(!rtsp_parse_request((const uint8_t *)body, sizeof(body) - 1,
                               &request));
}

static void test_sdp_and_timeout(void)
{
    char sdp[768];
    assert(rtsp_generate_sdp(sdp, sizeof(sdp), "192.168.1.40", "camera-01",
                             "living-room", 1234, 10));
    assert(strstr(sdp, "m=video 0 RTP/AVP 26\r\n") != NULL);
    assert(strstr(sdp, "a=rtpmap:26 JPEG/90000\r\n") != NULL);
    assert(strstr(sdp, "a=control:trackID=0\r\n") != NULL);
    assert(strstr(sdp, "a=x-node-id:camera-01\r\n") != NULL);
    assert(strstr(sdp, "a=x-room-id:living-room\r\n") != NULL);
    assert(!rtsp_generate_sdp(sdp, 20, "192.168.1.40", "camera-01",
                              "living-room", 1234, 10));
    assert(!rtsp_session_is_expired(29999, 0, 30000));
    assert(rtsp_session_is_expired(30000, 0, 30000));
    assert(rtsp_session_is_expired(1, 2, 30000));
}

static void test_rtp_jpeg_packetization(void)
{
    uint8_t jpeg[4096];
    size_t jpeg_length = build_test_jpeg(jpeg, sizeof(jpeg), 2600);
    rtp_jpeg_state_t state = {
        .sequence = 100,
        .timestamp = 90000,
        .timestamp_step = 9000,
        .ssrc = 0x11223344,
    };
    packet_collector_t collector = {0};
    assert(rtp_jpeg_packetize(jpeg, jpeg_length, 600, &state,
                              collect_packet, &collector));
    assert(collector.count > 1);
    size_t reconstructed = 0;
    uint8_t scan[2600];
    for (size_t i = 0; i < collector.count; ++i) {
        const uint8_t *packet = collector.packets[i];
        assert((packet[0] >> 6) == 2);
        assert((packet[1] & 0x7f) == RTP_JPEG_PAYLOAD_TYPE);
        assert(((packet[1] & 0x80) != 0) == (i + 1 == collector.count));
        assert(be16(packet + 2) == 100 + i);
        assert(be32(packet + 4) == 90000);
        assert(be32(packet + 8) == 0x11223344);
        size_t offset = ((size_t)packet[13] << 16) |
                ((size_t)packet[14] << 8) | packet[15];
        assert(offset == reconstructed);
        assert(packet[16] == 1);
        assert(packet[17] == 255);
        assert(packet[18] == 40);
        assert(packet[19] == 30);
        size_t header = 20;
        if (i == 0) {
            assert(be16(packet + 22) == 128);
            header += 132;
        }
        size_t payload = collector.lengths[i] - header;
        memcpy(scan + reconstructed, packet + header, payload);
        reconstructed += payload;
    }
    assert(reconstructed == sizeof(scan));
    size_t scan_start = jpeg_length - sizeof(scan) - 2;
    assert(memcmp(scan, jpeg + scan_start, sizeof(scan)) == 0);
    assert(state.sequence == 100 + collector.count);
    assert(state.timestamp == 99000);

    jpeg[0] = 0;
    assert(!rtp_jpeg_packetize(jpeg, jpeg_length, 600, &state,
                               collect_packet, &collector));
}

static void test_provisioning(void)
{
    static const uint8_t valid[] =
            "HouseWiFi\0" "password123\0" "192.168.1.5\0" "8080\0"
            "camera-01\0" "living-room\0" "80\0" "8554\0"
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\0";
    app_config_t config;
    assert(provisioning_parse_config(valid, sizeof(valid) - 1, &config));
    assert(app_config_is_valid(&config));
    assert(strcmp(config.node_id, "camera-01") == 0);
    assert(config.gateway_port == 8080);
    assert(config.snapshot_port == 80);
    assert(config.rtsp_port == 8554);
    assert(!provisioning_parse_config(valid, sizeof(valid) - 2, &config));

    uint8_t trailing[sizeof(valid) + 1];
    memcpy(trailing, valid, sizeof(valid) - 1);
    trailing[sizeof(valid) - 1] = 'X';
    assert(!provisioning_parse_config(trailing, sizeof(valid), &config));

    static const uint8_t weak_key[] =
            "HouseWiFi\0" "password123\0" "192.168.1.5\0" "8080\0"
            "camera-01\0" "living-room\0" "80\0" "8554\0" "short\0";
    assert(!provisioning_parse_config(weak_key, sizeof(weak_key) - 1, &config));

    assert(constant_time_secret_matches(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            65));
    assert(!constant_time_secret_matches(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "1123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            65));
}

int main(void)
{
    test_rtsp_parser();
    test_sdp_and_timeout();
    test_rtp_jpeg_packetization();
    test_provisioning();
    puts("camera protocol safety tests passed");
    return 0;
}
