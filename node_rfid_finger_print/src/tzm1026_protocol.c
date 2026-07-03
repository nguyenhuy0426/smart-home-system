#include "tzm1026_protocol.h"

#include <string.h>

tzm1026_frame_status_t tzm1026_parse_ack(const uint8_t *frame,
                                         size_t frame_length,
                                         uint64_t transaction_started_us,
                                         uint64_t received_at_us,
                                         uint64_t timeout_us,
                                         tzm1026_ack_t *out_ack)
{
    if (frame == NULL || out_ack == NULL) return TZM1026_FRAME_NULL;
    memset(out_ack, 0, sizeof(*out_ack));
    if (received_at_us < transaction_started_us ||
            received_at_us - transaction_started_us > timeout_us) {
        return TZM1026_FRAME_STALE;
    }
    if (frame_length < 12 || frame[0] != 0xEF || frame[1] != 0x01 ||
            frame[2] != 0xFF || frame[3] != 0xFF || frame[4] != 0xFF ||
            frame[5] != 0xFF || frame[6] != 0x07) {
        return TZM1026_FRAME_HEADER;
    }

    uint16_t packet_length = ((uint16_t)frame[7] << 8) | frame[8];
    if (packet_length < 3 || packet_length > TZM1026_MAX_ACK_DATA + 3 ||
            frame_length != (size_t)packet_length + 9) {
        return TZM1026_FRAME_LENGTH;
    }

    uint32_t checksum = 0;
    for (size_t i = 6; i < frame_length - 2; i++) checksum += frame[i];
    uint16_t expected_checksum =
            ((uint16_t)frame[frame_length - 2] << 8) | frame[frame_length - 1];
    if ((uint16_t)checksum != expected_checksum) return TZM1026_FRAME_CHECKSUM;

    out_ack->confirmation_code = frame[9];
    out_ack->data_length = packet_length - 3;
    if (out_ack->data_length > 0) {
        memcpy(out_ack->data, frame + 10, out_ack->data_length);
    }
    return TZM1026_FRAME_OK;
}
