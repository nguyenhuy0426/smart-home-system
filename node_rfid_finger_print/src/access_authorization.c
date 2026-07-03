#include "access_authorization.h"
#include "access_credential_privacy.h"

#include <string.h>

static bool constant_time_equal(const char *left, const char *right, size_t length)
{
    unsigned char difference = 0;
    for (size_t i = 0; i < length; i++) {
        difference |= (unsigned char)left[i] ^ (unsigned char)right[i];
    }
    return difference == 0;
}

static size_t bounded_length(const char *value, size_t maximum)
{
    size_t length = 0;
    if (value == NULL) return 0;
    while (length < maximum && value[length] != '\0') length++;
    return length;
}

bool access_authorization_hash_matches(const char *candidate,
                                       const char allowed_hashes[][ACCESS_HASH_STRING_SIZE],
                                       size_t allowed_count)
{
    if (candidate == NULL || allowed_hashes == NULL ||
            bounded_length(candidate, ACCESS_HASH_STRING_SIZE) != ACCESS_HASH_STRING_SIZE - 1) {
        return false;
    }
    for (size_t i = 0; i < allowed_count; i++) {
        if (bounded_length(allowed_hashes[i], ACCESS_HASH_STRING_SIZE) ==
                ACCESS_HASH_STRING_SIZE - 1 &&
                constant_time_equal(candidate, allowed_hashes[i],
                        ACCESS_HASH_STRING_SIZE - 1)) {
            return true;
        }
    }
    return false;
}

access_authorization_decision_t access_authorization_evaluate(
        access_sensor_status_t sensor_status,
        bool credential_is_allowlisted)
{
    access_authorization_decision_t decision = {
        .result = ACCESS_RESULT_DENIED,
        .should_unlock = false,
    };

    if (sensor_status == ACCESS_SENSOR_CREDENTIAL && credential_is_allowlisted) {
        decision.result = ACCESS_RESULT_GRANTED;
        decision.should_unlock = true;
    } else if (sensor_status == ACCESS_SENSOR_MALFORMED ||
            sensor_status == ACCESS_SENSOR_MISSING ||
            sensor_status == ACCESS_SENSOR_TIMEOUT) {
        decision.result = ACCESS_RESULT_SENSOR_ERROR;
    }
    return decision;
}
