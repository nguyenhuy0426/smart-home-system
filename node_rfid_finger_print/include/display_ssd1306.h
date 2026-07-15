#ifndef DISPLAY_SSD1306_H
#define DISPLAY_SSD1306_H

#include "access_control_pipeline.h" /* access_result_t, access_credential_kind_t */

#include <stdbool.h>

/*
 * Minimal SSD1306 128x64 I2C status-display abstraction (esp_lcd based).
 *
 * Failure policy (PHASE 04 requirement): the display must NEVER take down or
 * delay the fail-closed access-control path. If bring-up fails for any reason
 * (wiring absent, I2C/panel error, allocation failure) display_ssd1306_init()
 * logs the cause once, releases everything it allocated, returns false, and all
 * later show_* calls become no-ops. No show_* function ever influences the
 * authorization decision or the relay.
 *
 * Data policy: only REAL runtime states are rendered. There are no fake or
 * simulated values, no fake authentication states, and no unsafe auto-grant
 * hint anywhere in the render path. The result screen reflects the exact
 * decision.result already produced by access_authorization_evaluate() and the
 * relay outcome.
 *
 * Concurrency/blocking: the module keeps a shadow framebuffer and only pushes
 * over I2C when the composed screen actually changes, so repeatedly calling
 * show_ready() from the idle poll loop performs no I2C traffic. The underlying
 * esp_lcd I2C transfer is bounded by the driver's own timeouts.
 */

/* Bring up the I2C master bus, panel IO and SSD1306 panel exactly once.
 * Returns true when the display is usable. A second call is rejected (logged)
 * so the I2C bus can never be double-initialized. Safe to ignore the return
 * value: on failure every show_* call is a silent no-op. */
bool display_ssd1306_init(void);

/* Firmware bring-up screen. */
void display_ssd1306_show_boot(void);

/* Node is unprovisioned and BLE Mesh provisioning is running. Local readers
 * keep operating (fail-closed), so the REAL reader readiness is shown too. */
void display_ssd1306_show_provisioning(bool rfid_ready, bool fingerprint_ready);

/* No credential reader is available; the relay is held OFF (fail-closed). */
void display_ssd1306_show_no_reader(void);

/* Idle screen: waiting for a card / fingerprint. Shows the REAL reader
 * readiness and Wi-Fi link state. Cheap to call every poll iteration. */
void display_ssd1306_show_ready(bool rfid_ready, bool fingerprint_ready,
                                bool wifi_connected);

/* RFID enrollment window is open; present a card. */
void display_ssd1306_show_enroll_open(void);

/* Result of an RFID enrollment attempt (all three are real outcomes of
 * credential_store_enroll_rfid). */
void display_ssd1306_show_enroll_result(bool succeeded, bool already_enrolled);

/* Access attempt outcome. `kind` and `result` are the exact values already
 * decided by the access-control pipeline for this attempt. */
void display_ssd1306_show_result(access_credential_kind_t kind,
                                 access_result_t result);

#endif /* DISPLAY_SSD1306_H */
