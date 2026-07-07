#ifndef MQ7_CONVERSION_H
#define MQ7_CONVERSION_H

#include "mq7_cycle.h"
#include "sensor_status.h"

#include <stdbool.h>

typedef struct {
    bool valid;
    double sensor_supply_mv;
    double adc_divider_ratio;
    double load_resistor_ohm;
    double clean_air_resistance_ohm;
    double curve_a;
    double curve_b;
} mq7_calibration_t;

typedef struct {
    sensor_status_t status;
    mq7_heater_phase_t phase;
    double co_ppm;
    double sensor_resistance_ohm;
    int adc_millivolts;
} mq7_reading_t;

sensor_status_t mq7_convert_adc(int adc_millivolts,
                                const mq7_calibration_t *calibration,
                                mq7_reading_t *reading);

#endif
