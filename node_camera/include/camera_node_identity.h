/*
 * Responsibility: declares stable node identity hooks for provisioning and
 * location mapping of the camera node.
 */
#ifndef CAMERA_NODE_IDENTITY_H
#define CAMERA_NODE_IDENTITY_H

#include <stddef.h>

typedef struct {
    char node_id[64];
    char home_id[64];
    char room_id[64];
    char placement_label[96];
    char hardware_fingerprint[96];
    char replaces_node_id[64];
    int provisioned;
} camera_node_identity_t;

const char *camera_node_identity_stub(void);
void camera_node_identity_reset_mock_storage(void);
int camera_node_identity_assign(const camera_node_identity_t *identity);
int camera_node_identity_load(camera_node_identity_t *identity);
void camera_node_identity_simulate_reboot(void);

#endif
