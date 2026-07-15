#include "sensor_status.h"

const char *sensor_status_name(sensor_status_t status)
{
    switch (status) {
    case SENSOR_STATUS_VALID: return "valid";
    case SENSOR_STATUS_NOT_INITIALIZED: return "not_initialized";
    case SENSOR_STATUS_TIMEOUT: return "timeout";
    case SENSOR_STATUS_CHECKSUM_ERROR: return "checksum_error";
    case SENSOR_STATUS_IO_ERROR: return "io_error";
    case SENSOR_STATUS_OUT_OF_RANGE: return "out_of_range";
    case SENSOR_STATUS_HEATER_WARMUP: return "heater_warmup";
    case SENSOR_STATUS_CALIBRATION_MISSING: return "calibration_missing";
    case SENSOR_STATUS_RAW_UNCALIBRATED: return "raw_uncalibrated";
    case SENSOR_STATUS_RATE_LIMITED: return "rate_limited";
    case SENSOR_STATUS_NO_NEW_DATA: return "no_new_data";
    case SENSOR_STATUS_UNSUPPORTED: return "unsupported";
    case SENSOR_STATUS_NOT_CONNECTED: return "not_connected";
    default: return "unknown_error";
    }
}
