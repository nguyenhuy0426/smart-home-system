#ifndef DHT22_PROTOCOL_H
#define DHT22_PROTOCOL_H

#include "sensor_status.h"

#include <stddef.h>
#include <stdint.h>

sensor_status_t dht22_decode(const uint8_t frame[5],
                             double *temperature,
                             double *humidity);
sensor_status_t dht22_decode_pulses(const uint16_t *high_pulse_us,
                                    size_t pulse_count,
                                    double *temperature,
                                    double *humidity);

#endif
