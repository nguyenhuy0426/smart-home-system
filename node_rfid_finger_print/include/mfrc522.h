#ifndef MFRC522_H
#define MFRC522_H

#include "driver/spi_master.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef enum {
    MFRC522_NO_CARD = 0,
    MFRC522_UID_OK = 1,
    MFRC522_TIMEOUT = -1,
    MFRC522_PROTOCOL_ERROR = -2,
    MFRC522_HARDWARE_ERROR = -3
} mfrc522_status_t;

bool mfrc522_init(spi_host_device_t host_id, int mosi_pin, int miso_pin,
                  int sck_pin, int cs_pin);
mfrc522_status_t mfrc522_read_card_uid(uint8_t *uid, size_t uid_capacity,
                                      uint8_t *uid_len);

#endif
