# Phase 02 Completion Report

Date: 2026-07-10
Node: `/home/huynn/smart_home/node_sensor_enviroment` (ONLY — the RFID node was not touched this phase)
Phase spec: `docs/hardware_gpio_display_phases/PHASE_02_ENVIRONMENT_ST7789_DISPLAY.md`
Previous reports read before starting: `PHASE_00_COMPLETION_REPORT.md`, `PHASE_01_COMPLETION_REPORT.md`

## 1. Scope completed

Added real ST7789 1.3" 240x240 SPI display support to the environment sensor
node, exactly and only per the PHASE 02 spec:

- Display driver: **native ESP-IDF `esp_lcd` component** (`esp_lcd_new_panel_st7789`).
  No TFT_eSPI, no Arduino, no third-party display library.
- Why compatible with the installed IDF: the toolchain in this repo is
  PlatformIO `framework-espidf @ 3.50302.0 (5.3.2)`. The installed package at
  `~/.platformio/packages/framework-espidf/components/esp_lcd/` ships
  `src/esp_lcd_panel_st7789.c` and public headers `esp_lcd_panel_io.h`,
  `esp_lcd_panel_vendor.h` (declares `esp_lcd_new_panel_st7789`),
  `esp_lcd_panel_ops.h`. Every API used in this phase was verified against
  those installed headers before use (signatures, config-struct fields, the
  `esp_lcd_panel_io_color_trans_done_cb_t` typedef in `esp_lcd_types.h:69`,
  and `LCD_RGB_DATA_ENDIAN_LITTLE` RAMCTRL handling in
  `esp_lcd_panel_st7789.c` ~line 110). No API was invented.
- New small abstraction module: `include/display_st7789.h` +
  `src/display_st7789.c`.
- SPI bus (`SPI2_HOST`) is initialized exactly once; a double-init guard in
  `display_st7789_init()` rejects and logs any second call.
- Display failure can never crash or degrade the sensor pipeline: every init
  failure logs the failing step once, tears down everything allocated
  (panel → panel IO → SPI bus → DMA buffer → semaphore), returns `false`,
  and all subsequent render calls become no-ops.
- Only real pipeline values are displayed (the same
  `environment_raw_sensor_sample_t` sent to telemetry). Sensor errors are
  shown as their real `sensor_status_name()` text. No fake/simulated values
  exist anywhere in the render path.

NOT done (correctly out of scope): PHASE 03 (RFID/fingerprint GPIO), any
change to `node_rfid_finger_print`, any change to sensor acquisition logic.

## 2. Previous completed work preserved

- PHASE 01 MQ-7 heater removal untouched: `mq7.h/.c`, `mq7_cycle.h/.c`
  unchanged this phase; software sampling cadence still in place.
- PHASE 01 confirmed pin map preserved verbatim in `board_pins.h`
  (DHT22=GPIO4, GP2Y LED=GPIO6, BME680 SDA/SCL=GPIO8/9, MQ7 AO=ADC1_CH0/GPIO1,
  GP2Y AO=ADC1_CH1/GPIO2); the ST7789 block was appended, nothing edited.
- `board_pins_report_conflicts()`, ADC channel validation in `app_main`,
  fail-closed provisioning flow, telemetry signing, fusion init: all
  untouched.
- The pre-existing RFID-node diff in git (from work before this phase
  series) was left exactly as found.

## 3. Files changed

| File | Change |
|---|---|
| `node_sensor_enviroment/include/board_pins.h` | Added `hal/spi_types.h` include + ST7789 pin block (`BOARD_ST7789_*`) |
| `node_sensor_enviroment/include/display_st7789.h` | NEW — display abstraction API (2 functions) |
| `node_sensor_enviroment/src/display_st7789.c` | NEW — esp_lcd ST7789 bring-up, 8x8 font renderer, fail-safe policy |
| `node_sensor_enviroment/src/main.c` | `#include "display_st7789.h"`; init after fusion init (warn-and-continue on failure); render call in online path after `post_telemetry`; Wi-Fi-only render (`sample=NULL`) in offline branch |
| `node_sensor_enviroment/src/CMakeLists.txt` | SRCS + `display_st7789.c`; REQUIRES + `esp_driver_spi`, `esp_lcd` |

No other files were modified in this phase.

## 4. Technical changes

### 4.1 Driver stack (all real, verified in installed IDF 5.3.2)

```
spi_bus_initialize(SPI2_HOST, …, SPI_DMA_CH_AUTO)          // esp_driver_spi
esp_lcd_new_panel_io_spi(SPI2_HOST, &io_cfg, &io)          // esp_lcd
esp_lcd_new_panel_st7789(io, &panel_cfg, &panel)           // esp_lcd (vendor)
esp_lcd_panel_reset / _init / _invert_color(true)
    / _disp_on_off(true) / _draw_bitmap
```

- `io_cfg`: CS=GPIO10, DC=GPIO13, `spi_mode=0`, `pclk_hz=20 MHz`,
  `trans_queue_depth=10`, `lcd_cmd_bits=8`, `lcd_param_bits=8`,
  `on_color_trans_done` callback registered.
- `panel_cfg`: `reset_gpio_num=GPIO14`, `rgb_ele_order=LCD_RGB_ELEMENT_ORDER_RGB`,
  `data_endian=LCD_RGB_DATA_ENDIAN_LITTLE`, `bits_per_pixel=16`.
  Little-endian RGB565 is handled by the ST7789 RAMCTRL register inside the
  IDF 5.3 driver, so the renderer needs no software byte-swap.
- Backlight GPIO15 is a plain `gpio_config` output: low during bring-up
  (hides garbage frames), high only after the panel is initialized and
  cleared to black.

### 4.2 Rendering design (no blocking, no refresh spam, no log spam)

- Text grid: 15 cols x 15 rows of 16x16 cells (public-domain
  `font8x8_basic` glyphs, 0x20..0x5F subset, scaled 2x; lowercase mapped to
  uppercase, out-of-range chars render `?`). 9 rows used.
- One DMA-capable stripe buffer (240 x 16 px x 2 B = 7,680 B,
  `heap_caps_malloc(MALLOC_CAP_DMA)`) is reused for every row. A binary
  semaphore enforces ownership: taken (1 s timeout) before the CPU refills
  the stripe, given back by the `on_color_trans_done` ISR callback when DMA
  finishes, and given back manually if `draw_bitmap` fails.
- Per-line cache (text + color): a row is redrawn only when its content
  actually changed. Render is called once per telemetry tick (~5 s) from the
  existing telemetry task — no extra task, no timer, no per-frame refresh.
- Draw-failure logs are throttled (first occurrence, then every 60th).
- Line layout (colors: white = VALID, red = any non-valid status):
  row0 `ENV WIFI UP/DOWN`, row1 DHT22 T/H, row2 BME680 T/H, row3 pressure,
  row4 gas kOhm, row5 CO (VALID→ppm, CALIBRATION_MISSING→real raw mV
  honestly labeled `CO RAW`, HEATER_WARMUP→`CO WARMUP`, else status name),
  row6 PM2.5 (same pattern), row7 uptime seconds, row8 sequence number.
- Offline: `display_st7789_render_sample(NULL, false)` updates only the
  Wi-Fi row; last real sensor values stay on screen. No placeholder numbers.

### 4.3 Runtime update strategy (summary for next phases)

Init once in `app_main` (after sensor/fusion init, before Wi-Fi). Update
once per 5 s telemetry tick inside `sample_and_publish_task`, after the
HTTP post, mirroring the exact posted sample. Worst case a fully changed
frame is 9 stripe transfers of 7,680 B at 20 MHz SPI (≈3 ms each) — sensor
acquisition is never delayed measurably, and a hung SPI can stall a render
call at most 1 s per row via the semaphore timeout, never indefinitely.

## 5. Pin map after this phase

| Peripheral | Signal | GPIO | Notes |
|---|---|---|---|
| DHT22 | DATA | 4 | unchanged (PHASE 01) |
| GP2Y1014 | LED pulse | 6 | unchanged |
| GP2Y1014 | AO | 2 (ADC1_CH1) | unchanged |
| MQ-7 | AO | 1 (ADC1_CH0) | unchanged; 5 V divider TODO_HW_CONFIRM |
| BME680 | SDA / SCL | 8 / 9 | unchanged |
| ST7789 | CS | 10 | NEW; TODO_HW_CONFIRM — set −1 in `board_pins.h` if module has no CS pad |
| ST7789 | MOSI (SDA) | 11 | NEW |
| ST7789 | SCLK (SCL) | 12 | NEW |
| ST7789 | DC | 13 | NEW |
| ST7789 | RST (RES) | 14 | NEW |
| ST7789 | BL (BLK) | 15 | NEW; assumed active-high (TODO_HW_CONFIRM) |

No conflicts: GPIO10–15 are free, ADC-independent pins on the ESP32-S3
DevKitC-1 and collide with nothing above (GPIO19/20 native USB avoided).

## 6. Build commands and results

```
cd /home/huynn/smart_home/node_sensor_enviroment
~/.platformio/penv/bin/pio run          # PATH `pio` is broken; use this path
```

Result: **SUCCESS** (full build 35.77 s).
`RAM: 21.1% (69,208 / 327,680 B)` — `Flash: 82.4% (1,727,600 / 2,097,152 B)`.
Flash grew 80.6% → 82.4% vs PHASE 01 (esp_lcd driver + font + module).
A forced clean recompile of `display_st7789.c` and `main.c` (objects deleted,
rebuilt) produced **zero warnings, zero errors**; `display_st7789_init` /
`display_st7789_render_sample` confirmed present in `firmware.elf` via nm.

## 7. Test commands and results

```
cd /home/huynn/smart_home/node_sensor_enviroment/test
rm -rf build            # mandatory: stale caches cause bogus -Werror failures
cmake -S . -B build && cmake --build build     # exit 0, no warnings
ctest --test-dir build --output-on-failure
```

Result: `0% tests passed, 1 tests failed out of 1`.

### Pre-existing unrelated failure (unchanged from PHASE 00 §7 / PHASE 01 §7)

`test_environment_safety.c:116: test_bounded_provisioning_parser: Assertion
'provisioning_parse_config(valid, sizeof(valid) - 1, &configuration)' failed.`
Same root cause documented since PHASE 00: the test fixture provides 5
provisioning fields while the parser requires 6 (including `auth_key`
≥ 32 chars). Provably unrelated to PHASE 02: the display module is
hardware-bound and intentionally **not** part of the host-test build, and no
host-tested source was modified this phase. All assertions before line 116
(including the MQ-7 cycle asserts) passed. Per phase rules this failure was
reproduced and documented, not fixed.

## 8. Git diff/status summary

PHASE 02 changes only:

```
M  node_sensor_enviroment/include/board_pins.h   (ST7789 block appended)
A? node_sensor_enviroment/include/display_st7789.h   (new, untracked)
A? node_sensor_enviroment/src/display_st7789.c       (new, untracked)
M  node_sensor_enviroment/src/main.c              (+display init/render wiring)
M  node_sensor_enviroment/src/CMakeLists.txt      (+1 SRC, +2 REQUIRES)
```

Still present from earlier work, untouched this phase: PHASE 01 mq7/board
pin diffs, pre-existing `node_rfid_finger_print/src/main.c` diff and its
untracked `board_pins.h`, `docs/` additions. Nothing was committed.

## 9. Known issues and TODOs

Hardware-confirmation TODOs (all marked `TODO_HW_CONFIRM` in source; none
block compilation and all fail safe at runtime):

1. **CS pad presence** — many 1.3" 240x240 ST7789 breakouts have no CS pin.
   Default wiring assumes CS→GPIO10; if the physical module has no CS pad,
   set `BOARD_ST7789_CS_GPIO` to `-1` (real esp_lcd no-CS support). I am not
   sure which variant is installed until the hardware is inspected; no-CS
   modules also commonly require `spi_mode = 3` instead of `0`
   (`display_st7789.c`, io_config).
2. **Color inversion** — `invert_color(true)` is the common requirement for
   1.3" IPS 240x240 panels; flip to `false` if colors render inverted.
3. **Backlight polarity** — BLK assumed active-high.
4. **Panel gap/orientation** — no `esp_lcd_panel_set_gap`/mirror/swap is
   applied (defaults). Some 240x240 modules need an 80-px Y offset in
   non-default orientations; verify on real hardware.
5. Carried over from PHASE 01: MQ-7 AO 5 V swing must be measured; add a
   divider if it exceeds the ADC range. Host provisioning test fixture
   still needs its 6th field (pre-existing, out of scope).

## 10. Hardware verification status

**NOT hardware-verified.** No firmware was flashed to a physical board in
this phase; no physical display was attached or tested. All results above
are compile/link/host-test results only. The TODO_HW_CONFIRM items in §9
must be resolved during first flash.

## 11. Exact instructions for the next phase

Next phase: **PHASE 03 — RFID and Fingerprint GPIO Correction**
(`docs/hardware_gpio_display_phases/PHASE_03_RFID_FINGERPRINT_GPIO.md`).

- Read PHASE 00/01/02 completion reports, then inspect code and git diff.
- Modify **only** `/home/huynn/smart_home/node_rfid_finger_print`. Do not
  touch the environment node (PHASES 01–02 complete there).
- No OLED implementation yet — pin/dependency preparation for PHASE 04 only.
- Confirmed pin map: SSD1306 SDA/SCL→GPIO8/9; RC522 CS→10, MOSI→11, SCK→12,
  MISO→13, RST→14, IRQ n/c; TZM1026 TOUCH_OUT→15 (optional), ESP32 RX=17,
  ESP32 TX=18. Preserve the working relay GPIO unless a verified conflict.
- Remove the obsolete GPIO9 RC522-RST conflict. Verify whether the actual
  RC522 driver supports hardware RST — do not invent an RST API; if
  unsupported, document honestly.
- Preserve secure access-control / fail-closed logic; no auto-grant.
- Note: there is a **pre-existing uncommitted diff** in
  `node_rfid_finger_print/src/main.c` and an untracked
  `node_rfid_finger_print/include/board_pins.h` from before this phase
  series — inspect them first; they may already implement part of PHASE 03.
- Build with `~/.platformio/penv/bin/pio run` (PATH `pio` is broken:
  ModuleNotFoundError semantic_version). Clean any host-test build dir
  before configuring (`rm -rf build`).
- Write `PHASE_03_COMPLETION_REPORT.md` in this directory. Do not start
  PHASE 04.
