#ifndef MFRC522_PROTOCOL_H
#define MFRC522_PROTOCOL_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

uint16_t mfrc522_crc_a(const uint8_t *data, size_t length);
bool mfrc522_uid_bcc_valid(const uint8_t cascade_uid[5]);
bool mfrc522_crc_a_valid(const uint8_t *data, size_t data_length,
                         const uint8_t received_crc[2]);

#endif
