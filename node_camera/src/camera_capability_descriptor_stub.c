/*
 * Responsibility: placeholder for building the JSON capability descriptor
 * advertised by the camera node.
 * Per ARCHITECTURE.md §4, the descriptor contains only capability fields —
 * Node Identity fields (nodeId, homeId, location) are separate.
 */
#include "camera_capability_descriptor.h"

#include <ctype.h>
#include <stdio.h>
#include <string.h>

static const char CAMERA_DESCRIPTOR_JSON[] =
    "{"
    "\"schemaVersion\":1,"
    "\"nodeType\":\"camera.rtsp\","
    "\"displayName\":\"Camera Node\","
    "\"firmware\":{\"version\":\"0.0.0-stub\",\"minGatewayDescriptorVersion\":1},"
    "\"transports\":{\"bleMesh\":{\"vendorModel\":\"[TBD: allocate company/model ID]\",\"supportsProvisioning\":true},\"wifi\":{\"requiredFor\":[\"rtspStream\",\"otaImageTransfer\"],\"optionalFor\":[]}},"
    "\"metrics\":[{\"key\":\"streamStatus\",\"unit\":\"state\",\"source\":\"ESP32-CAM\",\"role\":\"status\",\"type\":\"string\"}],"
    "\"events\":[{\"key\":\"camera.stream.started\",\"severity\":\"info\",\"payloadSchema\":\"generic-key-value\"},{\"key\":\"camera.stream.lost\",\"severity\":\"warning\",\"payloadSchema\":\"generic-key-value\"}],"
    "\"actions\":[{\"key\":\"camera.restartStream\",\"requiresRole\":\"device_admin\"}],"
    "\"ota\":{\"channel\":\"stable\",\"imageTransport\":\"wifi\",\"controlTransport\":\"ble_mesh\"},"
    "\"video\":{\"protocol\":\"rtsp\",\"multiClient\":true,\"snapshotOwner\":\"gateway\"}"
    "}";

static int extract_string_value(const char *json, const char *key, char *out, size_t out_size)
{
    char pattern[96];
    const char *cursor;
    const char *quote;
    size_t len;

    if (json == NULL || key == NULL || out == NULL || out_size == 0) {
        return 0;
    }
    (void)snprintf(pattern, sizeof(pattern), "\"%s\"", key);
    cursor = strstr(json, pattern);
    if (cursor == NULL) {
        return 0;
    }
    cursor = strchr(cursor + strlen(pattern), ':');
    if (cursor == NULL) {
        return 0;
    }
    cursor++;
    while (*cursor != '\0' && isspace((unsigned char)*cursor)) {
        cursor++;
    }
    if (*cursor != '"') {
        return 0;
    }
    quote = ++cursor;
    while (*quote != '\0' && *quote != '"') {
        quote++;
    }
    if (*quote != '"') {
        return 0;
    }
    len = (size_t)(quote - cursor);
    if (len >= out_size) {
        return 0;
    }
    memcpy(out, cursor, len);
    out[len] = '\0';
    return 1;
}

static int extract_int_value(const char *json, const char *key, int *out)
{
    char pattern[96];
    const char *cursor;
    int value = 0;

    if (json == NULL || key == NULL || out == NULL) {
        return 0;
    }
    (void)snprintf(pattern, sizeof(pattern), "\"%s\"", key);
    cursor = strstr(json, pattern);
    if (cursor == NULL) {
        return 0;
    }
    cursor = strchr(cursor + strlen(pattern), ':');
    if (cursor == NULL) {
        return 0;
    }
    cursor++;
    while (*cursor != '\0' && isspace((unsigned char)*cursor)) {
        cursor++;
    }
    if (sscanf(cursor, "%d", &value) != 1) {
        return 0;
    }
    *out = value;
    return 1;
}

const char *camera_capability_descriptor_stub(void)
{
    return camera_capability_descriptor_json();
}

const char *camera_capability_descriptor_json(void)
{
    return CAMERA_DESCRIPTOR_JSON;
}

int camera_capability_descriptor_decode(const char *json, camera_capability_descriptor_t *descriptor)
{
    if (descriptor == NULL) {
        return 0;
    }

    memset(descriptor, 0, sizeof(*descriptor));

    return extract_string_value(json, "nodeType", descriptor->node_type, sizeof(descriptor->node_type)) &&
           extract_int_value(json, "schemaVersion", &descriptor->schema_version) &&
           descriptor->schema_version == 1 &&
           extract_string_value(json, "displayName", descriptor->display_name, sizeof(descriptor->display_name)) &&
           extract_string_value(json, "version", descriptor->firmware_version, sizeof(descriptor->firmware_version)) &&
           strstr(json, "\"metrics\"") != NULL &&
           strstr(json, "\"events\"") != NULL &&
           strstr(json, "\"actions\"") != NULL &&
           strstr(json, "\"transports\"") != NULL;
}

int camera_capability_descriptor_round_trip_test(void)
{
    camera_capability_descriptor_t decoded;

    if (!camera_capability_descriptor_decode(camera_capability_descriptor_json(), &decoded)) {
        return 0;
    }

    return strcmp(decoded.node_type, "camera.rtsp") == 0 &&
           decoded.schema_version == 1 &&
           strcmp(decoded.display_name, "Camera Node") == 0 &&
           strcmp(decoded.firmware_version, "0.0.0-stub") == 0;
}
