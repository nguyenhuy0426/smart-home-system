#include "environment_sensor_pipeline.h"
#include <stdio.h>

int main(void) {
    environment_sensor_fusion_state_t state;
    environment_sensor_fusion_init(&state, 2.0, 3);
    
    environment_raw_sensor_sample_t sample = {
        .dht22_temperature_degc = 25.0,
        .dht22_humidity_percent = 50.0,
        .cj_temperature_degc = 26.0,
        .cj_humidity_percent = 48.0,
        .pressure_hpa = 1013.25,
        .eco2_ppm = 400.0,
        .tvoc_ppb = 0.0,
        .mq7_co_ppm = 1.0,
        .mq7_phase = ENV_MQ7_HEATER_SAMPLE,
        .pm25_ug_m3 = 5.0,
        .sequence = 1,
        .observed_at_epoch_ms = 1000000,
        .steady_state = 1
    };
    
    char json[1024];
    if (environment_sensor_pipeline_build_reading("node1", "room1", &sample, &state, json, sizeof(json))) {
        printf("Pipeline output:\n%s\n", json);
    } else {
        printf("Pipeline failed\n");
    }
    return 0;
}
