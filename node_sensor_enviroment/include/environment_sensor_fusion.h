/*
 * Responsibility: declares the DHT22-primary and CJMCU680-secondary sensor
 * fusion policy, including future offset compensation hooks.
 */
#ifndef ENVIRONMENT_SENSOR_FUSION_H
#define ENVIRONMENT_SENSOR_FUSION_H

typedef struct {
    double threshold_degc;
    int sustained_required;
    int sustained_count;
    double learned_bme680_temp_offset;
    double learned_bme680_humidity_offset;
    int offset_samples;
} environment_sensor_fusion_state_t;

typedef struct {
    double primary_temperature_degc;
    double primary_humidity_percent;
    double compensated_cj_temperature_degc;
    double compensated_cj_humidity_percent;
    double temperature_delta_degc;
    double humidity_delta_percent;
    int fault;
} environment_sensor_fusion_result_t;

const char *environment_sensor_fusion_stub(void);
void environment_sensor_fusion_init(environment_sensor_fusion_state_t *state,
                                    double threshold_degc,
                                    int sustained_required);
int environment_sensor_fusion_update(environment_sensor_fusion_state_t *state,
                                     double dht22_temperature_degc,
                                     double dht22_humidity_percent,
                                     double cj_temperature_degc,
                                     double cj_humidity_percent,
                                     int steady_state,
                                     environment_sensor_fusion_result_t *result);

#endif
