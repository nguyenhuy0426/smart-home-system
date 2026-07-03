#include "mfrc522_protocol.h"

uint16_t mfrc522_crc_a(const uint8_t *data, size_t length)
{
    uint16_t crc = 0x6363;
    if (data == NULL) return 0;
    for (size_t index = 0; index < length; index++) {
        uint8_t value = data[index] ^ (uint8_t)(crc & 0x00FF);
        value ^= (uint8_t)(value << 4);
        crc = (crc >> 8) ^ ((uint16_t)value << 8) ^
                ((uint16_t)value << 3) ^ ((uint16_t)value >> 4);
    }
    return crc;
}

bool mfrc522_uid_bcc_valid(const uint8_t cascade_uid[5])
{
    if (cascade_uid == NULL) return false;
    return (uint8_t)(cascade_uid[0] ^ cascade_uid[1] ^ cascade_uid[2] ^
            cascade_uid[3]) == cascade_uid[4];
}

bool mfrc522_crc_a_valid(const uint8_t *data, size_t data_length,
                         const uint8_t received_crc[2])
{
    if (data == NULL || received_crc == NULL) return false;
    uint16_t crc = mfrc522_crc_a(data, data_length);
    return received_crc[0] == (uint8_t)(crc & 0xFF) &&
            received_crc[1] == (uint8_t)(crc >> 8);
}
