# PHASE 03 — RFID and Fingerprint GPIO Correction

# Common Rules

You are a senior software engineer specialized in ESP-IDF, IoT firmware, embedded systems, AOSP, and Edge AI.

Absolute principles:

- Use only real APIs, drivers, libraries, functions, syntax, GPIO capabilities, protocol behavior, and build systems that actually exist for the target platform and versions in this repository.
- Never invent ESP-IDF APIs, Arduino APIs, GPIO capabilities, SPI/I2C/UART behavior, display drivers, sensor functions, or protocol semantics.
- Preserve the existing codebase architecture, interfaces, drivers, global state, error handling, tests, and build system.
- Do not introduce the Arduino framework into an ESP-IDF project.
- Do not add fake sensor values, fake authentication states, simulated display values, or unsafe auto-grant behavior.
- Never hardcode secrets, Wi-Fi credentials, API keys, passwords, or certificates.
- If something cannot be verified from the actual source code, target hardware, installed ESP-IDF version, official documentation, or an existing dependency, explicitly say: **"I am not sure"** and add a TODO requiring confirmation instead of guessing.

## Critical continuation rule — do not redo completed work

Before doing any implementation work:

1. Read all previous completion reports under:
   `/home/huynn/smart_home/docs/agent_handoff/hardware_gpio_display/`
2. Inspect the current source code.
3. Inspect `git status`, `git diff`, and relevant recent changes.
4. Inspect existing:
   - `board_pins.h`
   - `main.c`
   - sensor drivers
   - display drivers
   - `platformio.ini`
   - `CMakeLists.txt`
   - `sdkconfig` / `sdkconfig.defaults`
   - tests
5. Determine exactly what is already complete.
6. Do not redo, recreate, duplicate, revert, or unnecessarily refactor completed work.
7. Trust previous reports only after verifying the actual current code state.
8. Continue from the exact current state.

If a task requested by this phase is already correctly completed:
- verify it,
- document it as already complete,
- do not implement it again.

## Project paths

Environment sensor node:

`/home/huynn/smart_home/node_sensor_enviroment`

RFID + fingerprint access node:

`/home/huynn/smart_home/node_rfid_finger_print`

Phase prompts:

`/home/huynn/smart_home/prompts/hardware_gpio_display_phases/`

Completion reports:

`/home/huynn/smart_home/docs/agent_handoff/hardware_gpio_display/`

## Mandatory execution rule for every phase

- Read all previous completion reports first.
- Inspect the current code and Git diff before editing.
- Do not redo completed work.
- Make only changes belonging to the current phase.
- Do not start the next phase.
- Build and test affected projects after changes.
- Write a completion report after finishing.
- The next phase must read that report before continuing.

## Mandatory completion report format

Every completion report must contain:

# Phase XX Completion Report

## 1. Scope completed

## 2. Previous completed work preserved

## 3. Files changed

## 4. Technical changes

## 5. Pin map after this phase

## 6. Build commands and results

## 7. Test commands and results

## 8. Git diff/status summary

## 9. Known issues and TODOs

## 10. Hardware verification status

## 11. Exact instructions for the next phase

Never say a task passed unless the corresponding build/test command was actually run successfully.

Never say hardware was verified unless firmware was actually flashed to and tested on physical hardware.


## Before starting

Read all previous completion reports:

- `PHASE_00_COMPLETION_REPORT.md`
- `PHASE_01_COMPLETION_REPORT.md`
- `PHASE_02_COMPLETION_REPORT.md`

Then inspect the current code and Git diff.

## Scope

Modify only:

`/home/huynn/smart_home/node_rfid_finger_print`

Do not implement OLED display functionality yet, except pin/dependency preparation strictly required for PHASE 04.

## Confirmed pin map

OLED SSD1306:
- SDA -> GPIO8
- SCL -> GPIO9

RC522:
- CS / SDA / SS -> GPIO10
- MOSI -> GPIO11
- SCK -> GPIO12
- MISO -> GPIO13
- RST -> GPIO14
- IRQ -> not connected

TZM1026:
- TOUCH_OUT -> GPIO15, optional
- ESP32 RX -> GPIO17, connected to fingerprint TX
- ESP32 TX -> GPIO18, connected to fingerprint RX

Relay:
- Preserve the existing working relay GPIO unless a real verified conflict exists.

## Requirements

- Remove the obsolete GPIO9 RC522 RST conflict.
- Preserve existing secure access-control logic.
- Do not introduce auto-grant behavior.
- Preserve fail-closed behavior.
- Verify whether the actual RC522 driver supports hardware RST.
- Do not invent an RST API.
- If the current RC522 driver has no hardware-reset support:
  - document this honestly,
  - do not fake it.
- Keep the corrected TZM1026 UART orientation:
  - ESP32 RX = GPIO17
  - ESP32 TX = GPIO18
- Keep the existing baud rate unless verified evidence requires changing it.
- Do not add unnecessary `TOUCH_OUT` interrupt/debounce code unless this signal is actually used.
- If TOUCH_OUT is implemented, handle noise/debounce appropriately.
- Preserve relay configuration unless there is an actual conflict.

## Validation

Build the RFID/fingerprint firmware.

Run all relevant existing tests.

Inspect the final Git diff.

## Completion report

Write:

`/home/huynn/smart_home/docs/agent_handoff/hardware_gpio_display/PHASE_03_COMPLETION_REPORT.md`

Include:
- files changed,
- final pin map,
- RC522 RST implementation status,
- TZM1026 UART verification,
- security behavior verification,
- build/test results,
- remaining scope for PHASE 04.

Stop after completing PHASE 03. Do not start PHASE 04.
