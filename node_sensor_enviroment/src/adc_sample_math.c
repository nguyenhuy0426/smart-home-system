#include "adc_sample_math.h"

#include <stdint.h>

sensor_status_t adc_samples_average(const int *samples,
                                    size_t sample_count,
                                    int *average_raw)
{
    if (average_raw != NULL) *average_raw = 0;
    if (samples == NULL || average_raw == NULL ||
            sample_count == 0 || sample_count > 64) {
        return SENSOR_STATUS_IO_ERROR;
    }
    int64_t total = 0;
    for (size_t i = 0; i < sample_count; i++) {
        if (samples[i] < 0 || samples[i] > 4095) {
            return SENSOR_STATUS_OUT_OF_RANGE;
        }
        total += samples[i];
    }
    *average_raw = (int)((total + (int64_t)sample_count / 2) /
            (int64_t)sample_count);
    return SENSOR_STATUS_VALID;
}
