#include "provisioning_parser.h"

#include "camera_protocol.h"

#include <ctype.h>
#include <string.h>

#define PROVISION_FIELD_COUNT 9

typedef struct {
    const uint8_t *data;
    size_t length;
} field_view_t;

static bool valid_utf8_printable(const uint8_t *data, size_t length)
{
    size_t i = 0;
    while (i < length) {
        uint8_t first = data[i++];
        if (first < 0x20 || first == 0x7f) return false;
        if (first < 0x80) continue;
        unsigned continuation_count;
        uint32_t codepoint;
        if (first >= 0xc2 && first <= 0xdf) {
            continuation_count = 1;
            codepoint = first & 0x1f;
        } else if (first >= 0xe0 && first <= 0xef) {
            continuation_count = 2;
            codepoint = first & 0x0f;
        } else if (first >= 0xf0 && first <= 0xf4) {
            continuation_count = 3;
            codepoint = first & 0x07;
        } else {
            return false;
        }
        if (i + continuation_count > length) return false;
        for (unsigned j = 0; j < continuation_count; ++j) {
            uint8_t next = data[i++];
            if ((next & 0xc0) != 0x80) return false;
            codepoint = (codepoint << 6) | (next & 0x3f);
        }
        if ((continuation_count == 2 && codepoint < 0x800) ||
                (continuation_count == 3 && codepoint < 0x10000) ||
                codepoint > 0x10ffff ||
                (codepoint >= 0xd800 && codepoint <= 0xdfff)) return false;
    }
    return true;
}

static bool copy_field(char *output, size_t capacity, field_view_t field,
                       size_t minimum_length)
{
    if (field.length < minimum_length || field.length >= capacity ||
            !valid_utf8_printable(field.data, field.length)) return false;
    memcpy(output, field.data, field.length);
    output[field.length] = '\0';
    return true;
}

static bool parse_port(field_view_t field, uint16_t *port)
{
    if (port == NULL || field.length == 0 || field.length > 5) return false;
    uint32_t value = 0;
    for (size_t i = 0; i < field.length; ++i) {
        if (field.data[i] < '0' || field.data[i] > '9') return false;
        value = value * 10U + (uint32_t)(field.data[i] - '0');
        if (value > 65535U) return false;
    }
    if (value == 0) return false;
    *port = (uint16_t)value;
    return true;
}

static bool auth_key_is_valid(const char *key)
{
    if (key == NULL || strnlen(key, 65) != 64) return false;
    for (size_t i = 0; i < 64; ++i) {
        if (!isxdigit((unsigned char)key[i])) return false;
    }
    return true;
}

static bool ipv4_is_valid(const char *text)
{
    if (text == NULL) return false;
    size_t length = strnlen(text, 16);
    if (length < 7 || length >= 16) return false;
    unsigned octets = 0;
    size_t offset = 0;
    while (offset < length) {
        if (++octets > 4) return false;
        unsigned value = 0;
        size_t digits = 0;
        while (offset < length && text[offset] != '.') {
            if (text[offset] < '0' || text[offset] > '9' || ++digits > 3)
                return false;
            value = value * 10U + (unsigned)(text[offset++] - '0');
            if (value > 255) return false;
        }
        if (digits == 0 || (digits > 1 && text[offset - digits] == '0')) return false;
        if (offset < length) ++offset;
    }
    return octets == 4 && text[length - 1] != '.';
}

bool app_config_is_valid(const app_config_t *config)
{
    if (config == NULL || config->provisioned != 1 ||
            strnlen(config->wifi_ssid, sizeof(config->wifi_ssid)) == 0 ||
            strnlen(config->wifi_ssid, sizeof(config->wifi_ssid)) >=
                    sizeof(config->wifi_ssid) ||
            strnlen(config->wifi_pass, sizeof(config->wifi_pass)) < 8 ||
            strnlen(config->wifi_pass, sizeof(config->wifi_pass)) > 63 ||
            !camera_identity_value_is_valid(config->node_id,
                    sizeof(config->node_id)) ||
            !camera_identity_value_is_valid(config->room_id,
                    sizeof(config->room_id)) ||
            !auth_key_is_valid(config->auth_key) ||
            config->gateway_port == 0 || config->snapshot_port == 0 ||
            config->rtsp_port == 0 || config->snapshot_port == config->rtsp_port) {
        return false;
    }
    return ipv4_is_valid(config->gateway_ip);
}

bool provisioning_parse_config(const uint8_t *data, size_t length,
                               app_config_t *config)
{
    if (data == NULL || config == NULL || length < PROVISION_FIELD_COUNT * 2 ||
            length > 512) return false;
    field_view_t fields[PROVISION_FIELD_COUNT];
    size_t offset = 0;
    for (size_t i = 0; i < PROVISION_FIELD_COUNT; ++i) {
        const uint8_t *terminator = memchr(data + offset, '\0', length - offset);
        if (terminator == NULL) return false;
        fields[i].data = data + offset;
        fields[i].length = (size_t)(terminator - (data + offset));
        offset += fields[i].length + 1;
    }
    if (offset != length) return false;

    app_config_t parsed;
    memset(&parsed, 0, sizeof(parsed));
    if (!copy_field(parsed.wifi_ssid, sizeof(parsed.wifi_ssid), fields[0], 1) ||
            !copy_field(parsed.wifi_pass, sizeof(parsed.wifi_pass), fields[1], 8) ||
            !copy_field(parsed.gateway_ip, sizeof(parsed.gateway_ip), fields[2], 7) ||
            !parse_port(fields[3], &parsed.gateway_port) ||
            !copy_field(parsed.node_id, sizeof(parsed.node_id), fields[4], 1) ||
            !copy_field(parsed.room_id, sizeof(parsed.room_id), fields[5], 1) ||
            !parse_port(fields[6], &parsed.snapshot_port) ||
            !parse_port(fields[7], &parsed.rtsp_port) ||
            !copy_field(parsed.auth_key, sizeof(parsed.auth_key), fields[8], 64)) {
        return false;
    }
    parsed.provisioned = 1;
    if (!app_config_is_valid(&parsed)) return false;
    *config = parsed;
    return true;
}
