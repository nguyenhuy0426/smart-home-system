#ifndef ADC_SAMPLE_MATH_H
#define ADC_SAMPLE_MATH_H

#include "sensor_status.h"

#include <stddef.h>

sensor_status_t adc_samples_average(const int *samples,
                                    size_t sample_count,
                                    int *average_raw);

#endif
