# Phase 04 Completion Report

**Phase:** 04 — SSD1306 0.96" I2C OLED Status Display (RFID + Fingerprint access node)
**Node:** `node_rfid_finger_print`
**Board:** ESP32-S3-DevKitC-1 (ESP-IDF 5.3.2 / framework-espidf 3.50302.0, PlatformIO 6.1.19)
**Date:** 2026-07-10
**Status:** COMPLETE — firmware builds, host tests pass. NOT flashed to physical hardware.

---

## 1. Scope completed

Implemented a real SSD1306 128x64 monochrome I2C OLED status display for the
fail-closed RFID + fingerprint access node, using ONLY the native ESP-IDF
`esp_lcd` SSD1306 panel driver over the new `esp_driver_i2c` master driver. No
Arduino/U8g2/Adafruit code was introduced.

The display renders ONLY real runtime states derived from actual firmware state:
boot/init, BLE Mesh provisioning (unprovisioned), no-reader (fail-closed lock),
idle/ready with real reader + Wi-Fi status, enrollment window open, enrollment
result, and the exact per-attempt access decision (granted / denied /
sensor-error). There are no fabricated states, no simulated values, and no
auto-grant hint anywhere in the render path.

The fail-closed access-control path is unchanged and fully isolated from the
display: display init failure or any render error disables the OLED and turns
every `show_*` call into a silent no-op, never touching the relay or the
authorization decision.

---

## 2. Previous completed work preserved

- **PHASE 00–03 pin map:** unchanged. Relay GPIO4, RC522 SPI2 (CS10/MOSI11/
  SCK12/MISO13, RST target GPIO14 unused), TZM1026 UART1 (ESP RX17/TX18 @57600),
  enroll button GPIO0 — all intact.
- **PHASE 03 OLED reservation promoted, not moved:** GPIO8 (SDA) / GPIO9 (SCL)
  were reserved as `BOARD_TARGET_OLED_*` in PHASE 03; PHASE 04 promotes them to
  active `BOARD_OLED_*` config. No previously active pin was changed. GPIO8/9 do
  not conflict with any active pin (RC522 RST target GPIO14 remains unused).
- **Fail-closed logic:** `process_attempt`, `relay_force_off()` calls, the
  no-reader early return, and the unprovisioned early return are all preserved.
  Display calls were added alongside — never in place of — existing safety logic.
- No completed work was redone. Changes continue from the exact prior source state.

---

## 3. Files changed

| File | Change |
|------|--------|
| `node_rfid_finger_print/include/board_pins.h` | OLED pins promoted from `BOARD_TARGET_OLED_*` (reserved) to active `BOARD_OLED_*` config (SDA8/SCL9, port auto, addr 0x3C, 400 kHz, 64 px). |
| `node_rfid_finger_print/include/display_ssd1306.h` | NEW. Display API + documented fail-safe / real-data-only / non-blocking policy. |
| `node_rfid_finger_print/src/display_ssd1306.c` | NEW. esp_lcd SSD1306 bring-up, 1 bpp framebuffer renderer, 8x8 font, double-init guard, fail-safe teardown, shadow-framebuffer change detection. |
| `node_rfid_finger_print/src/CMakeLists.txt` | Added `esp_lcd` and `esp_driver_i2c` to REQUIRES. |
| `node_rfid_finger_print/src/main.c` | Wired `display_ssd1306_*` calls into the real decision points (see §4.3). |

---

## 4. Technical changes

### 4.1 Real driver stack (verified, not invented)

- **I2C master:** `driver/i2c_master.h` — `i2c_new_master_bus()` with
  `i2c_master_bus_config_t` (port `-1` = auto-select a free controller, SDA
  GPIO8, SCL GPIO9, `clk_source = I2C_CLK_SRC_DEFAULT`, `glitch_ignore_cnt = 7`,
  internal pullups enabled). Teardown via `i2c_del_master_bus()`.
- **Panel IO:** `esp_lcd_new_panel_io_i2c()` with
  `esp_lcd_panel_io_i2c_config_t` (`dev_addr = 0x3C`, `scl_speed_hz = 400000`,
  `control_phase_bytes = 1`, `dc_bit_offset = 6`, `lcd_cmd_bits = 8`,
  `lcd_param_bits = 8`). This is the canonical SSD1306 control-byte encoding used
  by Espressif's `i2c_oled` example.
- **Panel:** `esp_lcd_new_panel_ssd1306()` with `esp_lcd_panel_ssd1306_config_t`
  `{ .height = 64 }` as `vendor_config`, `esp_lcd_panel_dev_config_t`
  `{ .reset_gpio_num = -1, .bits_per_pixel = 1 }` (4-pin I2C module has no reset
  line). Bring-up: `esp_lcd_panel_reset()` → `esp_lcd_panel_init()` →
  `esp_lcd_panel_disp_on_off(true)`. All signatures verified against the
  installed `esp_lcd_panel_ssd1306.h/.c` in ESP-IDF 5.3.2.

### 4.2 Framebuffer + rendering

- 1024-byte framebuffer for 128x64 at 1 bpp. Layout matches the driver's
  horizontal addressing mode (memory addressing mode `0x00`, verified in the
  driver's init): byte index `= (y/8)*128 + x`, pixel bit `= 1 << (y & 7)`.
- Pushed with `esp_lcd_panel_draw_bitmap(panel, 0, 0, 128, 64, fb)`.
- 8x8 public-domain `font8x8_basic` glyph table (ASCII 0x20–0x5F), the same
  table already used by the environment node's ST7789 module. Lowercase is
  folded to uppercase; out-of-range chars render `?`. Screens compose a scale-2
  title with up to two scale-1 detail lines, horizontally centered.

### 4.3 State wiring (real runtime state only)

| Screen | Trigger in `main.c` | Source of truth |
|--------|--------------------|-----------------|
| `show_boot()` | top of `app_main` | firmware bring-up |
| `show_provisioning()` | unprovisioned early-return branch | `s_config.provisioned == false` |
| `show_no_reader()` | no-reader early-return branch | `!s_rfid_ready && !s_fingerprint_ready` |
| `show_ready(rfid, fp, wifi)` | idle loop each iteration | `s_rfid_ready`, `s_fingerprint_ready`, `wifi_manager_is_connected()` |
| `show_enroll_open()` | idle loop while window open | `enroll_window_deadline_ms != 0` |
| `show_enroll_result(ok, already)` | after `credential_store_enroll_rfid` | real enroll return + `already_enrolled` |
| `show_result(kind, result)` | end of `process_attempt` | exact `decision.result` after relay outcome |

The result screen reflects the FINAL `decision.result`, including the
downgrade to `ACCESS_RESULT_SENSOR_ERROR` when `relay_trigger_unlock()` fails —
so the OLED can never show "GRANTED/UNLOCKED" unless the relay was actually
commanded.

### 4.4 Non-blocking / fail-safe design

- **Double-init guard:** `s_init_attempted` ensures the I2C bus is created
  exactly once; a second `init()` is logged and rejected.
- **Fail-safe teardown:** any bring-up step failure calls `display_teardown()`
  (deletes panel, panel-IO, and I2C bus in order), logs the cause once, and
  returns false. `s_ready` stays false, so every `show_*` becomes a no-op.
- **Shadow framebuffer:** `fb_commit()` compares the composed frame to a shadow
  of what is on the panel and skips the I2C transfer when unchanged. The idle
  `show_ready()` call in the 200 ms poll loop therefore costs one `memcmp` and
  zero I2C traffic while the screen is static. The only blocking is the
  esp_lcd I2C transfer itself, bounded by the driver's own timeouts, and it runs
  in the `access_monitor_task` context — never on the relay decision itself.

---

## 5. Pin map after this phase

| Function | Signal | GPIO | Bus | Status |
|----------|--------|------|-----|--------|
| Relay (lock) | out | 4 | GPIO | active |
| RC522 | CS / MOSI / SCK / MISO | 10 / 11 / 12 / 13 | SPI2 | active |
| RC522 RST | (soft-reset only) | 14 | — | target, unused |
| TZM1026 | ESP RX / ESP TX | 17 / 18 | UART1 | active |
| TZM1026 TOUCH_OUT | (optional) | 15 | — | reserved, unused |
| Enroll button | BOOT | 0 | GPIO | active |
| **OLED SSD1306** | **SDA / SCL** | **8 / 9** | **I2C (auto)** | **active (new)** |

No conflicts. OLED is the only I2C device on the node and owns the bus exclusively.

---

## 6. Build commands and results

```
~/.platformio/penv/bin/pio run
```

Result: **[SUCCESS]** (33.73 s)
- RAM: 23.2% (75,920 / 327,680 bytes)
- Flash: 82.6% (1,732,740 / 2,097,152 bytes)
- Baseline before PHASE 04 was Flash 81.4%; +1.2% for the esp_lcd/i2c_master
  driver stack and the display module. Comfortably within budget.

---

## 7. Test commands and results

Host-side security/protocol unit tests (native, clean build dir):

```
cd node_rfid_finger_print/test && rm -rf build && mkdir build && cd build
cmake .. && cmake --build .
ctest --output-on-failure
```

Result:
```
1/1 Test #1: access_security_tests ............   Passed    0.00 sec
100% tests passed, 0 tests failed out of 1
```

Note: the host test target compiles a fixed source list (credential privacy +
mfrc522/tzm1026 protocol) and does not link `display_ssd1306.c` (it depends on
`esp_lcd`, which is target-only). The display module is exercised only by the
target build, which links and compiles it cleanly.

---

## 8. Git diff/status summary

```
 M node_rfid_finger_print/src/CMakeLists.txt   (+esp_lcd, +esp_driver_i2c)
 M node_rfid_finger_print/src/main.c           (display_ssd1306_* wiring)
?? node_rfid_finger_print/include/board_pins.h      (OLED pins promoted to active)
?? node_rfid_finger_print/include/display_ssd1306.h (new API)
?? node_rfid_finger_print/src/display_ssd1306.c      (new implementation)
```

All changes are additive to the display concern plus the OLED pin promotion. No
edits were made to the authorization, relay, credential-store, or networking
code paths.

---

## 9. Known issues and TODOs

- **TODO_HW_CONFIRM (I2C address):** assumes the standard 0.96" SSD1306 7-bit
  address `0x3C`. Some modules are strapped to `0x3D`. If the panel stays blank,
  confirm the address (e.g. an I2C scan) and adjust `BOARD_OLED_I2C_ADDR`.
- **TODO_HW_CONFIRM (pullups):** relies on the ESP32-S3 internal pullups at
  400 kHz. Most breakout boards already carry SDA/SCL pullups; if the bus is
  unreliable over longer wiring, add external 4.7 kΩ pullups and/or drop to
  100 kHz (`BOARD_OLED_I2C_SCL_HZ`).
- **Not hardware-verified:** firmware has NOT been flashed to a physical board;
  no OLED was physically observed. Only the build and host tests were run.
- The idle screen is redrawn from the `access_monitor_task` poll loop; there is
  no separate display task. This is intentional (keeps the display strictly
  subordinate to the access path and avoids extra concurrency), and the shadow
  framebuffer keeps the cost negligible.

---

## 10. Hardware verification status

**NOT verified on hardware.** No firmware was flashed and no OLED output was
physically observed. All claims in this report are limited to: (a) a successful
target build against the real ESP-IDF esp_lcd SSD1306 + esp_driver_i2c APIs, and
(b) passing host unit tests. Physical bring-up (address confirmation, pullups,
visible screens) remains outstanding and is listed under §9.

---

## 11. Exact instructions for the next phase

PHASE 04 is complete. **Do not start PHASE 05 without explicit instruction.**

When PHASE 05 begins:
1. Read this report and the PHASE 05 spec fully before editing.
2. Verify PHASE 04 state on real hardware first if a board is available: flash
   `~/.platformio/penv/bin/pio run -t upload`, then confirm the OLED shows BOOT →
   READY, and resolve the `0x3C`/pullup TODOs in §9.
3. Preserve the fail-closed access path and the display's no-op-on-failure
   contract; do not let any new feature block or gate the relay decision.
4. Keep using `~/.platformio/penv/bin/pio` (PATH `pio` is broken:
   `ModuleNotFoundError: semantic_version`).
5. Run the host tests with a clean build dir (`rm -rf build`) after changes.
