/*
 * Responsibility: placeholder for DHT22-primary temperature/humidity reporting
 * and CJMCU680 offset-compensated cross-check diagnostics.
 */
#include "environment_sensor_fusion.h"

#include <math.h>
#include <stddef.h>

const char *environment_sensor_fusion_stub(void)
{
    return "environment-sensor-fusion-ready";
}

void environment_sensor_fusion_init(environment_sensor_fusion_state_t *state,
                                    double threshold_degc,
                                    int sustained_required)
{
    if (state == NULL) {
        return;
    }

    state->threshold_degc = threshold_degc > 0.0 ? threshold_degc : 2.0;
    state->sustained_required = sustained_required > 0 ? sustained_required : 3;
    state->sustained_count = 0;
    state->learned_bme680_temp_offset = 0.0;
    state->learned_bme680_humidity_offset = 0.0;
    state->offset_samples = 0;
}

int environment_sensor_fusion_update(environment_sensor_fusion_state_t *state,
                                     double dht22_temperature_degc,
                                     double dht22_humidity_percent,
                                     double cj_temperature_degc,
                                     double cj_humidity_percent,
                                     int steady_state,
                                     environment_sensor_fusion_result_t *result)
{
    double compensated_temp;
    double compensated_humidity;
    double delta;
    double humidity_delta;

    if (state == NULL || result == NULL) {
        return 0;
    }

    if (steady_state) {
        double observed_temp_offset = cj_temperature_degc - dht22_temperature_degc;
        double observed_humidity_offset = cj_humidity_percent - dht22_humidity_percent;
        state->learned_bme680_temp_offset =
                ((state->learned_bme680_temp_offset * state->offset_samples) + observed_temp_offset) /
                (state->offset_samples + 1);
        state->learned_bme680_humidity_offset =
                ((state->learned_bme680_humidity_offset * state->offset_samples) + observed_humidity_offset) /
                (state->offset_samples + 1);
        state->offset_samples++;
    }

    compensated_temp = cj_temperature_degc - state->learned_bme680_temp_offset;
    compensated_humidity = cj_humidity_percent - state->learned_bme680_humidity_offset;
    delta = fabs(dht22_temperature_degc - compensated_temp);
    humidity_delta = fabs(dht22_humidity_percent - compensated_humidity);

    if (delta > state->threshold_degc) {
        state->sustained_count++;
    } else {
        state->sustained_count = 0;
    }

    result->primary_temperature_degc = dht22_temperature_degc;
    result->primary_humidity_percent = dht22_humidity_percent;
    result->compensated_cj_temperature_degc = compensated_temp;
    result->compensated_cj_humidity_percent = compensated_humidity;
    result->temperature_delta_degc = delta;
    result->humidity_delta_percent = humidity_delta;
    result->fault = state->sustained_count >= state->sustained_required;
    return 1;
}
