#ifndef ENVIRONMENT_SENSOR_PIPELINE_H
#define ENVIRONMENT_SENSOR_PIPELINE_H

#include "mq7_cycle.h"
#include "sensor_status.h"

#include <stddef.h>
#include <stdint.h>

#define ENVIRONMENT_READING_SCHEMA_VERSION 1

typedef struct {
    sensor_status_t bme680_status;
    sensor_status_t bme680_gas_status;
    double bme680_temperature_degc;
    double bme680_humidity_percent;
    double pressure_hpa;
    double gas_resistance_ohm;

    sensor_status_t mq7_status;
    mq7_heater_phase_t mq7_phase;
    double mq7_co_ppm;
    int mq7_adc_millivolts;

    sensor_status_t gp2y_status;
    double pm25_ug_m3;
    int gp2y_adc_millivolts;

    uint64_t sequence;
    /* Unix epoch ms; 0 means the node clock never SNTP-synced. */
    uint64_t observed_at_epoch_ms;
    uint64_t observed_at_uptime_ms;
} environment_raw_sensor_sample_t;

int environment_sensor_pipeline_build_reading(
        const char *node_id,
        const char *room_id,
        const environment_raw_sensor_sample_t *sample,
        char *out_json,
        size_t out_json_size);

#endif
