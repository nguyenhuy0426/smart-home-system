#include "gp2y1014_conversion.h"

#include <math.h>
#include <stddef.h>

sensor_status_t gp2y1014_convert_adc(int adc_millivolts,
                                     const gp2y1014_calibration_t *calibration,
                                     gp2y1014_reading_t *reading)
{
    if (reading == NULL) return SENSOR_STATUS_IO_ERROR;
    reading->adc_millivolts = adc_millivolts;
    reading->pm25_ug_m3 = NAN;
    if (adc_millivolts < 0) {
        reading->status = SENSOR_STATUS_OUT_OF_RANGE;
        return reading->status;
    }
    if (calibration == NULL || !calibration->valid ||
            calibration->adc_divider_ratio <= 0.0 ||
            calibration->clean_air_voltage_mv < 0.0 ||
            calibration->sensitivity_mv_per_ug_m3 <= 0.0) {
        reading->status = SENSOR_STATUS_CALIBRATION_MISSING;
        return reading->status;
    }
    double sensor_output_mv = adc_millivolts * calibration->adc_divider_ratio;
    double density = (sensor_output_mv - calibration->clean_air_voltage_mv) /
            calibration->sensitivity_mv_per_ug_m3;
    if (!isfinite(density) || density < -20.0 || density > 1000.0) {
        reading->status = SENSOR_STATUS_OUT_OF_RANGE;
        return reading->status;
    }
    if (density < 0.0) density = 0.0;
    reading->pm25_ug_m3 = density;
    reading->status = SENSOR_STATUS_VALID;
    return reading->status;
}
