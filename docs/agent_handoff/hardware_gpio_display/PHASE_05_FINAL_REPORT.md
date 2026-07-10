# PHASE 05 — Final Validation and Handoff Report

**Phase:** 05 — Full validation and final handoff (both firmware nodes)
**Nodes:** `node_sensor_enviroment`, `node_rfid_finger_print`
**Board:** ESP32-S3-DevKitC-1 (ESP-IDF 5.3.2 / framework-espidf 3.50302.0, PlatformIO Core 6.1.19)
**Date:** 2026-07-11
**Status:** COMPLETE — both firmware images build; RFID host tests pass; one **pre-existing, unrelated** env host-test failure documented. **NOT flashed to physical hardware.**

---

## 1. Executive summary

This phase performed a full static + build + test validation of the GPIO/display
correction work carried out across PHASE 00–04, made exactly **one** in-scope
cleanup, and produced this handoff. No PHASE 00–04 implementation was redone; all
prior work was verified against the actual current source state before any action.

Outcome by evidence class:

| Class | Result |
|-------|--------|
| **Build-verified** | Both nodes build clean via `pio run` (env Flash 82.4%, rfid Flash 82.6%). |
| **Test-verified** | RFID host suite 1/1 PASS. Env host suite 1/1 FAIL — a **pre-existing** provisioning-parser/test-fixture mismatch, unrelated to GPIO/display work (see §8). |
| **Statically verified** | Final GPIO maps match target; no obsolete MQ7 heater code; no GPIO35 analog use; no stale GPIO8/GPIO9 conflict logic; one bus-init per SPI2/I2C/UART1 per node; ST7789 + SSD1306 use only real ESP-IDF `esp_lcd` APIs; no Arduino framework; no fake sensor/display/auth data; no hardcoded secrets; access control fail-closed. |
| **NOT hardware-verified** | No firmware flashed, no display/sensor/relay physically observed. All `TODO_HW_CONFIRM` items remain open. |

**Single change made in PHASE 05:** removed the stale `esp_driver_ledc` entry from
the environment node's `src/CMakeLists.txt` REQUIRES — dead residue from the MQ7
heater PWM removed in PHASE 01 (no source references any `ledc_*` API). Removal is
build-safe (verified by a clean rebuild) and the `driver` umbrella component still
provides LEDC transitively if ever needed.

---

## 2. All files changed across all phases

### Tracked files modified (working tree vs committed HEAD `0cf5a93`)

```
 node_rfid_finger_print/src/CMakeLists.txt  |  2 +   (PHASE 04: +esp_lcd, +esp_driver_i2c)
 node_rfid_finger_print/src/main.c          | 65 +-  (PHASE 03/04: display + fail-closed wiring)
 node_sensor_enviroment/include/mq7.h       | 10 +-  (PHASE 01: heater param/decl removed)
 node_sensor_enviroment/include/mq7_cycle.h |  1 -   (PHASE 01: HV helper decl removed)
 node_sensor_enviroment/src/CMakeLists.txt  |  4 +-  (PHASE 02: +display_st7789.c/+esp_lcd/+esp_driver_spi; PHASE 05: -esp_driver_ledc)
 node_sensor_enviroment/src/main.c          | 52 +-  (PHASE 01/02: heater removal + display wiring)
 node_sensor_enviroment/src/mq7.c           | 96 +-  (PHASE 01: all LEDC/heater code removed)
 node_sensor_enviroment/src/mq7_cycle.c     |  5 -   (PHASE 01: HV helper impl removed)
 8 files changed, 89 insertions(+), 146 deletions(-)
```

### New (untracked) source/header files added

| File | Phase | Purpose |
|------|-------|---------|
| `node_sensor_enviroment/include/board_pins.h` | 01 | Centralized env pin map + ADC1 validation helpers + `board_pins_report_conflicts()`. |
| `node_sensor_enviroment/include/display_st7789.h` | 02 | ST7789 display API + fail-safe/real-data-only policy. |
| `node_sensor_enviroment/src/display_st7789.c` | 02 | Real `esp_lcd` ST7789 (SPI) driver + renderer. |
| `node_rfid_finger_print/include/board_pins.h` | 03 | Centralized RFID pin map. |
| `node_rfid_finger_print/include/display_ssd1306.h` | 04 | SSD1306 display API + fail-safe policy. |
| `node_rfid_finger_print/src/display_ssd1306.c` | 04 | Real `esp_lcd` SSD1306 (I2C) driver + renderer. |

### Documentation added (untracked)

- `docs/hardware_gpio_display_phases/` — phase specs (PHASE 00–05).
- `docs/agent_handoff/hardware_gpio_display/` — completion reports PHASE 00–04 and this final report.

> **PHASE 05 delta only:** one line removed (`esp_driver_ledc`) from
> `node_sensor_enviroment/src/CMakeLists.txt`. No other source was modified in this phase.

---

## 3. Final pin map for both nodes

### Environment sensor node (`node_sensor_enviroment/include/board_pins.h`)

| Function | Signal | GPIO | Bus / mode | Matches target |
|----------|--------|------|-----------|:--------------:|
| MQ7 | AO | 1 | ADC1_CH0 | ✅ |
| GP2Y1014 | AO (analog) | 2 | ADC1_CH1 | ✅ |
| DHT22 | DATA | 4 | GPIO | ✅ |
| GP2Y1014 | LED drive | 6 | GPIO | ✅ |
| BME680 | SDA | 8 | I2C | ✅ |
| BME680 | SCL | 9 | I2C | ✅ |
| ST7789 | CS | 10 | SPI2 | ✅ |
| ST7789 | MOSI | 11 | SPI2 | ✅ |
| ST7789 | SCLK | 12 | SPI2 | ✅ |
| ST7789 | DC | 13 | SPI2 | ✅ |
| ST7789 | RST | 14 | SPI2 | ✅ |
| ST7789 | BL (backlight) | 15 | GPIO | ✅ |

Notes: GPIO1/GPIO2 are ADC1_CH0/CH1 (both ADC-capable on S3; validated at runtime by
`board_pins_is_valid_adc1_channel()` before any analog read). GPIO35 is **not** used
(only a doc comment records that it is not ADC-capable on the S3).

### RFID + fingerprint access node (`node_rfid_finger_print/include/board_pins.h`)

| Function | Signal | GPIO | Bus / mode | Matches target |
|----------|--------|------|-----------|:--------------:|
| Relay (electric lock) | out | 4 | GPIO, active-high | ✅ (unchanged) |
| OLED SSD1306 | SDA | 8 | I2C (auto port) | ✅ |
| OLED SSD1306 | SCL | 9 | I2C (auto port) | ✅ |
| RC522 | CS | 10 | SPI2 | ✅ |
| RC522 | MOSI | 11 | SPI2 | ✅ |
| RC522 | SCK | 12 | SPI2 | ✅ |
| RC522 | MISO | 13 | SPI2 | ✅ |
| RC522 | RST | 14 | — (documented only) | ✅ (see note) |
| TZM1026 | TOUCH_OUT (optional) | 15 | — (reserved, unused) | ✅ |
| TZM1026 | ESP32 RX (← sensor TX_OUT) | 17 | UART1 @57600 | ✅ |
| TZM1026 | ESP32 TX (→ sensor RX_IN) | 18 | UART1 @57600 | ✅ |
| Enroll button | BOOT | 0 | GPIO (pull-up) | (extra) |

Note on RC522 RST (GPIO14): the project's `mfrc522` driver performs a **soft** reset
over SPI (`PCD_SOFT_RESET` written to `CommandReg` in `mfrc522_init`) and exposes no
hardware-RST parameter, so firmware does **not** drive GPIO14. It is recorded as
`BOARD_TARGET_RC522_RST_GPIO` for wiring documentation only. No hardware-RST API was
invented. This is a real, correct constraint, not a gap.

---

## 4. Obsolete code removed

All MQ-7 heater control was removed in PHASE 01 and confirmed fully gone in PHASE 05
(a repo-wide search finds no `ledc_*` API call anywhere in either node):

| Removed | Location | Reason |
|---------|----------|--------|
| `mq7_init(... gpio_num_t heater_control_pin ...)` parameter | `mq7.h` / `mq7.c` | Wired MQ-7 breakout is VCC/GND/AO only; heater is 5 V direct, not MCU-controllable. |
| `void mq7_heater_off(void)` | `mq7.h` / `mq7.c` | No heater GPIO/PWM to turn off. |
| `#include "driver/gpio.h"` in `mq7.h` | `mq7.h` | No GPIO used by the ADC-only driver. |
| All LEDC/PWM heater duty-cycle logic | `mq7.c` (96-line reduction) | Dead once heater is uncontrollable. |
| `mq7_cycle_uses_high_voltage()` decl+impl | `mq7_cycle.h` / `mq7_cycle.c` | Heater HV-phase gate no longer needed. |
| **`esp_driver_ledc` REQUIRE** | `node_sensor_enviroment/src/CMakeLists.txt` | **PHASE 05:** last dangling heater build dependency; no `ledc_*` user remained. |

**Retained (intentionally, not obsolete):** the `mq7_heater_phase_t` enum and
`SENSOR_STATUS_HEATER_WARMUP` telemetry status are naming artifacts of the software
sampling cadence / sensor warm-up window and remain legitimate. The BME680 gas-sensor
heater configuration in `bme680_i2c.c` is real Bosch `bme68x` API usage (an on-sensor
heater the chip manages over I2C, not an MCU GPIO) and was correctly left untouched.

---

## 5. Drivers / dependencies added

All additions are real ESP-IDF 5.3.2 components, verified present in the installed
framework (`~/.platformio/packages/framework-espidf/components/esp_lcd/…`):

| Node | Component(s) added | For |
|------|--------------------|-----|
| env (`node_sensor_enviroment/src/CMakeLists.txt`) | `esp_lcd`, `esp_driver_spi` | ST7789 SPI panel (`esp_lcd_new_panel_st7789`, `spi_bus_initialize`). |
| rfid (`node_rfid_finger_print/src/CMakeLists.txt`) | `esp_lcd`, `esp_driver_i2c` | SSD1306 I2C panel (`esp_lcd_new_panel_ssd1306`, `i2c_new_master_bus`). |

Verified real APIs in use (no invented interfaces): `esp_lcd_new_panel_st7789`,
`esp_lcd_new_panel_ssd1306`, `esp_lcd_new_panel_io_spi`, `esp_lcd_new_panel_io_i2c`,
`esp_lcd_panel_reset/init/draw_bitmap/disp_on_off`, `spi_bus_initialize`,
`i2c_new_master_bus`. No Arduino, TFT_eSPI, Adafruit_GFX, or U8g2 code was introduced
in either node (`platformio.ini` for both remains `framework = espidf` with no
`lib_deps`).

---

## 6. Build commands and exact results

Both builds were run fresh in this phase (PATH `pio` is broken — use the venv binary):

```
cd /home/huynn/smart_home/node_sensor_enviroment && ~/.platformio/penv/bin/pio run
cd /home/huynn/smart_home/node_rfid_finger_print && ~/.platformio/penv/bin/pio run
```

| Node | Result | RAM | Flash |
|------|--------|-----|-------|
| `node_sensor_enviroment` | **[SUCCESS]** (7.35 s) | 21.1% (69,208 / 327,680) | **82.4%** (1,727,600 / 2,097,152) |
| `node_rfid_finger_print` | **[SUCCESS]** (3.89 s) | 23.2% (75,920 / 327,680) | **82.6%** (1,732,740 / 2,097,152) |

The env node build ran **after** the `esp_driver_ledc` removal, so it also confirms
that cleanup is build-safe. Both images are comfortably within the 2 MB app partition.

---

## 7. Test commands and exact results

Host-side (native Linux) unit tests, each run with a **clean** build directory:

```
cd node_sensor_enviroment/test && rm -rf build && mkdir build && cd build && cmake .. && cmake --build . && ctest --output-on-failure
cd node_rfid_finger_print/test && rm -rf build && mkdir build && cd build && cmake .. && cmake --build . && ctest --output-on-failure
```

| Node | Suite | Result |
|------|-------|--------|
| `node_rfid_finger_print` | `access_security_tests` | **1/1 PASS** (0.00 s) |
| `node_sensor_enviroment` | `environment_safety_tests` | **1/1 FAIL** — pre-existing, unrelated (see §8) |

Both host builds compiled cleanly (`build exit: 0`). Note: the host test targets do
not link the display modules (`display_st7789.c` / `display_ssd1306.c` depend on
`esp_lcd`, which is target-only); those are exercised only by the successful `pio run`
target builds.

---

## 8. Existing unrelated failures

**`node_sensor_enviroment` → `environment_safety_tests` →
`test_bounded_provisioning_parser`** aborts at `test_environment_safety.c:116`:

```
Assertion `provisioning_parse_config(valid, sizeof(valid) - 1, &configuration)' failed.
```

Root cause (verified this phase): the test fixture `valid[]`
(`test_environment_safety.c:112–114`) supplies **5** NUL-terminated fields
(ssid, pass, gateway, node_id, room_id), but `provisioning_parse_config()`
(`provisioning_parser.c:105–116`) requires a **6th** field — a 32-byte `auth_key`
with `required = true` — and therefore correctly returns `false`. The positive
assertion then aborts.

This is a **test-fixture ↔ parser contract mismatch confined entirely to the
provisioning/auth-key path**. It is **pre-existing and unrelated** to the GPIO/display
work:

- Neither `provisioning_parser.c` nor `test_environment_safety.c` appears in
  `git status` — both are at committed HEAD, untouched by PHASE 00–05.
- The PHASE 00–05 changes touch only pin maps, MQ7 heater removal, display drivers,
  `main.c`, and `CMakeLists.txt` — none of which is on this code path.

Per the phase rules it is **documented, not silently fixed** (it is out of PHASE 05's
GPIO/display scope). **Recommended follow-up (separate task):** update the test fixture
to append a 32-byte `auth_key` field, OR confirm whether `auth_key` should be optional
during provisioning and adjust `parse_field(..., required)` accordingly. Do not change
it under the GPIO/display effort.

---

## 9. Security verification

| Check | Result |
|-------|--------|
| No hardcoded secrets | ✅ No password/API-key/token/private-key literals in any new or changed source. |
| Credentials from NVS only | ✅ `s_config.wifi_ssid`, `s_config.wifi_pass`, `s_config.auth_key` are all loaded from NVS (`nvs_config_load`); none are literals. |
| No fake auth/sensor/display data | ✅ Env display renders only the exact `environment_raw_sensor_sample_t` sent to the pipeline (offline → last real values, no placeholders). RFID OLED renders only real `access_result_t` / reader / Wi-Fi state. |
| Access control fail-closed | ✅ `access_authorization_evaluate()` initializes every decision to `ACCESS_RESULT_DENIED` / `should_unlock=false`, grants **only** on `ACCESS_SENSOR_CREDENTIAL && credential_is_allowlisted`, uses constant-time hash comparison; no auto-grant path. |
| Relay defaults locked on any failure | ✅ `process_attempt` calls `relay_force_off()` on sequence-persist failure, event-build failure, and relay-unavailable/unlock failure; a failed unlock downgrades the result to `SENSOR_ERROR`, so the OLED can never display GRANTED unless the relay was actually commanded. |
| Display isolated from safety path | ✅ Display init/render failure disables the panel and turns every `show_*` / `render_*` call into a silent no-op; it never touches the relay or the authorization decision. |
| Request signing preserved | ✅ Telemetry/access POSTs are HMAC-signed via `ingest_auth_sign`; sends are skipped if signing is unavailable. |

---

## 10. Hardware electrical warnings

These are **not firmware-verifiable** and must be confirmed on the bench before or
during physical bring-up:

1. **ESP32-S3 strapping pins.** GPIO0 (RFID enroll/BOOT button) is a strapping pin —
   holding it low at reset enters download mode. It is used post-boot as the enroll
   button, which is fine, but avoid holding it during power-on. GPIO45/GPIO46 (not
   used here) are also strapping pins — keep them clear.
2. **Native USB pins.** GPIO19/GPIO20 are the S3 native USB D-/D+. Neither node uses
   them; do not repurpose them if USB-CDC/JTAG is needed.
3. **MQ-7 heater current.** The MQ-7 heater draws significant current (~150 mA) from
   5 V and gets hot; power it from the 5 V rail, not a GPIO, and budget the supply
   accordingly. The MCU only reads AO through the ADC — **confirm the AO divider keeps
   the ADC input within the S3's safe range (≈0–3.1 V at the chosen attenuation).**
4. **GP2Y1014 analog input.** Same ADC-range caution: verify the divider on the dust
   sensor's analog output before connecting it to GPIO2.
5. **Relay drive & flyback.** Confirm GPIO4 active-high matches the relay module's
   trigger polarity; use a proper driver/opto stage and flyback protection for the
   electric lock's inductive load. Do not drive a coil directly from the GPIO.
6. **I2C pull-ups.** SSD1306/BME680 rely on pull-ups on SDA/SCL. Internal pull-ups are
   enabled at 400 kHz, but add external 4.7 kΩ pull-ups (and/or drop to 100 kHz) if the
   bus is long or unreliable.
7. **Display supply voltage.** Confirm each panel's logic level and backlight/VCC
   requirements (many ST7789 boards are 3.3 V logic; some expect specific BL polarity).

---

## 11. Remaining TODOs

Firmware `TODO_HW_CONFIRM` markers (all bench-confirmable, none blocking the build):

- **ST7789 (env):** CS pad presence/usage, color inversion, and backlight polarity are
  assumptions — confirm against the actual panel.
- **SSD1306 (rfid):** I2C address assumed `0x3C` (some modules strap `0x3D`); internal
  pull-ups assumed sufficient. Confirm via an I2C scan if the panel stays blank.
- **RC522 RST (rfid):** GPIO14 is documented but undriven (soft reset only). Confirm the
  module resets reliably over SPI, or wire a hardware RST if a driver that supports it is
  added later.
- **TZM1026 TOUCH_OUT (rfid):** GPIO15 reserved/unused; wire only if hardware wake is
  desired (finger presence is already detected over UART).

Non-hardware follow-up:

- **Env host test fixture** (§8): reconcile the 6-field/`auth_key` parser contract with
  the 5-field `test_bounded_provisioning_parser` fixture. Track as a separate task.

---

## 12. Exact flash / monitor commands to run next

Use the venv PlatformIO binary (PATH `pio` is broken:
`ModuleNotFoundError: No module named 'semantic_version'`). Connect the board over USB
first and verify the serial port.

Environment sensor node:

```
cd /home/huynn/smart_home/node_sensor_enviroment
~/.platformio/penv/bin/pio run -t upload            # build + flash
~/.platformio/penv/bin/pio device monitor -b 115200 # serial monitor (Ctrl+] to exit)
# or combined:
~/.platformio/penv/bin/pio run -t upload -t monitor
```

RFID + fingerprint access node:

```
cd /home/huynn/smart_home/node_rfid_finger_print
~/.platformio/penv/bin/pio run -t upload
~/.platformio/penv/bin/pio device monitor -b 115200
```

If the port is not auto-detected, pass it explicitly, e.g.
`~/.platformio/penv/bin/pio run -t upload --upload-port /dev/ttyACM0` (or `/dev/ttyUSB0`).
Both nodes start **unprovisioned** and bring up BLE Mesh provisioning only until an
`app_config_t` (Wi-Fi, gateway, node/room IDs, and `auth_key`) is written to NVS — do
not expect telemetry/access behavior before provisioning.

---

## 13. What was and was not verified on physical hardware

**Verified in this phase (software only):**

- **Build-verified:** both firmware images compile and link against real ESP-IDF 5.3.2
  APIs via `pio run` (§6).
- **Test-verified:** RFID host security suite passes 1/1; env host suite fails 1/1 on a
  pre-existing, unrelated provisioning-parser fixture issue (§7–8).
- **Statically verified:** final GPIO maps, obsolete-heater removal, no GPIO35 analog
  use, no stale GPIO8/GPIO9 conflict logic, single bus-init per SPI2/I2C/UART1 per node,
  real `esp_lcd` display APIs, no Arduino framework, no fake data, no hardcoded secrets,
  and fail-closed access control (§3–5, §9).

**NOT verified — no physical hardware was involved:**

- No firmware was flashed to any ESP32-S3 board.
- No ST7789 or SSD1306 display was powered or visually observed.
- No MQ-7, GP2Y1014, DHT22, or BME680 sensor was read on real hardware.
- No RC522 card read, TZM1026 fingerprint scan, or relay actuation was performed.
- No I2C address, pull-up adequacy, ADC divider range, backlight/color polarity, or
  relay trigger polarity was electrically confirmed.

**No claim of hardware verification is made.** All `TODO_HW_CONFIRM` items in §11 and
all electrical warnings in §10 remain open and must be confirmed on the bench.

---

## Handoff note

PHASE 05 is complete. The GPIO/display correction effort (PHASE 00–05) is
software-complete and build/test-validated as described above. The only remaining work
is physical hardware bring-up (flash + electrical confirmation of the `TODO_HW_CONFIRM`
items) and the separate, pre-existing env provisioning-parser test-fixture fix (§8).
Do not begin a further phase without explicit instruction.
