#ifndef TZM1026_PROTOCOL_H
#define TZM1026_PROTOCOL_H

#include <stddef.h>
#include <stdint.h>

#define TZM1026_MAX_ACK_DATA 32

typedef enum {
    TZM1026_FRAME_OK = 0,
    TZM1026_FRAME_NULL = -1,
    TZM1026_FRAME_STALE = -2,
    TZM1026_FRAME_HEADER = -3,
    TZM1026_FRAME_LENGTH = -4,
    TZM1026_FRAME_CHECKSUM = -5
} tzm1026_frame_status_t;

typedef struct {
    uint8_t confirmation_code;
    uint8_t data[TZM1026_MAX_ACK_DATA];
    size_t data_length;
} tzm1026_ack_t;

tzm1026_frame_status_t tzm1026_parse_ack(const uint8_t *frame,
                                         size_t frame_length,
                                         uint64_t transaction_started_us,
                                         uint64_t received_at_us,
                                         uint64_t timeout_us,
                                         tzm1026_ack_t *out_ack);

#endif
