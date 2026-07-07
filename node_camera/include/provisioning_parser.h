#ifndef PROVISIONING_PARSER_H
#define PROVISIONING_PARSER_H

#include "nvs_config.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

bool provisioning_parse_config(const uint8_t *data, size_t length,
                               app_config_t *config);
bool app_config_is_valid(const app_config_t *config);

#endif
