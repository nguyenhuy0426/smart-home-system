#include "access_authorization.h"
#include "access_credential_privacy.h"
#include "mfrc522_protocol.h"
#include "tzm1026_protocol.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

static const uint8_t TEST_KEY[ACCESS_HMAC_KEY_SIZE] = {
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
    0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
    0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
};

static size_t build_ack(uint8_t confirmation, const uint8_t *data,
                        size_t data_length, uint8_t *out)
{
    const uint8_t prefix[] = {0xEF, 0x01, 0xFF, 0xFF, 0xFF, 0xFF, 0x07};
    memcpy(out, prefix, sizeof(prefix));
    uint16_t packet_length = (uint16_t)(data_length + 3);
    out[7] = (uint8_t)(packet_length >> 8);
    out[8] = (uint8_t)packet_length;
    out[9] = confirmation;
    if (data_length > 0) memcpy(out + 10, data, data_length);
    size_t total = packet_length + 9;
    uint32_t checksum = 0;
    for (size_t i = 6; i < total - 2; i++) checksum += out[i];
    out[total - 2] = (uint8_t)(checksum >> 8);
    out[total - 1] = (uint8_t)checksum;
    return total;
}

static void test_unknown_card_never_unlocks(void)
{
    uint8_t enrolled_uid[] = {0x01, 0x02, 0x03, 0x04};
    uint8_t unknown_uid[] = {0x10, 0x20, 0x30, 0x40};
    char allowed[1][ACCESS_HASH_STRING_SIZE] = {{0}};
    char candidate[ACCESS_HASH_STRING_SIZE] = {0};
    assert(access_credential_hmac_sha256(enrolled_uid, sizeof(enrolled_uid),
            TEST_KEY, sizeof(TEST_KEY), allowed[0], sizeof(allowed[0])));
    assert(access_credential_hmac_sha256(unknown_uid, sizeof(unknown_uid),
            TEST_KEY, sizeof(TEST_KEY), candidate, sizeof(candidate)));
    bool allowlisted = access_authorization_hash_matches(candidate, allowed, 1);
    access_authorization_decision_t decision = access_authorization_evaluate(
            ACCESS_SENSOR_CREDENTIAL, allowlisted);
    assert(!allowlisted);
    assert(decision.result == ACCESS_RESULT_DENIED);
    assert(!decision.should_unlock);
}

static void test_valid_credential_unlocks(void)
{
    uint8_t uid[] = {0xDE, 0xAD, 0xBE, 0xEF};
    char allowed[1][ACCESS_HASH_STRING_SIZE] = {{0}};
    char candidate[ACCESS_HASH_STRING_SIZE] = {0};
    assert(access_credential_hmac_sha256(uid, sizeof(uid), TEST_KEY,
            sizeof(TEST_KEY), allowed[0], sizeof(allowed[0])));
    assert(access_credential_hmac_sha256(uid, sizeof(uid), TEST_KEY,
            sizeof(TEST_KEY), candidate, sizeof(candidate)));
    bool allowlisted = access_authorization_hash_matches(candidate, allowed, 1);
    access_authorization_decision_t decision = access_authorization_evaluate(
            ACCESS_SENSOR_CREDENTIAL, allowlisted);
    assert(allowlisted);
    assert(decision.result == ACCESS_RESULT_GRANTED);
    assert(decision.should_unlock);
}

static void test_missing_hardware_never_unlocks(void)
{
    access_authorization_decision_t decision = access_authorization_evaluate(
            ACCESS_SENSOR_MISSING, true);
    assert(decision.result == ACCESS_RESULT_SENSOR_ERROR);
    assert(!decision.should_unlock);
}

static void test_malformed_uart_frame_is_rejected(void)
{
    uint8_t frame[32];
    uint8_t search_data[] = {0x00, 0x2A, 0x00, 0x64};
    size_t length = build_ack(0x00, search_data, sizeof(search_data), frame);
    tzm1026_ack_t ack;
    assert(tzm1026_parse_ack(frame, length, 1000, 1100, 500, &ack) ==
            TZM1026_FRAME_OK);
    assert(ack.confirmation_code == 0x00);
    assert(ack.data_length == sizeof(search_data));
    assert(memcmp(ack.data, search_data, sizeof(search_data)) == 0);
    frame[length - 1] ^= 0x01;
    assert(tzm1026_parse_ack(frame, length, 1000, 1100, 500, &ack) ==
            TZM1026_FRAME_CHECKSUM);
    frame[length - 1] ^= 0x01;
    frame[8]++;
    assert(tzm1026_parse_ack(frame, length, 1000, 1100, 500, &ack) ==
            TZM1026_FRAME_LENGTH);
}

static void test_stale_uart_frame_is_rejected(void)
{
    uint8_t frame[16];
    size_t length = build_ack(0x00, NULL, 0, frame);
    tzm1026_ack_t ack;
    assert(tzm1026_parse_ack(frame, length, 2000, 1999, 500, &ack) ==
            TZM1026_FRAME_STALE);
    assert(tzm1026_parse_ack(frame, length, 2000, 2600, 500, &ack) ==
            TZM1026_FRAME_STALE);
}

static void test_rc522_uid_validation(void)
{
    uint8_t cascade_uid[] = {0xDE, 0xAD, 0xBE, 0xEF, 0x22};
    assert(mfrc522_uid_bcc_valid(cascade_uid));
    cascade_uid[4] ^= 0x01;
    assert(!mfrc522_uid_bcc_valid(cascade_uid));

    uint8_t select_data[] = {0x93, 0x70, 0xDE, 0xAD, 0xBE, 0xEF, 0x22};
    uint16_t crc = mfrc522_crc_a(select_data, sizeof(select_data));
    uint8_t encoded_crc[] = {(uint8_t)crc, (uint8_t)(crc >> 8)};
    assert(mfrc522_crc_a_valid(select_data, sizeof(select_data), encoded_crc));
}

int main(void)
{
    test_unknown_card_never_unlocks();
    test_valid_credential_unlocks();
    test_missing_hardware_never_unlocks();
    test_malformed_uart_frame_is_rejected();
    test_stale_uart_frame_is_rejected();
    test_rc522_uid_validation();
    puts("access_security_tests: all tests passed");
    return 0;
}
