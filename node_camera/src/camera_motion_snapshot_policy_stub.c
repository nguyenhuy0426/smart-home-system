/*
 * Responsibility: placeholder for exposing camera status to the gateway-owned
 * motion detection, snapshot upload, and retention pipeline.
 */
#include "camera_motion_snapshot_policy.h"

#include <stdio.h>
#include <string.h>

const char *camera_motion_snapshot_policy_stub(void)
{
    return "camera-motion-snapshot-policy-ready";
}

void camera_motion_snapshot_policy_init(camera_motion_snapshot_policy_t *policy,
                                        double threshold,
                                        int cooldown_frames)
{
    if (policy == NULL) {
        return;
    }

    policy->threshold = threshold > 0.0 ? threshold : 0.55;
    policy->cooldown_frames = cooldown_frames > 0 ? cooldown_frames : 3;
    policy->frames_since_snapshot = cooldown_frames > 0 ? cooldown_frames : 3;
    policy->motion_active = 0;
    policy->snapshot_retention_days = 30;
    policy->metadata_retention_days = 180;
}

int camera_motion_snapshot_policy_evaluate(camera_motion_snapshot_policy_t *policy,
                                           const camera_frame_motion_input_t *frame,
                                           camera_motion_snapshot_decision_t *decision)
{
    const char *observed_at;
    int above_threshold;

    if (policy == NULL || frame == NULL || decision == NULL) {
        return 0;
    }

    memset(decision, 0, sizeof(*decision));
    observed_at = frame->observed_at_iso8601 != NULL ?
            frame->observed_at_iso8601 : "1970-01-01T00:00:00Z";
    above_threshold = frame->diff_score >= policy->threshold;

    if (!above_threshold) {
        policy->motion_active = 0;
        policy->frames_since_snapshot++;
        decision->should_discard = 1;
        return 1;
    }

    if (!policy->motion_active || policy->frames_since_snapshot >= policy->cooldown_frames) {
        policy->motion_active = 1;
        policy->frames_since_snapshot = 0;
        decision->should_snapshot = 1;
        (void)snprintf(decision->snapshot_id, sizeof(decision->snapshot_id),
                "snap_%s_%08lu", frame->node_id, frame->frame_sequence);
        (void)snprintf(decision->event_json, sizeof(decision->event_json),
                "{"
                "\"snapshotId\":\"%s\","
                "\"nodeId\":\"%s\","
                "\"roomId\":\"%s\","
                "\"eventType\":\"video.motion_snapshot\","
                "\"observedAt\":\"%s\","
                "\"storagePath\":\"homes/home_stub/videoSnapshots/%s.jpg\","
                "\"contentType\":\"image/jpeg\","
                "\"widthPx\":%d,"
                "\"heightPx\":%d,"
                "\"motion\":{\"algorithm\":\"frame_difference_stub\",\"score\":%.3f,\"threshold\":%.3f},"
                "\"retention\":{\"snapshotRetentionDays\":%d,\"metadataRetentionDays\":%d,\"configurable\":true}"
                "}",
                decision->snapshot_id,
                frame->node_id,
                frame->room_id,
                observed_at,
                decision->snapshot_id,
                frame->width_px,
                frame->height_px,
                frame->diff_score,
                policy->threshold,
                policy->snapshot_retention_days,
                policy->metadata_retention_days);
        return 1;
    }

    policy->frames_since_snapshot++;
    decision->should_discard = 1;
    return 1;
}
