#include "dht22_protocol.h"

#include <math.h>

sensor_status_t dht22_decode(const uint8_t frame[5],
                             double *temperature,
                             double *humidity)
{
    if (temperature != NULL) *temperature = NAN;
    if (humidity != NULL) *humidity = NAN;
    if (frame == NULL || temperature == NULL || humidity == NULL) {
        return SENSOR_STATUS_IO_ERROR;
    }
    uint8_t checksum = (uint8_t)(frame[0] + frame[1] + frame[2] + frame[3]);
    if (checksum != frame[4]) return SENSOR_STATUS_CHECKSUM_ERROR;

    uint16_t raw_humidity = ((uint16_t)frame[0] << 8) | frame[1];
    uint16_t raw_temperature = ((uint16_t)frame[2] << 8) | frame[3];
    double decoded_temperature = (raw_temperature & 0x8000)
            ? -(double)(raw_temperature & 0x7FFF) / 10.0
            : (double)raw_temperature / 10.0;
    double decoded_humidity = (double)raw_humidity / 10.0;
    if (decoded_temperature < -40.0 || decoded_temperature > 80.0 ||
            decoded_humidity < 0.0 || decoded_humidity > 100.0) {
        return SENSOR_STATUS_OUT_OF_RANGE;
    }
    *temperature = decoded_temperature;
    *humidity = decoded_humidity;
    return SENSOR_STATUS_VALID;
}

sensor_status_t dht22_decode_pulses(const uint16_t *high_pulse_us,
                                    size_t pulse_count,
                                    double *temperature,
                                    double *humidity)
{
    if (temperature != NULL) *temperature = NAN;
    if (humidity != NULL) *humidity = NAN;
    if (high_pulse_us == NULL || pulse_count != 40) {
        return SENSOR_STATUS_TIMEOUT;
    }
    uint8_t frame[5] = {0};
    for (size_t bit = 0; bit < 40; bit++) {
        if (high_pulse_us[bit] < 10 || high_pulse_us[bit] > 100) {
            return SENSOR_STATUS_TIMEOUT;
        }
        frame[bit / 8] <<= 1;
        if (high_pulse_us[bit] >= 50) frame[bit / 8] |= 1;
    }
    return dht22_decode(frame, temperature, humidity);
}
