#include "provisioning_parser.h"

#include <string.h>

#ifdef ESP_PLATFORM
#include "lwip/inet.h"
#else
#include <arpa/inet.h>
#endif

static bool valid_utf8_without_controls(const uint8_t *value, size_t length)
{
    size_t index = 0;
    while (index < length) {
        uint8_t first = value[index++];
        if (first < 0x80) {
            if (first < 0x20 || first == 0x7F) return false;
            continue;
        }
        uint32_t codepoint;
        size_t continuation_count;
        if (first >= 0xC2 && first <= 0xDF) {
            codepoint = first & 0x1F;
            continuation_count = 1;
        } else if (first >= 0xE0 && first <= 0xEF) {
            codepoint = first & 0x0F;
            continuation_count = 2;
        } else if (first >= 0xF0 && first <= 0xF4) {
            codepoint = first & 0x07;
            continuation_count = 3;
        } else {
            return false;
        }
        if (continuation_count > length - index) return false;
        for (size_t i = 0; i < continuation_count; i++) {
            uint8_t continuation = value[index++];
            if ((continuation & 0xC0) != 0x80) return false;
            codepoint = (codepoint << 6) | (continuation & 0x3F);
        }
        if ((continuation_count == 2 && codepoint < 0x800) ||
                (continuation_count == 3 && codepoint < 0x10000) ||
                (codepoint >= 0x80 && codepoint <= 0x9F) ||
                (codepoint >= 0xD800 && codepoint <= 0xDFFF) ||
                codepoint > 0x10FFFF) {
            return false;
        }
    }
    return true;
}

static bool parse_field(const uint8_t **cursor,
                        size_t *remaining,
                        char *destination,
                        size_t destination_size,
                        size_t minimum_length,
                        bool require_ascii)
{
    if (cursor == NULL || *cursor == NULL || remaining == NULL ||
            destination == NULL || destination_size == 0) return false;
    const uint8_t *terminator = memchr(*cursor, '\0', *remaining);
    if (terminator == NULL) return false;
    size_t length = (size_t)(terminator - *cursor);
    if (length < minimum_length || length >= destination_size ||
            !valid_utf8_without_controls(*cursor, length)) {
        return false;
    }
    if (require_ascii) {
        for (size_t i = 0; i < length; i++) {
            if ((*cursor)[i] >= 0x80) return false;
        }
    }
    memcpy(destination, *cursor, length);
    destination[length] = '\0';
    *cursor += length + 1;
    *remaining -= length + 1;
    return true;
}

static bool valid_identifier(const char *value)
{
    if (value == NULL || value[0] == '\0') return false;
    for (size_t i = 0; value[i] != '\0'; i++) {
        char character = value[i];
        if (!((character >= 'A' && character <= 'Z') ||
                (character >= 'a' && character <= 'z') ||
                (character >= '0' && character <= '9') ||
                character == '_' || character == '-')) {
            return false;
        }
    }
    return true;
}

bool provisioning_parse_config(const uint8_t *message,
                               size_t message_length,
                               app_config_t *configuration)
{
    if (message == NULL || configuration == NULL || message_length < 5 ||
            message_length > 255) {
        return false;
    }
    memset(configuration, 0, sizeof(*configuration));
    const uint8_t *cursor = message;
    size_t remaining = message_length;
    if (!parse_field(&cursor, &remaining, configuration->wifi_ssid,
                sizeof(configuration->wifi_ssid), 1, false) ||
            !parse_field(&cursor, &remaining, configuration->wifi_pass,
                sizeof(configuration->wifi_pass), 8, true) ||
            !parse_field(&cursor, &remaining, configuration->gateway_ip,
                sizeof(configuration->gateway_ip), 7, true) ||
            !parse_field(&cursor, &remaining, configuration->node_id,
                sizeof(configuration->node_id), 1, true) ||
            !parse_field(&cursor, &remaining, configuration->room_id,
                sizeof(configuration->room_id), 1, true) ||
            !parse_field(&cursor, &remaining, configuration->auth_key,
                sizeof(configuration->auth_key), 32, true) ||
            remaining != 0 || !valid_identifier(configuration->node_id) ||
            !valid_identifier(configuration->room_id)) {
        memset(configuration, 0, sizeof(*configuration));
        return false;
    }
    struct in_addr address;
#ifdef ESP_PLATFORM
    if (inet_aton(configuration->gateway_ip, &address) == 0) {
#else
    if (inet_pton(AF_INET, configuration->gateway_ip, &address) != 1) {
#endif
        memset(configuration, 0, sizeof(*configuration));
        return false;
    }
    configuration->provisioned = 1;
    return true;
}
