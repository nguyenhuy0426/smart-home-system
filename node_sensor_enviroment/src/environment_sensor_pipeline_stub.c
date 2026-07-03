/*
 * Responsibility: placeholder for coordinating sensor sampling and normalized
 * reading envelopes without implementing hardware drivers.
 */
#include "environment_sensor_pipeline.h"

#include <stdio.h>
#include <string.h>

const char *environment_sensor_pipeline_stub(void)
{
    return "environment-sensor-pipeline-ready";
}

int environment_sensor_pipeline_build_reading(const char *node_id,
                                              const char *room_id,
                                              const environment_raw_sensor_sample_t *sample,
                                              environment_sensor_fusion_state_t *fusion_state,
                                              char *out_json,
                                              size_t out_json_size)
{
    environment_sensor_fusion_result_t fusion;
    const char *mq7_quality;
    int written;

    if (sample == NULL || fusion_state == NULL || out_json == NULL || out_json_size == 0) {
        return 0;
    }

    if (!environment_sensor_fusion_update(fusion_state,
            sample->dht22_temperature_degc,
            sample->dht22_humidity_percent,
            sample->cj_temperature_degc,
            sample->cj_humidity_percent,
            sample->steady_state,
            &fusion)) {
        return 0;
    }

    mq7_quality = sample->mq7_phase == ENV_MQ7_HEATER_SAMPLE ?
            "valid_after_heater_cycle" : "invalid_heater_warmup";

    written = snprintf(out_json, out_json_size,
            "{"
            "\"readingId\":\"%s_%08lu\","
            "\"nodeId\":\"%s\","
            "\"roomId\":\"%s\","
            "\"sequence\":%lu,"
            "\"observedAtEpochMs\":%lld,"
            "\"metrics\":{"
            "\"ambientTemperature\":{\"value\":%.2f,\"unit\":\"degC\",\"source\":\"DHT22\",\"quality\":\"primary\"},"
            "\"relativeHumidity\":{\"value\":%.2f,\"unit\":\"percent_rh\",\"source\":\"DHT22\",\"quality\":\"primary\"},"
            "\"co\":{\"value\":%.2f,\"unit\":\"ppm\",\"source\":\"MQ7\",\"quality\":\"%s\",\"heaterPhase\":\"%s\"},"
            "\"pm25\":{\"value\":%.2f,\"unit\":\"ug_m3\",\"source\":\"GP2Y1014\",\"quality\":\"sampled\"},"
            "\"pressure\":{\"value\":%.2f,\"unit\":\"hPa\",\"source\":\"CJMCU680\",\"quality\":\"sampled\"},"
            "\"eco2\":{\"value\":%.2f,\"unit\":\"ppm\",\"source\":\"CJMCU680\",\"quality\":\"algorithm_estimate\"},"
            "\"tvoc\":{\"value\":%.2f,\"unit\":\"ppb\",\"source\":\"CJMCU680\",\"quality\":\"algorithm_estimate\"}"
            "},"
            "\"diagnostics\":{\"temperatureDeltaAfterCompensation\":%.2f,"
            "\"temperatureDeltaUnit\":\"degC\",\"sensorFaultFlags\":[%s]}"
            "}",
            node_id,
            sample->sequence,
            node_id,
            room_id,
            sample->sequence,
            sample->observed_at_epoch_ms,
            fusion.primary_temperature_degc,
            fusion.primary_humidity_percent,
            sample->mq7_co_ppm,
            mq7_quality,
            sample->mq7_phase == ENV_MQ7_HEATER_SAMPLE ? "sample" : "warmup",
            sample->pm25_ug_m3,
            sample->pressure_hpa,
            sample->eco2_ppm,
            sample->tvoc_ppb,
            fusion.temperature_delta_degc,
            fusion.fault ? "\"temperature_discrepancy\"" : "");

    return written > 0 && (size_t)written < out_json_size;
}
