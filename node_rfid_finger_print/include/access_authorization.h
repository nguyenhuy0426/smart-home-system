#ifndef ACCESS_AUTHORIZATION_H
#define ACCESS_AUTHORIZATION_H

#include "access_control_pipeline.h"
#include "access_credential_privacy.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef enum {
    ACCESS_SENSOR_NO_CREDENTIAL = 0,
    ACCESS_SENSOR_CREDENTIAL = 1,
    ACCESS_SENSOR_NO_MATCH = 2,
    ACCESS_SENSOR_MALFORMED = 3,
    ACCESS_SENSOR_MISSING = 4,
    ACCESS_SENSOR_TIMEOUT = 5
} access_sensor_status_t;

typedef struct {
    access_result_t result;
    bool should_unlock;
} access_authorization_decision_t;

bool access_authorization_hash_matches(const char *candidate,
                                       const char allowed_hashes[][ACCESS_HASH_STRING_SIZE],
                                       size_t allowed_count);

access_authorization_decision_t access_authorization_evaluate(
        access_sensor_status_t sensor_status,
        bool credential_is_allowlisted);

#endif
