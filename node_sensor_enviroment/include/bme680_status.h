#ifndef BME680_STATUS_H
#define BME680_STATUS_H

#include "sensor_status.h"

#include <stdint.h>

sensor_status_t bme680_validate_frame_status(int8_t api_result,
                                             uint8_t field_count,
                                             uint8_t frame_status,
                                             sensor_status_t *gas_status);

#endif
