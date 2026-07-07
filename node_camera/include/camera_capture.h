#ifndef CAMERA_CAPTURE_H
#define CAMERA_CAPTURE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define CAMERA_MAX_JPEG_BYTES (512U * 1024U)

typedef struct {
    const uint8_t *data;
    size_t length;
    size_t width;
    size_t height;
    uint64_t captured_at_us;
    void *driver_frame;
} camera_frame_t;

bool camera_capture_init(void);
bool camera_capture_is_ready(void);
bool camera_capture_acquire(camera_frame_t *frame);
void camera_capture_release(camera_frame_t *frame);

#endif
