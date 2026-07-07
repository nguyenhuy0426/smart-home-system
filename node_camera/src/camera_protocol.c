#include "camera_protocol.h"

#include <ctype.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

typedef struct {
    const uint8_t *scan;
    size_t scan_length;
    uint16_t width;
    uint16_t height;
    uint8_t type;
    uint8_t quantization_tables[128];
} jpeg_payload_view_t;

static uint16_t read_be16(const uint8_t *value)
{
    return (uint16_t)(((uint16_t)value[0] << 8) | value[1]);
}

static void write_be16(uint8_t *output, uint16_t value)
{
    output[0] = (uint8_t)(value >> 8);
    output[1] = (uint8_t)value;
}

static void write_be32(uint8_t *output, uint32_t value)
{
    output[0] = (uint8_t)(value >> 24);
    output[1] = (uint8_t)(value >> 16);
    output[2] = (uint8_t)(value >> 8);
    output[3] = (uint8_t)value;
}

static bool ascii_equal_ignore_case(const uint8_t *left, size_t left_length,
                                    const char *right)
{
    size_t right_length = strlen(right);
    if (left_length != right_length) return false;
    for (size_t i = 0; i < left_length; ++i) {
        if (tolower((unsigned char)left[i]) !=
                tolower((unsigned char)right[i])) {
            return false;
        }
    }
    return true;
}

static const uint8_t *find_bytes(const uint8_t *data, size_t length,
                                 const char *needle)
{
    size_t needle_length = strlen(needle);
    if (needle_length == 0 || needle_length > length) return NULL;
    for (size_t i = 0; i <= length - needle_length; ++i) {
        if (memcmp(data + i, needle, needle_length) == 0) return data + i;
    }
    return NULL;
}

static const uint8_t *find_bytes_ignore_case(const uint8_t *data, size_t length,
                                             const char *needle)
{
    size_t needle_length = strlen(needle);
    if (needle_length == 0 || needle_length > length) return NULL;
    for (size_t i = 0; i <= length - needle_length; ++i) {
        if (ascii_equal_ignore_case(data + i, needle_length, needle))
            return data + i;
    }
    return NULL;
}

static bool parse_decimal(const uint8_t *data, size_t length,
                          uint32_t maximum, uint32_t *value)
{
    if (data == NULL || value == NULL || length == 0) return false;
    uint32_t parsed = 0;
    for (size_t i = 0; i < length; ++i) {
        if (data[i] < '0' || data[i] > '9') return false;
        uint32_t digit = (uint32_t)(data[i] - '0');
        if (parsed > (maximum - digit) / 10U) return false;
        parsed = parsed * 10U + digit;
    }
    *value = parsed;
    return true;
}

static void trim_ascii(const uint8_t **data, size_t *length)
{
    while (*length > 0 && ((*data)[0] == ' ' || (*data)[0] == '\t')) {
        ++*data;
        --*length;
    }
    while (*length > 0 && ((*data)[*length - 1] == ' ' ||
            (*data)[*length - 1] == '\t')) {
        --*length;
    }
}

static bool copy_header_value(char *output, size_t capacity,
                              const uint8_t *value, size_t value_length)
{
    trim_ascii(&value, &value_length);
    if (value_length == 0 || value_length >= capacity) return false;
    for (size_t i = 0; i < value_length; ++i) {
        if (value[i] < 0x20 || value[i] > 0x7e) return false;
    }
    memcpy(output, value, value_length);
    output[value_length] = '\0';
    return true;
}

static rtsp_method_t parse_method(const uint8_t *data, size_t length)
{
    if (ascii_equal_ignore_case(data, length, "OPTIONS")) return RTSP_METHOD_OPTIONS;
    if (ascii_equal_ignore_case(data, length, "DESCRIBE")) return RTSP_METHOD_DESCRIBE;
    if (ascii_equal_ignore_case(data, length, "SETUP")) return RTSP_METHOD_SETUP;
    if (ascii_equal_ignore_case(data, length, "PLAY")) return RTSP_METHOD_PLAY;
    if (ascii_equal_ignore_case(data, length, "TEARDOWN")) return RTSP_METHOD_TEARDOWN;
    return RTSP_METHOD_UNKNOWN;
}

static bool parse_transport(const uint8_t *value, size_t length,
                            bool *supported, uint16_t *rtp_port,
                            uint16_t *rtcp_port)
{
    *supported = find_bytes_ignore_case(value, length, "RTP/AVP") != NULL &&
            find_bytes_ignore_case(value, length, "unicast") != NULL &&
            find_bytes_ignore_case(value, length, "interleaved=") == NULL &&
            find_bytes_ignore_case(value, length, "RTP/AVP/TCP") == NULL;
    if (!*supported) return true;
    const uint8_t *ports = find_bytes_ignore_case(value, length, "client_port=");
    if (ports == NULL) return false;
    ports += strlen("client_port=");
    size_t remaining = length - (size_t)(ports - value);
    const uint8_t *dash = memchr(ports, '-', remaining);
    if (dash == NULL) return false;
    const uint8_t *end = dash + 1;
    while ((size_t)(end - value) < length && *end >= '0' && *end <= '9') ++end;
    uint32_t first = 0;
    uint32_t second = 0;
    if (!parse_decimal(ports, (size_t)(dash - ports), UINT16_MAX, &first) ||
            !parse_decimal(dash + 1, (size_t)(end - dash - 1), UINT16_MAX,
                           &second) || first < 1024 || second != first + 1U) {
        return false;
    }
    *rtp_port = (uint16_t)first;
    *rtcp_port = (uint16_t)second;
    return true;
}

bool rtsp_parse_request(const uint8_t *data, size_t length,
                        rtsp_request_t *request)
{
    if (data == NULL || request == NULL || length == 0 ||
            length > RTSP_REQUEST_MAX_BYTES ||
            find_bytes(data, length, "\r\n\r\n") == NULL) {
        return false;
    }
    memset(request, 0, sizeof(*request));

    const uint8_t *line_end = find_bytes(data, length, "\r\n");
    if (line_end == NULL) return false;
    size_t first_line_length = (size_t)(line_end - data);
    const uint8_t *space1 = memchr(data, ' ', first_line_length);
    if (space1 == NULL) return false;
    size_t after_first = first_line_length - (size_t)(space1 + 1 - data);
    const uint8_t *space2 = memchr(space1 + 1, ' ', after_first);
    if (space2 == NULL || memchr(space2 + 1, ' ',
            first_line_length - (size_t)(space2 + 1 - data)) != NULL) {
        return false;
    }
    size_t method_length = (size_t)(space1 - data);
    if (method_length == 0 || method_length > 16) return false;
    for (size_t i = 0; i < method_length; ++i) {
        if (!isalpha((unsigned char)data[i])) return false;
    }
    request->method = parse_method(data, method_length);
    size_t uri_length = (size_t)(space2 - space1 - 1);
    if (uri_length == 0 || uri_length >= sizeof(request->uri) ||
            !ascii_equal_ignore_case(space2 + 1,
                    first_line_length - (size_t)(space2 + 1 - data),
                    "RTSP/1.0")) {
        return false;
    }
    for (size_t i = 0; i < uri_length; ++i) {
        if (space1[1 + i] < 0x21 || space1[1 + i] > 0x7e) return false;
    }
    memcpy(request->uri, space1 + 1, uri_length);
    request->uri[uri_length] = '\0';

    bool found_cseq = false;
    const uint8_t *cursor = line_end + 2;
    const uint8_t *limit = data + length;
    while (cursor < limit) {
        line_end = find_bytes(cursor, (size_t)(limit - cursor), "\r\n");
        if (line_end == NULL) return false;
        size_t line_length = (size_t)(line_end - cursor);
        if (line_length == 0) break;
        const uint8_t *colon = memchr(cursor, ':', line_length);
        if (colon == NULL) return false;
        const uint8_t *value = colon + 1;
        size_t value_length = line_length - (size_t)(value - cursor);
        trim_ascii(&value, &value_length);
        size_t name_length = (size_t)(colon - cursor);
        if (ascii_equal_ignore_case(cursor, name_length, "CSeq")) {
            uint32_t cseq = 0;
            if (found_cseq || !parse_decimal(value, value_length, INT_MAX, &cseq) ||
                    cseq == 0) return false;
            request->cseq = cseq;
            found_cseq = true;
        } else if (ascii_equal_ignore_case(cursor, name_length, "Transport")) {
            if (request->has_transport || !parse_transport(value, value_length,
                    &request->transport_supported, &request->client_rtp_port,
                    &request->client_rtcp_port)) {
                return false;
            }
            request->has_transport = true;
        } else if (ascii_equal_ignore_case(cursor, name_length, "Session")) {
            const uint8_t *semicolon = memchr(value, ';', value_length);
            size_t token_length = semicolon == NULL ? value_length :
                    (size_t)(semicolon - value);
            if (request->has_session || !copy_header_value(request->session_id,
                    sizeof(request->session_id), value, token_length)) return false;
            request->has_session = true;
        } else if (ascii_equal_ignore_case(cursor, name_length, "Authorization")) {
            if (request->has_authorization ||
                    !copy_header_value(request->authorization,
                            sizeof(request->authorization), value, value_length)) {
                return false;
            }
            request->has_authorization = true;
        } else if (ascii_equal_ignore_case(cursor, name_length, "Content-Length")) {
            uint32_t content_length = 0;
            if (!parse_decimal(value, value_length, UINT32_MAX, &content_length) ||
                    content_length != 0) return false;
        }
        cursor = line_end + 2;
    }
    if (!found_cseq) return false;
    if (request->method == RTSP_METHOD_SETUP && !request->has_transport) return false;
    return true;
}

bool camera_identity_value_is_valid(const char *value, size_t capacity)
{
    if (value == NULL || capacity < 2) return false;
    size_t length = strnlen(value, capacity);
    if (length == 0 || length >= capacity) return false;
    for (size_t i = 0; i < length; ++i) {
        unsigned char ch = (unsigned char)value[i];
        if (!(isalnum(ch) || ch == '-' || ch == '_' || ch == '.')) return false;
    }
    return true;
}

bool constant_time_secret_matches(const char *expected, const char *provided,
                                  size_t capacity)
{
    if (expected == NULL || provided == NULL || capacity == 0) return false;
    size_t expected_length = strnlen(expected, capacity);
    size_t provided_length = strnlen(provided, capacity);
    if (expected_length == capacity || provided_length == capacity) return false;
    unsigned difference = (unsigned)(expected_length ^ provided_length);
    for (size_t i = 0; i < capacity; ++i) {
        unsigned char left = i < expected_length ? (unsigned char)expected[i] : 0;
        unsigned char right = i < provided_length ? (unsigned char)provided[i] : 0;
        difference |= (unsigned)(left ^ right);
    }
    return difference == 0;
}

bool rtsp_generate_sdp(char *output, size_t output_size,
                       const char *server_ipv4, const char *node_id,
                       const char *room_id, uint32_t session_id,
                       unsigned frames_per_second)
{
    if (output == NULL || output_size == 0 || server_ipv4 == NULL ||
            !camera_identity_value_is_valid(node_id, 64) ||
            !camera_identity_value_is_valid(room_id, 64) ||
            frames_per_second == 0 || frames_per_second > 30) return false;
    int written = snprintf(output, output_size,
            "v=0\r\n"
            "o=- %u 1 IN IP4 %s\r\n"
            "s=SmartHome Camera %s\r\n"
            "i=Room %s\r\n"
            "c=IN IP4 %s\r\n"
            "t=0 0\r\n"
            "a=control:*\r\n"
            "a=x-node-id:%s\r\n"
            "a=x-room-id:%s\r\n"
            "m=video 0 RTP/AVP 26\r\n"
            "a=rtpmap:26 JPEG/90000\r\n"
            "a=framerate:%u\r\n"
            "a=control:trackID=0\r\n",
            session_id, server_ipv4, node_id, room_id, server_ipv4,
            node_id, room_id, frames_per_second);
    return written > 0 && (size_t)written < output_size;
}

bool rtsp_session_is_expired(uint64_t now_ms, uint64_t last_activity_ms,
                             uint64_t timeout_ms)
{
    return timeout_ms == 0 || now_ms < last_activity_ms ||
            now_ms - last_activity_ms >= timeout_ms;
}

static bool parse_jpeg(const uint8_t *jpeg, size_t length,
                       jpeg_payload_view_t *view)
{
    if (jpeg == NULL || view == NULL || length < 16 || jpeg[0] != 0xff ||
            jpeg[1] != 0xd8 || jpeg[length - 2] != 0xff ||
            jpeg[length - 1] != 0xd9) return false;
    memset(view, 0, sizeof(*view));
    bool have_sof = false;
    bool have_q0 = false;
    bool have_q1 = false;
    size_t position = 2;
    while (position + 4 <= length) {
        if (jpeg[position] != 0xff) return false;
        while (position < length && jpeg[position] == 0xff) ++position;
        if (position >= length) return false;
        uint8_t marker = jpeg[position++];
        if (marker == 0xd9) break;
        if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
        if (position + 2 > length) return false;
        uint16_t segment_length = read_be16(jpeg + position);
        if (segment_length < 2 || position + segment_length > length) return false;
        const uint8_t *segment = jpeg + position + 2;
        size_t data_length = segment_length - 2U;
        if (marker == 0xdb) {
            size_t offset = 0;
            while (offset < data_length) {
                if (data_length - offset < 65) return false;
                uint8_t table_info = segment[offset++];
                uint8_t precision = table_info >> 4;
                uint8_t table_id = table_info & 0x0f;
                if (precision != 0 || table_id > 1) return false;
                memcpy(view->quantization_tables + table_id * 64,
                       segment + offset, 64);
                if (table_id == 0) have_q0 = true;
                if (table_id == 1) have_q1 = true;
                offset += 64;
            }
        } else if (marker == 0xc0) {
            if (data_length < 15 || segment[0] != 8 || segment[5] != 3) return false;
            view->height = read_be16(segment + 1);
            view->width = read_be16(segment + 3);
            uint8_t y_sampling = segment[7];
            if (y_sampling == 0x21) view->type = 0;
            else if (y_sampling == 0x22) view->type = 1;
            else return false;
            have_sof = view->width > 0 && view->height > 0;
        } else if (marker == 0xdd) {
            if (data_length != 2 || read_be16(segment) != 0) return false;
        } else if (marker == 0xda) {
            size_t scan_start = position + segment_length;
            if (!have_sof || !have_q0 || !have_q1 || scan_start >= length - 2)
                return false;
            view->scan = jpeg + scan_start;
            view->scan_length = length - scan_start - 2;
            return view->scan_length > 0;
        }
        position += segment_length;
    }
    return false;
}

bool rtp_jpeg_packetize(const uint8_t *jpeg, size_t jpeg_length,
                        size_t maximum_packet_size, rtp_jpeg_state_t *state,
                        rtp_packet_sender_t sender, void *context)
{
    jpeg_payload_view_t view;
    if (state == NULL || sender == NULL || maximum_packet_size < 256 ||
            maximum_packet_size > RTP_JPEG_MAX_PACKET_BYTES ||
            !parse_jpeg(jpeg, jpeg_length, &view)) return false;
    unsigned width_blocks = (view.width + 7U) / 8U;
    unsigned height_blocks = (view.height + 7U) / 8U;
    if (width_blocks > 255 || height_blocks > 255) return false;

    uint8_t packet[RTP_JPEG_MAX_PACKET_BYTES];
    size_t fragment_offset = 0;
    while (fragment_offset < view.scan_length) {
        bool first = fragment_offset == 0;
        size_t header_length = 12 + 8 + (first ? 4 + 128 : 0);
        if (header_length >= maximum_packet_size) return false;
        size_t chunk_length = view.scan_length - fragment_offset;
        size_t capacity = maximum_packet_size - header_length;
        if (chunk_length > capacity) chunk_length = capacity;
        bool last = fragment_offset + chunk_length == view.scan_length;

        packet[0] = 0x80;
        packet[1] = (uint8_t)((last ? 0x80 : 0) | RTP_JPEG_PAYLOAD_TYPE);
        write_be16(packet + 2, state->sequence);
        write_be32(packet + 4, state->timestamp);
        write_be32(packet + 8, state->ssrc);
        packet[12] = 0;
        packet[13] = (uint8_t)(fragment_offset >> 16);
        packet[14] = (uint8_t)(fragment_offset >> 8);
        packet[15] = (uint8_t)fragment_offset;
        packet[16] = view.type;
        packet[17] = 255;
        packet[18] = (uint8_t)width_blocks;
        packet[19] = (uint8_t)height_blocks;
        size_t payload_position = 20;
        if (first) {
            packet[payload_position++] = 0;
            packet[payload_position++] = 0;
            write_be16(packet + payload_position, 128);
            payload_position += 2;
            memcpy(packet + payload_position, view.quantization_tables, 128);
            payload_position += 128;
        }
        memcpy(packet + payload_position, view.scan + fragment_offset,
               chunk_length);
        if (!sender(packet, payload_position + chunk_length, context)) return false;
        ++state->sequence;
        fragment_offset += chunk_length;
    }
    state->timestamp += state->timestamp_step;
    return true;
}
