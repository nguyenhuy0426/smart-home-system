#ifndef DISPLAY_ST7789_H
#define DISPLAY_ST7789_H

#include "environment_sensor_pipeline.h"

#include <stdbool.h>

typedef enum {
    DISPLAY_ST7789_THEME_DARK = 0,
    DISPLAY_ST7789_THEME_LIGHT,
} display_st7789_theme_t;

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

/* Select one of the two required high-contrast themes. The next render uses
 * the selected palette; no sensor values are changed or synthesized. */
void display_st7789_set_theme(display_st7789_theme_t theme);

/* Render the latest real pipeline sample once per telemetry tick (~5 s).
 * The sample carries the validated epoch or zero when unsynchronized; the
 * renderer never invents clock data. No-op for sample == NULL or when the
 * display is unavailable. */
void display_st7789_render_sample(
        const environment_raw_sensor_sample_t *sample,
        bool wifi_connected,
        bool provisioned,
        bool ble_mesh_ready);

#endif /* DISPLAY_ST7789_H */
