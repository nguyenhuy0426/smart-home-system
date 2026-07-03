#include "camera_node_identity.h"
#include "nvs_config.h"
#include <string.h>

static camera_node_identity_t runtime_identity;

const char *camera_node_identity_stub(void)
{
    app_config_t config;
    if (nvs_config_load(&config) && config.provisioned) {
        return "camera-node-provisioned";
    }
    return "camera-node-unprovisioned";
}

void camera_node_identity_reset_mock_storage(void)
{
    nvs_config_reset();
    memset(&runtime_identity, 0, sizeof(runtime_identity));
}

int camera_node_identity_assign(const camera_node_identity_t *identity)
{
    if (identity == NULL || identity->node_id[0] == '\0' ||
        identity->home_id[0] == '\0' || identity->room_id[0] == '\0') {
        return 0;
    }

    app_config_t config;
    if (!nvs_config_load(&config)) {
        memset(&config, 0, sizeof(config));
    }

    strncpy(config.node_id, identity->node_id, sizeof(config.node_id) - 1);
    strncpy(config.room_id, identity->room_id, sizeof(config.room_id) - 1);
    config.provisioned = 1;

    if (nvs_config_save(&config)) {
        runtime_identity = *identity;
        runtime_identity.provisioned = 1;
        return 1;
    }
    return 0;
}

int camera_node_identity_load(camera_node_identity_t *identity)
{
    if (identity == NULL) {
        return 0;
    }

    app_config_t config;
    if (nvs_config_load(&config) && config.provisioned) {
        memset(identity, 0, sizeof(camera_node_identity_t));
        strncpy(identity->node_id, config.node_id, sizeof(identity->node_id) - 1);
        strncpy(identity->room_id, config.room_id, sizeof(identity->room_id) - 1);
        identity->provisioned = 1;
        runtime_identity = *identity;
        return 1;
    }
    return 0;
}

void camera_node_identity_simulate_reboot(void)
{
    memset(&runtime_identity, 0, sizeof(runtime_identity));
}
