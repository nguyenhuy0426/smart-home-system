#ifndef ACCESS_CONTROL_PIPELINE_H
#define ACCESS_CONTROL_PIPELINE_H

#include <stdbool.h>
#include <stddef.h>

typedef enum {
    ACCESS_CREDENTIAL_FINGERPRINT = 0,
    ACCESS_CREDENTIAL_RFID = 1
} access_credential_kind_t;

typedef enum {
    ACCESS_RESULT_DENIED = 0,
    ACCESS_RESULT_GRANTED = 1,
    ACCESS_RESULT_SENSOR_ERROR = 2
} access_result_t;

typedef struct {
    access_credential_kind_t credential_kind;
    const char *hashed_credential_id;
    access_result_t result;
    int confidence;
    bool unlock_commanded;
    unsigned long sequence;
    long long observed_at_epoch_ms;
    long long observed_at_uptime_ms;
} access_control_attempt_t;

int access_control_pipeline_build_event(const access_control_attempt_t *attempt,
                                        char *out_json,
                                        size_t out_json_size);

#endif
