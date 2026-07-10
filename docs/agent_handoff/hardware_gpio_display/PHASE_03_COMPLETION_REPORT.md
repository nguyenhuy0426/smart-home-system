# Phase 03 Completion Report

RFID + fingerprint access-node GPIO correction. Date: 2026-07-10.
Scope was limited to `/home/huynn/smart_home/node_rfid_finger_print`. No OLED
functionality was implemented (PHASE 04). No other node was touched.

Target hardware: ESP32-S3-DevKitC-1 (`board = esp32-s3-devkitc-1`,
`framework = espidf`, PlatformIO Core 6.1.19, framework-espidf 5.3.2).

## 1. Scope completed

- Read the PHASE 03 prompt
  (`docs/hardware_gpio_display_phases/PHASE_03_RFID_FINGERPRINT_GPIO.md`) and the
  PHASE 00 / 01 / 02 completion reports before editing.
- Re-verified `git status`, `git diff`, and the actual current source of the
  RFID node against the reports (main.c, board_pins.h, mfrc522, tzm1026,
  relay, access-control modules, host test).
- Corrected the obsolete RC522 RST target in `board_pins.h`: the confirmed
  wiring routes RST to **GPIO14**, not the old GPIO9, so the stale
  GPIO9 double-book (OLED SCL vs RC522 RST) no longer exists.
- Removed the now-invalid `#warning` that fired on that obsolete GPIO9
  double-book, and updated the OLED-pin comment accordingly.
- Verified the RC522 driver's reset capability (soft-reset over SPI only, no
  hardware RST pin) and documented it honestly instead of inventing an RST API.
- Verified — and left untouched — the already-correct pin centralization and
  TZM1026 UART orientation in `main.c` from prior work (not redone).
- Verified the secure fail-closed access-control logic is intact (no
  auto-grant).
- Built the RFID firmware (SUCCESS) and ran the host security test suite
  (100% pass).

## 2. Previous completed work preserved

The earlier uncommitted work on this node (documented in PHASE 00 §2 and
carried through PHASES 01–02) was verified against current source and left
**unchanged** except for the single obsolete RST-target correction above:

- `src/main.c` already consumes `BOARD_*` macros from `board_pins.h` (local
  `#define`s removed). **Not touched this phase.**
- The TZM1026 TX/RX cross-over is already correct: sensor TX_OUT lands on the
  ESP RX pin, sensor RX_IN on the ESP TX pin. `main.c` passes ESP-side TX/RX to
  `tzm1026_init(uart, esp_tx, esp_rx, baud)` as
  `tzm1026_init(BOARD_TZM_UART, BOARD_TZM_ESP_TX_GPIO, BOARD_TZM_ESP_RX_GPIO,
  BOARD_TZM_BAUD_RATE)`. ESP RX = GPIO17, ESP TX = GPIO18. **Preserved.**
- Baud rate stays **57600** (existing project value; no verified evidence to
  change it). **Preserved.**
- Relay stays on **GPIO4**, active-high, 5000 ms unlock pulse. No verified
  conflict exists, so it was preserved per the phase rule. **Preserved.**
- Enroll button stays on GPIO0 (BOOT), 3 s hold / 30 s window. **Preserved.**
- RC522 SPI pins CS=10, MOSI=11, SCK=12, MISO=13 on SPI2_HOST already match the
  confirmed target. **Preserved.**
- TOUCH_OUT reserved as GPIO15, unused (finger presence is already detected
  over UART). No interrupt/debounce code was added because the signal is not
  used — per the phase rule. **Preserved.**
- All access-control / credential / privacy modules and the host test were left
  byte-for-byte unchanged.

## 3. Files changed

| File | State | Change |
|---|---|---|
| `node_rfid_finger_print/include/board_pins.h` | modified (untracked) | `BOARD_TARGET_RC522_RST_GPIO` 9 → 14; rewrote the RST comment to record soft-reset-only status; removed the obsolete GPIO9 double-book `#if/#warning/#endif`; updated OLED comment to drop the stale RST-collision note |

No other file was modified in this phase. `src/main.c` shows only the
pre-existing (prior-work) diff — it was recompiled because `board_pins.h`
changed, but its source was not edited this phase.

Untracked host-test build cache under `test/build/` was deleted and regenerated
while running tests (not source).

## 4. Technical changes

### 4.1 RC522 RST target correction (the only source edit)

Before:
```c
/* Target wires RC522 RST -> GPIO9. The mfrc522 driver soft-resets over SPI and
 * exposes no RST pin, and GPIO9 also collides with the OLED SCL target below.
 * Not wired in firmware pending hardware confirmation. */
#define BOARD_TARGET_RC522_RST_GPIO 9
...
#if BOARD_TARGET_OLED_SCL_GPIO == BOARD_TARGET_RC522_RST_GPIO
#warning "board_pins: GPIO9 double-booked (OLED SCL vs RC522 RST) - neither configured, see TODO_HW_CONFIRM"
#endif
```

After:
```c
/* Confirmed wiring routes RC522 RST -> GPIO14 (was previously mis-targeted at
 * GPIO9, which collided with the OLED SCL target; that conflict is now gone).
 * The mfrc522 driver in this project performs a SOFT reset over SPI
 * (PCD_SOFT_RESET / 0x0F written to CommandReg inside mfrc522_init) and exposes
 * NO hardware RST pin parameter, so firmware does not drive GPIO14. ... */
#define BOARD_TARGET_RC522_RST_GPIO 14
```
The `#if/#warning/#endif` block was deleted (no conflict remains). The OLED
comment's "SCL target GPIO9 also collides with the RC522 RST target" clause was
removed.

### 4.2 RC522 hardware-RST verification (verified, not invented)

Verified directly in `src/mfrc522.c`:
- `mfrc522_init(spi_host_device_t host_id, int mosi_pin, int miso_pin,
  int sck_pin, int cs_pin)` — the signature has **no RST pin parameter**
  (`mfrc522.h:18-19`).
- Reset is performed in software: `#define PCD_SOFT_RESET 0x0F` (`mfrc522.c:32`)
  and `write_reg(CommandReg, PCD_SOFT_RESET)` inside `mfrc522_init`
  (`mfrc522.c:178`), followed by `ets_delay_us(50000)` and a VersionReg
  presence check. This is the standard MFRC522 SoftReset command written over
  SPI — a real datasheet-defined operation, not an invented API.

**Conclusion: the RC522 driver has NO hardware-reset (RST GPIO) support.**
GPIO14 is therefore recorded as a documented wiring target only and is **not
driven by firmware**. No RST API was invented; the RST wire (if present) may be
held high externally / by the module. This is a `TODO_HW_CONFIRM` item — see §9.

### 4.3 TZM1026 UART orientation verification (unchanged, confirmed correct)

- `BOARD_TZM_ESP_RX_GPIO = GPIO_NUM_17` (connects to sensor TX_OUT).
- `BOARD_TZM_ESP_TX_GPIO = GPIO_NUM_18` (connects to sensor RX_IN).
- `main.c` calls `tzm1026_init(BOARD_TZM_UART, BOARD_TZM_ESP_TX_GPIO,
  BOARD_TZM_ESP_RX_GPIO, BOARD_TZM_BAUD_RATE)` — the ESP-side TX/RX GPIOs are
  passed in the driver's `(uart, tx, rx, baud)` order, matching the confirmed
  map (ESP RX=17, ESP TX=18) and the crossover wiring. Baud = 57600, unchanged.
  No change was required.

### 4.4 Security / fail-closed verification (unchanged, confirmed intact)

- `access_authorization_evaluate()` (`src/access_authorization.c:42-60`)
  initializes every decision to `ACCESS_RESULT_DENIED` / `should_unlock=false`
  and only sets GRANTED + unlock when
  `sensor_status == ACCESS_SENSOR_CREDENTIAL && credential_is_allowlisted`.
  Sensor MALFORMED/MISSING/TIMEOUT yield `ACCESS_RESULT_SENSOR_ERROR` (no
  unlock). There is **no auto-grant path**.
- `main.c` reinforces fail-closed everywhere: relay is forced OFF on NVS
  failure, unprovisioned boot, sequence-persist failure, event-build failure,
  relay-not-ready, and when no reader is available; an unlock is only commanded
  via `relay_trigger_unlock()` after a GRANTED decision, and any relay failure
  downgrades the result to SENSOR_ERROR and forces the relay OFF.
- Host tests `test_unknown_card_never_unlocks` and
  `test_missing_hardware_never_unlocks` exercise these paths and pass.

## 5. Pin map after this phase

RFID / fingerprint node (ACTIVE + documented targets):

| Peripheral | Signal | GPIO | Status |
|---|---|---|---|
| Relay | out (active-high, 5000 ms pulse) | 4 | ACTIVE — preserved |
| RC522 | CS | 10 | ACTIVE (SPI2_HOST) |
| RC522 | MOSI | 11 | ACTIVE |
| RC522 | SCK | 12 | ACTIVE |
| RC522 | MISO | 13 | ACTIVE |
| RC522 | RST | 14 | documented target only — driver soft-resets over SPI, GPIO not driven (was GPIO9, corrected) |
| RC522 | IRQ | — | not connected |
| TZM1026 | ESP RX (← sensor TX_OUT) | 17 | ACTIVE (UART1 @ 57600) |
| TZM1026 | ESP TX (→ sensor RX_IN) | 18 | ACTIVE |
| TZM1026 | TOUCH_OUT | 15 | reserved, unused (optional) |
| Enroll button | BOOT | 0 | ACTIVE — preserved |
| SSD1306 OLED | SDA | 8 | reserved target, no driver (PHASE 04) |
| SSD1306 OLED | SCL | 9 | reserved target, no driver (PHASE 04) |

No GPIO conflict remains. Active pins (0, 4, 10, 11, 12, 13, 17, 18) are all
distinct; the documented-only pins (RST 14, TOUCH_OUT 15, OLED 8/9) collide with
nothing active. The former GPIO9 RST/OLED-SCL double-book is resolved (RST→14).

## 6. Build commands and results

Working `pio` binary: `~/.platformio/penv/bin/pio` (the PATH `pio` is broken —
`ModuleNotFoundError: No module named 'semantic_version'`).

| Command (from `node_rfid_finger_print/`) | Result |
|---|---|
| `~/.platformio/penv/bin/pio run` | **SUCCESS** — Took 4.11 s. `main.c` recompiled (board_pins.h changed). RAM 22.5% (73,832 B), Flash 81.4% (1,706,640 / 2,097,152 B). No warnings (the obsolete GPIO9 `#warning` is gone). |

Flash unchanged from PHASE 00 (81.4%) — this phase changed only a macro value
and comments/preprocessor, adding no code.

## 7. Test commands and results

Host-based CMake/CTest suite (Linux host, not on target):

| Command (from `node_rfid_finger_print/test/`) | Result |
|---|---|
| `rm -rf build && cmake -B build -S .` | configure OK |
| `cmake --build build` | **BUILD SUCCESS** — `access_security_tests` links clean |
| `ctest --test-dir build --output-on-failure` | **PASS — 100% (1/1)**, `access_security_tests`, 0.00 s |

`access_security_tests` runs 6 assertions incl. `test_unknown_card_never_unlocks`,
`test_valid_credential_unlocks`, `test_missing_hardware_never_unlocks`,
`test_malformed_uart_frame_is_rejected`, `test_stale_uart_frame_is_rejected`,
`test_rc522_uid_validation` — all passed. (Unlike the environment node, this
node has no pre-existing failing test.)

## 8. Git diff/status summary

Branch `main`, HEAD `0cf5a93`. Uncommitted state after PHASE 03 (nothing
committed):

RFID node:
- `?? node_rfid_finger_print/include/board_pins.h` (untracked) — RST target
  9→14, `#warning` removed, comments updated (this phase's only source edit).
- `M node_rfid_finger_print/src/main.c` — unchanged this phase; still shows the
  prior-work diff (+15/−28: local `#define`s removed, `board_pins.h` consumed,
  TZM crossover documented).

Untouched carry-over from earlier phases (not part of PHASE 03): the
environment node's PHASE 01/02 diffs (`mq7.*`, `mq7_cycle.*`, `board_pins.h`,
`display_st7789.*`, `main.c`, `CMakeLists.txt`) and the `docs/` additions,
including this report.

## 9. Known issues and TODOs

1. **TODO_HW_CONFIRM (RC522 RST GPIO14)**: the mfrc522 driver soft-resets over
   SPI and has no RST pin parameter, so firmware does not drive GPIO14. Confirm
   on the bench that the physical RC522 module comes up reliably with RST tied
   high (module default / external pull-up) and does not require a firmware-
   driven hardware reset pulse. If a hardware reset is ever required, that is
   net-new driver work (add an RST GPIO parameter to `mfrc522_init` and pulse
   it) — out of scope for a pin-correction phase; **do not fake it**.
2. **TODO_HW_CONFIRM (TZM1026 TOUCH_OUT GPIO15)**: reserved and unused; finger
   presence is detected over UART. Only wire/implement it if the hardware
   actually needs a wake line, with proper debounce.
3. **TODO_HW_CONFIRM (MQ-7 AO 5 V)** and the **stale env provisioning-parser
   host test** are environment-node items (PHASE 00/01) — not in this node's
   scope; listed only for continuity.
4. `pio` on PATH is broken; use `~/.platformio/penv/bin/pio`.
5. Flash usage is 81.4% on this node before the PHASE 04 OLED driver is added —
   monitor partition headroom when adding SSD1306 (esp_lcd + font/render code).

## 10. Hardware verification status

**NOT hardware-verified.** No firmware was flashed to a physical board in this
phase; no RC522, TZM1026, relay, or OLED was attached or exercised. All results
above are host build / host test results only. The RST-behavior, UART crossover,
relay pulse, and reader wiring remain unverified on the bench (see §9 TODOs).

## 11. Exact instructions for the next phase

Next phase: **PHASE 04 — SSD1306 OLED display for the RFID/fingerprint node**
(`docs/hardware_gpio_display_phases/PHASE_04_SSD1306_OLED_DISPLAY.md`).

- Read PHASE 00/01/02/03 completion reports, then inspect code and git diff
  before editing. Modify **only** `node_rfid_finger_print`.
- OLED target pins are already reserved in `board_pins.h`:
  `BOARD_TARGET_OLED_SDA_GPIO = 8`, `BOARD_TARGET_OLED_SCL_GPIO = 9` (I2C).
  Promote these to active `BOARD_*` config when adding the driver; GPIO8/9 are
  free of conflicts now that RC522 RST moved to GPIO14.
- No SSD1306 driver exists in this node yet. The installed ESP-IDF 5.3.2 ships
  a real `esp_lcd_panel_ssd1306.c` (see PHASE 00 §4.3 and the env node's PHASE
  02 esp_lcd usage as a working reference) — reuse the real component; do not
  invent a driver or introduce Arduino/TFT_eSPI.
- Keep the display strictly non-blocking and fail-safe: an OLED init/render
  failure must never crash or degrade the access-control path or the relay
  fail-closed behavior. Show only real state (reader readiness, last access
  result, Wi-Fi status) — no fake/simulated values.
- Preserve everything verified here: fail-closed authorization, no auto-grant,
  relay GPIO4, RC522 SPI pins, TZM1026 UART orientation (RX17/TX18 @57600), the
  RST soft-reset-only status, and the enroll-button flow.
- Build with `~/.platformio/penv/bin/pio run`; run host tests from `test/` with
  a CLEAN build dir (`rm -rf build`). Watch flash headroom (81.4% used now).
- Write `PHASE_04_COMPLETION_REPORT.md` in this directory, then STOP.

Stop after PHASE 03. PHASE 04 has NOT been started.
