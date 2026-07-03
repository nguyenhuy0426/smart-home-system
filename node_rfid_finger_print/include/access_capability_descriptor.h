/*
 * Responsibility: declares the access node capability descriptor surface
 * exchanged over the future BLE Mesh vendor model.
 * Per ARCHITECTURE.md §4, the descriptor contains only capability fields —
 * Node Identity fields (nodeId, homeId, location) are separate.
 */
#ifndef ACCESS_CAPABILITY_DESCRIPTOR_H
#define ACCESS_CAPABILITY_DESCRIPTOR_H

#include <stddef.h>

typedef struct {
    char node_type[48];
    int schema_version;
    char display_name[96];
    char firmware_version[32];
} access_capability_descriptor_t;

const char *access_capability_descriptor_stub(void);
const char *access_capability_descriptor_json(void);
int access_capability_descriptor_decode(const char *json, access_capability_descriptor_t *descriptor);
int access_capability_descriptor_round_trip_test(void);

#endif
