#ifndef PROVISIONING_PARSER_H
#define PROVISIONING_PARSER_H

#include "nvs_config.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

bool provisioning_parse_config(const uint8_t *message,
                               size_t message_length,
                               app_config_t *configuration);

#endif
