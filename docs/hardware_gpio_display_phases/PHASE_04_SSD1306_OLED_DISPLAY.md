# PHASE 04 — SSD1306 OLED Status Display

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
- `PHASE_03_COMPLETION_REPORT.md`

Then inspect the current code and Git diff.

## Scope

Implement a real SSD1306 0.96-inch I2C OLED status display for:

`/home/huynn/smart_home/node_rfid_finger_print`

## OLED pin map

- SDA -> GPIO8
- SCL -> GPIO9

## Driver rules

- Determine the actual installed ESP-IDF version.
- Reuse an existing compatible driver if present.
- Otherwise add a verified ESP-IDF-compatible SSD1306 driver.
- Do not add Arduino-only libraries.
- Do not fabricate dependency names or versions.
- Update `platformio.ini` only if a real external dependency is actually required.
- Update CMake dependencies only where required.

## I2C requirements

- Initialize I2C only once.
- Reuse/share the existing I2C bus correctly if another device already uses it.
- Prevent double initialization.
- Handle initialization and communication errors.
- OLED failure must not crash the access-control path.
- Avoid blocking the access-control path.

## Display states

Display only real runtime states:

- boot/init
- waiting for card/fingerprint
- RFID detected
- fingerprint detected
- authorized
- denied
- enrollment mode, only if the current firmware actually supports it
- sensor/communication error
- lock/unlock state if available from real runtime state

Do not fake states.

Preserve secure fail-closed access behavior.

## Validation

Build the RFID/fingerprint firmware.

Run relevant existing tests.

Inspect the final Git diff.

## Completion report

Write:

`/home/huynn/smart_home/docs/agent_handoff/hardware_gpio_display/PHASE_04_COMPLETION_REPORT.md`

Include:
- exact SSD1306 driver/dependency used,
- reason it is valid for this ESP-IDF project,
- files changed,
- final I2C configuration,
- OLED state machine/update strategy,
- build/test results,
- remaining TODOs,
- exact scope for PHASE 05.

Stop after completing PHASE 04. Do not start PHASE 05.
