#ifndef TZM1026_H
#define TZM1026_H

#include <stdbool.h>
#include <stdint.h>

typedef enum {
    TZM1026_SCAN_NO_FINGER = 0,
    TZM1026_SCAN_MATCH = 1,
    TZM1026_SCAN_NO_MATCH = 2,
    TZM1026_SCAN_TIMEOUT = -1,
    TZM1026_SCAN_MALFORMED = -2,
    TZM1026_SCAN_HARDWARE_ERROR = -3
} tzm1026_scan_status_t;

bool tzm1026_init(int uart_num, int tx_pin, int rx_pin, int baud_rate);
tzm1026_scan_status_t tzm1026_scan_finger(uint16_t *page_id, uint16_t *match_score);

#endif
