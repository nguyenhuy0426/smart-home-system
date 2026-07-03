#include "access_control_pipeline.h"
#include "nvs_config.h"

#include <stdio.h>
#include <string.h>

int access_control_pipeline_build_event(const access_control_attempt_t *attempt,
                                        char *out_json,
                                        size_t out_json_size)
{
    if (attempt == NULL || out_json == NULL || out_json_size == 0 ||
            (attempt->result == ACCESS_RESULT_GRANTED &&
                    (attempt->hashed_credential_id == NULL || !attempt->unlock_commanded))) {
        return 0;
    }
    if (attempt->hashed_credential_id != NULL &&
            (strlen(attempt->hashed_credential_id) != 71 ||
                    strncmp(attempt->hashed_credential_id, "sha256:", 7) != 0)) {
        return 0;
    }

    app_config_t config;
    if (!nvs_config_load(&config) || !config.provisioned ||
            config.node_id[0] == '\0' || config.room_id[0] == '\0') {
        return 0;
    }

    const char *kind = attempt->credential_kind == ACCESS_CREDENTIAL_FINGERPRINT
            ? "fingerprint" : "rfid";
    const char *result = attempt->result == ACCESS_RESULT_GRANTED
            ? "granted" : (attempt->result == ACCESS_RESULT_SENSOR_ERROR
                    ? "sensor_error" : "denied");
    const char *commanded_state = attempt->unlock_commanded ? "unlock_pulse" : "locked";
    char hash_json[96];
    if (attempt->hashed_credential_id == NULL) {
        snprintf(hash_json, sizeof(hash_json), "null");
    } else {
        snprintf(hash_json, sizeof(hash_json), "\"%s\"", attempt->hashed_credential_id);
    }

    int written = snprintf(out_json, out_json_size,
            "{"
            "\"eventId\":\"%s_%08lu\","
            "\"nodeId\":\"%s\","
            "\"roomId\":\"%s\","
            "\"eventType\":\"access.attempt\","
            "\"observedAtEpochMs\":%lld,"
            "\"observedAtUptimeMs\":%lld,"
            "\"result\":\"%s\","
            "\"credential\":{\"kind\":\"%s\",\"hashedEnrollmentId\":%s,"
            "\"rawTemplateStored\":false,\"rawUidStored\":false},"
            "\"confidence\":%d,"
            "\"actuator\":{\"kind\":\"door_lock_relay\",\"commandedState\":\"%s\"}"
            "}",
            config.node_id,
            attempt->sequence,
            config.node_id,
            config.room_id,
            attempt->observed_at_epoch_ms,
            attempt->observed_at_uptime_ms,
            result,
            kind,
            hash_json,
            attempt->confidence,
            commanded_state);
    return written > 0 && (size_t)written < out_json_size;
}
