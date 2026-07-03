/*
 * Responsibility: declares camera-side status hooks for the gateway-owned
 * motion snapshot and retention pipeline.
 */
#ifndef CAMERA_MOTION_SNAPSHOT_POLICY_H
#define CAMERA_MOTION_SNAPSHOT_POLICY_H

#include <stddef.h>

typedef struct {
    double threshold;
    int cooldown_frames;
    int frames_since_snapshot;
    int motion_active;
    int snapshot_retention_days;
    int metadata_retention_days;
} camera_motion_snapshot_policy_t;

typedef struct {
    char node_id[64];
    char room_id[64];
    double diff_score;
    int width_px;
    int height_px;
    unsigned long frame_sequence;
    const char *observed_at_iso8601;
} camera_frame_motion_input_t;

typedef struct {
    int should_snapshot;
    int should_discard;
    char snapshot_id[96];
    char event_json[768];
} camera_motion_snapshot_decision_t;

const char *camera_motion_snapshot_policy_stub(void);
void camera_motion_snapshot_policy_init(camera_motion_snapshot_policy_t *policy,
                                        double threshold,
                                        int cooldown_frames);
int camera_motion_snapshot_policy_evaluate(camera_motion_snapshot_policy_t *policy,
                                           const camera_frame_motion_input_t *frame,
                                           camera_motion_snapshot_decision_t *decision);

#endif
