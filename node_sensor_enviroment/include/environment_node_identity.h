/*
 * Responsibility: declares stable node identity hooks for provisioning and
 * location mapping of the environment sensor node.
 */
#ifndef ENVIRONMENT_NODE_IDENTITY_H
#define ENVIRONMENT_NODE_IDENTITY_H

#include <stddef.h>

typedef struct {
    char node_id[64];
    char home_id[64];
    char room_id[64];
    char placement_label[96];
    char hardware_fingerprint[96];
    char replaces_node_id[64];
    int provisioned;
} environment_node_identity_t;

const char *environment_node_identity_stub(void);
void environment_node_identity_reset_mock_storage(void);
int environment_node_identity_assign(const environment_node_identity_t *identity);
int environment_node_identity_load(environment_node_identity_t *identity);
void environment_node_identity_simulate_reboot(void);

#endif
