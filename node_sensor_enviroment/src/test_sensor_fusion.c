#include "environment_sensor_fusion.h"
#include <stdio.h>

int main(void) {
    environment_sensor_fusion_state_t state;
    environment_sensor_fusion_init(&state, 2.0, 3);
    
    environment_sensor_fusion_result_t result;
    environment_sensor_fusion_update(&state, 25.0, 50.0, 26.0, 48.0, 1, &result);
    
    printf("Fusion temperature: %.2f\n", result.primary_temperature_degc);
    printf("Fault status: %d\n", result.fault);
    return 0;
}
