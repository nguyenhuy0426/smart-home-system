# Phase 01 Completion Report

Environment-node GPIO correction and MQ-7 heater-logic removal. Date: 2026-07-10.
Scope was limited to `node_sensor_enviroment`. No ST7789 display work was started
(PHASE 02). No changes were made to `node_rfid_finger_print`.

Target hardware: ESP32-S3-DevKitC-1 (`board = esp32-s3-devkitc-1`,
`framework = espidf`, PlatformIO Core 6.1.19, framework-espidf 5.3.2).

## 1. Scope completed

- Read PHASE 01 prompt (`docs/hardware_gpio_display_phases/PHASE_01_ENVIRONMENT_GPIO_AND_MQ7.md`)
  and the PHASE 00 completion report; re-verified `git status`/`git diff` and the
  actual current source before editing.
- Removed **all** MQ-7 external heater-control GPIO logic (module is VCC/GND/AO
  only): LEDC PWM heater driver, the background heater-control task, the
  `heater_control_pin` parameter of `mq7_init()`, the `mq7_heater_off()` API and
  all its call sites, and the now-dead heater-voltage helper in `mq7_cycle.*`.
- Applied the confirmed environment-node pin map in `board_pins.h` (MQ-7 AO and
  GP2Y analog channels un-swapped; GP2Y LED moved off the USB pin to GPIO6;
  BME680 SCL moved to GPIO9).
- Deleted the obsolete old-target conflict machinery (`BOARD_TARGET_*` macros,
  the `#warning`, the GPIO35/GPIO8-double-book warning logic) and kept the ADC1
  validation helpers still used by `main.c`.
- Preserved the valid MQ-7 acquisition path: ADC sampling (16 samples, 2000 us
  spacing), calibration handling, sample-window timing/gating, conversion,
  validation, and error handling.
- Built the environment firmware (SUCCESS) and ran the host test suite
  (the single failure is the pre-existing, unrelated provisioning-parser test).

## 2. Previous completed work preserved

Work from the earlier uncommitted session and PHASE 00 was preserved except
where PHASE 01 explicitly required change:

- The `board_pins.h` centralization architecture and its ADC1 validation
  helpers (`board_pins_adc1_channel_for_gpio`, `board_pins_is_valid_adc1_channel`)
  were kept; only the obsolete conflict/target machinery was removed.
- `main.c`'s consumption of `BOARD_*` macros and its per-sensor ADC1-capability
  guards were kept; only the heater argument, the three `mq7_heater_off()` calls,
  and the two swapped pin/channel assignments changed.
- The MQ-7 cycle/phase model (`mq7_cycle_phase`, `MQ7_PHASE_*`,
  `mq7_heater_phase_name`) and the `mq7_reading_t.phase` field were preserved
  because the telemetry pipeline and host tests depend on them; only the
  physical heater control was removed.
- `node_rfid_finger_print` (both its `main.c` and `board_pins.h`) was left
  completely untouched — outside PHASE 01 scope.

## 3. Files changed

| File | State | Change |
|---|---|---|
| `node_sensor_enviroment/include/mq7.h` | modified | dropped `heater_control_pin` param + `mq7_heater_off()` decl + `driver/gpio.h`; added VCC/GND/AO doc note |
| `node_sensor_enviroment/src/mq7.c` | modified | removed LEDC/PWM heater driver, heater task, `set_heater_phase`, `mq7_heater_off`, heater-fault state; kept ADC read + phase-window sampling |
| `node_sensor_enviroment/include/mq7_cycle.h` | modified | removed `mq7_cycle_uses_high_voltage()` declaration |
| `node_sensor_enviroment/src/mq7_cycle.c` | modified | removed `mq7_cycle_uses_high_voltage()` definition |
| `node_sensor_enviroment/include/board_pins.h` | modified (untracked) | applied confirmed pin map; removed obsolete conflict machinery; kept ADC1 helpers; added MQ-7 AO 5 V voltage-divider TODO |
| `node_sensor_enviroment/src/main.c` | modified | `mq7_init()` call drops heater arg; removed 3 `mq7_heater_off()` calls (replaced ADC-unit-fail path with an error log) |

Untracked build caches under `test/build/` were deleted and regenerated while
running host tests (not source).

## 4. Technical changes

### 4.1 Exact MQ-7 heater code removed

From `src/mq7.c` (LEDC/PWM heater subsystem, entirely deleted):

- Includes: `driver/ledc.h`, `freertos/FreeRTOS.h`, `freertos/task.h`.
- Constants: `MQ7_PWM_FREQUENCY_HZ`, `MQ7_PWM_MAX_DUTY`, `MQ7_HEATER_HIGH_MV`,
  `MQ7_HEATER_LOW_MV`.
- State: `s_heater_fault`, `s_applied_phase`.
- `static bool set_heater_phase(mq7_heater_phase_t)` — computed a high/low PWM
  duty from the voltage ratio and drove `ledc_set_duty`/`ledc_update_duty`.
- `static void heater_control_task(void *)` — the FreeRTOS task that walked the
  60 s/90 s heater cycle and called `set_heater_phase()` every 100 ms.
- In `mq7_init()`: the `ledc_timer_config_t`/`ledc_channel_config_t` setup on
  `heater_control_pin`, the `heater_control_pin < 0` guard, the initial
  `set_heater_phase(MQ7_PHASE_HIGH_HEAT)`, and the `xTaskCreate(heater_control_task, ...)`.
- `void mq7_heater_off(void)` — the whole function (LEDC stop + state reset).
- In `mq7_read()`: the `if (s_heater_fault) { ... IO_ERROR }` block.

From `include/mq7.h`: the `gpio_num_t heater_control_pin` parameter of
`mq7_init()`, the `mq7_heater_off()` declaration, and `#include "driver/gpio.h"`.

From `src/mq7_cycle.c` and `include/mq7_cycle.h`:
`bool mq7_cycle_uses_high_voltage(mq7_heater_phase_t)` (the heater-voltage
selector — its only caller was the removed `set_heater_phase()`).

From `src/main.c`: the `BOARD_MQ7_HEATER_GPIO` argument in the `mq7_init()` call,
and the three `mq7_heater_off()` calls (MQ-7 ADC-channel-invalid branch,
ADC-unit-creation-failure branch, telemetry-task-creation-failure branch).

From `include/board_pins.h`: `BOARD_MQ7_HEATER_GPIO`, `BOARD_TARGET_MQ7_AO_GPIO`,
`BOARD_TARGET_BME680_SDA_GPIO`, `BOARD_TARGET_GP2Y_ANALOG_GPIO`, the
`#warning` on the GPIO8 double-book, and the two obsolete warning branches in
`board_pins_report_conflicts()`.

### 4.2 What was preserved in the MQ-7 acquisition path

- `adc_reader_init()` on the AO ADC1 channel; `adc_reader_read_mv(&r, 16, 2000, ...)`.
- `mq7_calibration_t` copy and the "calibration missing" warning.
- `mq7_cycle_phase()` sample-window gating in `mq7_read()`: the phase timer now
  free-runs purely as a **software sampling cadence** (documented in code), since
  the heater is not MCU-controlled on this breakout. `mq7_read()` still returns
  `SENSOR_STATUS_HEATER_WARMUP` outside the sample window and `mq7_convert_adc()`
  inside it. This preserves the original acquisition timing and keeps the
  telemetry `mq7_phase` field and the host cycle-phase tests valid.

### 4.3 board_pins.h channel/pin corrections

- `BOARD_MQ7_ADC_CHANNEL`: `ADC_CHANNEL_1` (GPIO2) → `ADC_CHANNEL_0` (GPIO1).
- `BOARD_GP2Y_ADC_CHANNEL`: `ADC_CHANNEL_0` (GPIO1) → `ADC_CHANNEL_1` (GPIO2).
- `BOARD_GP2Y_LED_GPIO`: `GPIO_NUM_19` (USB D-) → `GPIO_NUM_6`.
- `BOARD_BME680_SCL_GPIO`: `GPIO_NUM_7` → `GPIO_NUM_9`.
- `BOARD_BME680_SDA_GPIO`: `GPIO_NUM_8` (unchanged); `BOARD_DHT22_DATA_GPIO`:
  `GPIO_NUM_4` (unchanged).
- `board_pins_report_conflicts()` now logs a defensive "same ADC1 channel"
  guard plus the MQ-7 AO 5 V voltage-divider TODO_HW_CONFIRM.

## 5. Pin map after this phase

Environment node (ACTIVE values now match the confirmed target):

| Peripheral | Pin after PHASE 01 | Confirmed target | Match? |
|---|---|---|---|
| MQ-7 AO | GPIO1 / ADC1_CH0 | GPIO1 / ADC1_CH0 | YES |
| MQ-7 heater | (removed — no MCU control) | none | YES |
| GP2Y1014 analog | GPIO2 / ADC1_CH1 | GPIO2 / ADC1_CH1 | YES |
| GP2Y1014 LED | GPIO6 | GPIO6 | YES |
| DHT22 DATA | GPIO4 | GPIO4 | YES |
| BME680 SDA | GPIO8 | GPIO8 | YES |
| BME680 SCL | GPIO9 | GPIO9 | YES |
| ST7789 (10/11/12/13/14/15) | absent | 10..15 | NO (PHASE 02, intentionally unstarted) |

No GPIO conflict remains among the active pins (GPIO1, GPIO2, GPIO4, GPIO6,
GPIO8, GPIO9 are all distinct). GPIO10–15 remain free for the PHASE 02 ST7789.
GPIO1/GPIO2 are valid ADC1 pins on the ESP32-S3 (GPIO1..GPIO10 == ADC1_CH0..CH9).

## 6. Build commands and results

Working `pio` binary: `~/.platformio/penv/bin/pio` (the PATH `pio` is broken —
`ModuleNotFoundError: No module named 'semantic_version'`).

| Command (from `node_sensor_enviroment/`) | Result |
|---|---|
| `~/.platformio/penv/bin/pio run` | **SUCCESS** — RAM 21.0% (68,800 B), Flash 80.6% (1,691,240 / 2,097,152 B). No warnings (the obsolete board_pins `#warning` is gone). |

Flash dropped from 81.0% (PHASE 00) to 80.6% after removing the LEDC heater code.

## 7. Test commands and results

Host-based CMake/CTest suite (Linux host, not on target):

| Command (from `node_sensor_enviroment/test/`) | Result |
|---|---|
| `rm -rf build && cmake -B build -S . && cmake --build build` | **BUILD SUCCESS** — all sources incl. `mq7_cycle.c`, `mq7_conversion.c`, `environment_sensor_pipeline.c` compile clean. |
| `ctest --test-dir build --output-on-failure` | **1/1 FAILED** — `environment_safety_tests` aborts at `test_environment_safety.c:116` in `test_bounded_provisioning_parser`. |

### Pre-existing unrelated failure (unchanged from PHASE 00 §7)

The abort is the same one PHASE 00 documented: `provisioning_parse_config()` in
`provisioning_parser.c` requires **six** NUL-terminated fields (the sixth,
`auth_key`, min length 32), but the test's `valid` blob supplies only **five**
(ssid, pass, gateway_ip, node_id, room_id). The test is stale relative to the
parser.

Proof of non-involvement of PHASE 01 changes:
- The abort is at line 116; the MQ-7 cycle-phase assertions at
  `test_environment_safety.c:84–90` execute **before** line 116 and passed,
  which confirms the `mq7_cycle` edits did not regress the phase logic.
- `provisioning_parser.c` and `test_environment_safety.c` are untouched by
  PHASE 01 (git diff covers only `mq7.*`, `mq7_cycle.*`, `board_pins.h`,
  `main.c`). The failing path links no pin/heater code.
- The failure line, file, and assertion are byte-identical to PHASE 00's report.

Per the phase rules this unrelated failure was reproduced and documented, not
silently fixed. It needs a separate decision (fix the stale test blob to include
a valid 32-byte `auth_key`, or split it out) outside the pin/display phases.

## 8. Git diff/status summary

Branch `main`, HEAD `0cf5a93`. Uncommitted state after PHASE 01:

- `M node_sensor_enviroment/src/mq7.c` (−~80 net): heater PWM/task/`mq7_heater_off` removed.
- `M node_sensor_enviroment/include/mq7.h`: `heater_control_pin` + `mq7_heater_off` + `driver/gpio.h` removed.
- `M node_sensor_enviroment/src/mq7_cycle.c` / `include/mq7_cycle.h`: `mq7_cycle_uses_high_voltage` removed.
- `M node_sensor_enviroment/src/main.c`: heater arg + 3 `mq7_heater_off()` calls removed; pins consumed from board_pins.h.
- `?? node_sensor_enviroment/include/board_pins.h` (untracked): confirmed pin map, obsolete conflict machinery removed.
- Untouched carry-over from earlier work: `M node_rfid_finger_print/src/main.c`,
  `?? node_rfid_finger_print/include/board_pins.h`, `?? docs/hardware_gpio_display_phases/`,
  `?? docs/agent_handoff/` (this report added here).

`git diff --stat` (env node): 5 tracked files, +36 / −117.

## 9. Known issues and TODOs

1. **TODO_HW_CONFIRM (MQ-7 AO voltage)**: the module VCC is 5 V. The real AO
   output swing on the actual breakout must be measured; if it can exceed the
   ESP32-S3 ADC input range, add an external voltage divider before trusting
   GPIO1/ADC1_CH0 readings. Documented in `board_pins.h` and logged at boot.
   I am not sure of the exact AO range without the breakout datasheet.
2. **Heater cadence is now software-only**: the wired MQ-7 breakout cannot
   duty-cycle its heater (VCC/GND/AO only), so the heater runs continuously off
   5 V. The 60 s/90 s/10 s phase timer is retained purely as a sampling cadence
   and no longer reflects a physically switched heater. If future hardware adds a
   heater-control transistor, heater control would need to be reintroduced —
   out of scope here.
3. **Pre-existing env host-test failure** (stale provisioning-parser test,
   §7) — unrelated to pin/display phases; needs a separate fix decision.
4. `pio` on PATH is broken; use `~/.platformio/penv/bin/pio`.
5. Flash usage is 80.6% before any display code; PHASE 02 adds esp_lcd + font/
   render code — monitor partition headroom.
6. GP2Y1014 LED moved to GPIO6 (off the former USB-D- GPIO19); confirm GPIO6 is
   free on the physical wiring harness.

## 10. Hardware verification status

**NOT hardware-verified.** No firmware was flashed to physical hardware during
this phase. All results are host builds and host tests only. ADC readings, the
MQ-7 AO voltage level, the I2C BME680 on GPIO8/GPIO9, the DHT22, and the GP2Y
LED/analog wiring remain unverified on the bench.

## 11. Exact instructions for the next phase

PHASE 02 (`docs/hardware_gpio_display_phases/PHASE_02_*`) — ST7789 display for
the **environment node** — has NOT been started. For PHASE 02:

1. Read this report first; re-verify `git status`/`git diff` and the current
   `board_pins.h` (GPIO10–15 are free and reserved for ST7789 CS/MOSI/SCLK/DC/
   RST/BL — confirm the exact assignment against the phase prompt).
2. Use the real ESP-IDF `esp_lcd` component already shipped in
   framework-espidf 5.3.2 (`esp_lcd_panel_st7789.c`); do not invent a driver.
   SPI-without-CS is supported via `spi_device_interface_config_t.spics_io_num = -1`
   if the physical module lacks CS — that is a TODO_HW_CONFIRM (PHASE 00 §9.6).
3. Add ST7789 pins to `board_pins.h` centrally; verify no conflict with the
   active analog/I2C/DHT pins from this phase.
4. Watch flash headroom (80.6% used now).
5. Do NOT modify `node_rfid_finger_print` (that node's OLED is PHASE 04).
6. Build with `~/.platformio/penv/bin/pio run`; run host tests from `test/`
   with a clean build dir; the provisioning-parser failure (§7) is still
   expected until separately fixed — do not silently fix unrelated modules.
7. Write `PHASE_02_COMPLETION_REPORT.md` in this directory, then STOP.

Do not start PHASE 02 work as part of PHASE 01. PHASE 01 is complete.
