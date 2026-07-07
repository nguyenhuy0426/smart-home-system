#include "environment_sensor_fusion.h"

#include <math.h>
#include <stddef.h>
#include <string.h>

void environment_sensor_fusion_init(environment_sensor_fusion_state_t *state,
                                    double threshold_degc,
                                    int sustained_required)
{
    if (state == NULL) return;
    memset(state, 0, sizeof(*state));
    state->threshold_degc = threshold_degc > 0.0 ? threshold_degc : 2.0;
    state->sustained_required = sustained_required > 0 ? sustained_required : 3;
}

int environment_sensor_fusion_update(environment_sensor_fusion_state_t *state,
                                     double dht22_temperature_degc,
                                     double dht22_humidity_percent,
                                     double cj_temperature_degc,
                                     double cj_humidity_percent,
                                     int steady_state,
                                     environment_sensor_fusion_result_t *result)
{
    if (state == NULL || result == NULL ||
            !isfinite(dht22_temperature_degc) ||
            !isfinite(dht22_humidity_percent) ||
            !isfinite(cj_temperature_degc) ||
            !isfinite(cj_humidity_percent) ||
            dht22_temperature_degc < -40.0 || dht22_temperature_degc > 80.0 ||
            dht22_humidity_percent < 0.0 || dht22_humidity_percent > 100.0 ||
            cj_temperature_degc < -40.0 || cj_temperature_degc > 85.0 ||
            cj_humidity_percent < 0.0 || cj_humidity_percent > 100.0) {
        return 0;
    }
    memset(result, 0, sizeof(*result));
    result->primary_temperature_degc = dht22_temperature_degc;
    result->primary_humidity_percent = dht22_humidity_percent;
    result->compensated_cj_temperature_degc = cj_temperature_degc;
    result->compensated_cj_humidity_percent = cj_humidity_percent;
    result->temperature_delta_degc = fabs(
            dht22_temperature_degc - cj_temperature_degc);
    result->humidity_delta_percent = fabs(
            dht22_humidity_percent - cj_humidity_percent);
    if (steady_state && result->temperature_delta_degc > state->threshold_degc) {
        if (state->sustained_count < state->sustained_required) {
            state->sustained_count++;
        }
    } else {
        state->sustained_count = 0;
    }
    result->fault = state->sustained_count >= state->sustained_required;
    return 1;
}
