#include "bme680_status.h"

#include "bme68x_defs.h"

sensor_status_t bme680_validate_frame_status(int8_t api_result,
                                             uint8_t field_count,
                                             uint8_t frame_status,
                                             sensor_status_t *gas_status)
{
    if (gas_status == NULL) return SENSOR_STATUS_IO_ERROR;
    *gas_status = SENSOR_STATUS_IO_ERROR;
    if (api_result == BME68X_W_NO_NEW_DATA || field_count == 0 ||
            (frame_status & BME68X_NEW_DATA_MSK) == 0) {
        *gas_status = SENSOR_STATUS_NO_NEW_DATA;
        return SENSOR_STATUS_NO_NEW_DATA;
    }
    if (api_result != BME68X_OK) return SENSOR_STATUS_IO_ERROR;
    if ((frame_status & BME68X_GASM_VALID_MSK) == 0) {
        *gas_status = SENSOR_STATUS_NO_NEW_DATA;
    } else if ((frame_status & BME68X_HEAT_STAB_MSK) == 0) {
        *gas_status = SENSOR_STATUS_HEATER_WARMUP;
    } else {
        *gas_status = SENSOR_STATUS_VALID;
    }
    return SENSOR_STATUS_VALID;
}
