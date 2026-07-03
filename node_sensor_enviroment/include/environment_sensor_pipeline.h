/*
 * Responsibility: declares the environment sensor acquisition pipeline for
 * DHT22, MQ7, GP2Y1014, and CJMCU680 readings.
 */
#ifndef ENVIRONMENT_SENSOR_PIPELINE_H
#define ENVIRONMENT_SENSOR_PIPELINE_H

#include <stddef.h>

#include "environment_sensor_fusion.h"

typedef enum {
    ENV_MQ7_HEATER_WARMUP = 0,
    ENV_MQ7_HEATER_SAMPLE = 1
} environment_mq7_heater_phase_t;

typedef struct {
    double dht22_temperature_degc;
    double dht22_humidity_percent;
    double cj_temperature_degc;
    double cj_humidity_percent;
    double pressure_hpa;
    double eco2_ppm;
    double tvoc_ppb;
    double mq7_co_ppm;
    environment_mq7_heater_phase_t mq7_phase;
    double pm25_ug_m3;
    unsigned long sequence;
    long long observed_at_epoch_ms;
    int steady_state;
} environment_raw_sensor_sample_t;

const char *environment_sensor_pipeline_stub(void);
int environment_sensor_pipeline_build_reading(const char *node_id,
                                              const char *room_id,
                                              const environment_raw_sensor_sample_t *sample,
                                              environment_sensor_fusion_state_t *fusion_state,
                                              char *out_json,
                                              size_t out_json_size);

#endif
