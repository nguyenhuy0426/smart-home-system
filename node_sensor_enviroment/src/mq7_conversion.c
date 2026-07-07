#include "mq7_conversion.h"

#include <math.h>
#include <stddef.h>

sensor_status_t mq7_convert_adc(int adc_millivolts,
                                const mq7_calibration_t *calibration,
                                mq7_reading_t *reading)
{
    if (reading == NULL) return SENSOR_STATUS_IO_ERROR;
    reading->adc_millivolts = adc_millivolts;
    reading->co_ppm = NAN;
    reading->sensor_resistance_ohm = NAN;
    if (adc_millivolts <= 0) {
        reading->status = SENSOR_STATUS_OUT_OF_RANGE;
        return reading->status;
    }
    if (calibration == NULL || !calibration->valid ||
            calibration->sensor_supply_mv <= 0.0 ||
            calibration->adc_divider_ratio <= 0.0 ||
            calibration->load_resistor_ohm <= 0.0 ||
            calibration->clean_air_resistance_ohm <= 0.0 ||
            calibration->curve_a <= 0.0 || calibration->curve_b >= 0.0) {
        reading->status = SENSOR_STATUS_CALIBRATION_MISSING;
        return reading->status;
    }
    double output_mv = adc_millivolts * calibration->adc_divider_ratio;
    if (output_mv <= 0.0 || output_mv >= calibration->sensor_supply_mv) {
        reading->status = SENSOR_STATUS_OUT_OF_RANGE;
        return reading->status;
    }
    reading->sensor_resistance_ohm = calibration->load_resistor_ohm *
            (calibration->sensor_supply_mv / output_mv - 1.0);
    double resistance_ratio = reading->sensor_resistance_ohm /
            calibration->clean_air_resistance_ohm;
    reading->co_ppm = calibration->curve_a *
            pow(resistance_ratio, calibration->curve_b);
    if (!isfinite(reading->co_ppm) || reading->co_ppm < 0.0 ||
            reading->co_ppm > 10000.0 ||
            !isfinite(reading->sensor_resistance_ohm)) {
        reading->status = SENSOR_STATUS_OUT_OF_RANGE;
        reading->co_ppm = NAN;
        return reading->status;
    }
    reading->status = SENSOR_STATUS_VALID;
    return reading->status;
}
