#ifndef GP2Y1014_CONVERSION_H
#define GP2Y1014_CONVERSION_H

#include "sensor_status.h"

#include <stdbool.h>

typedef struct {
    bool valid;
    double adc_divider_ratio;
    double clean_air_voltage_mv;
    double sensitivity_mv_per_ug_m3;
} gp2y1014_calibration_t;

typedef struct {
    sensor_status_t status;
    double pm25_ug_m3;
    int adc_millivolts;
} gp2y1014_reading_t;

sensor_status_t gp2y1014_convert_adc(int adc_millivolts,
                                     const gp2y1014_calibration_t *calibration,
                                     gp2y1014_reading_t *reading);

#endif
