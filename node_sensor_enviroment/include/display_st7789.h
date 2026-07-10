#ifndef DISPLAY_ST7789_H
#define DISPLAY_ST7789_H

#include "environment_sensor_pipeline.h"

#include <stdbool.h>

/*
 * Minimal ST7789 1.3" 240x240 status-display abstraction (esp_lcd based).
 *
 * Failure policy (PHASE 02 requirement): the display must never take down
 * the sensor pipeline. If bring-up fails for any reason (wiring absent,
 * SPI/panel error, allocation failure) display_st7789_init() logs the
 * cause once, releases everything it allocated, returns false, and all
 * later render calls become no-ops.
 *
 * Data policy: only real pipeline values are rendered. Sensor errors are
 * shown as their sensor_status_name() text; no fake or simulated numbers
 * are ever drawn.
 */

/* Bring up the SPI bus, panel IO and ST7789 panel exactly once.
 * Returns true when the display is usable. A second call is rejected
 * (logged) so the SPI bus can never be double-initialized. */
bool display_st7789_init(void);

/* Render the latest real pipeline sample. Called once per telemetry tick
 * (~5 s); only lines whose content changed are redrawn.
 *
 * sample == NULL updates only the Wi-Fi status line (offline heartbeat)
 * and leaves the last real values on screen. No-op when the display is
 * unavailable. */
void display_st7789_render_sample(
        const environment_raw_sensor_sample_t *sample, bool wifi_connected);

#endif /* DISPLAY_ST7789_H */
