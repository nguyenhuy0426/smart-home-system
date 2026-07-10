# PHASE 02 — Environment ST7789 Display

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

Read:

- `/home/huynn/smart_home/docs/agent_handoff/hardware_gpio_display/PHASE_00_COMPLETION_REPORT.md`
- `/home/huynn/smart_home/docs/agent_handoff/hardware_gpio_display/PHASE_01_COMPLETION_REPORT.md`

Then inspect the actual current source code and Git diff.

## Scope

Add real ST7789 1.3-inch 240x240 SPI display support to:

`/home/huynn/smart_home/node_sensor_enviroment`

Do not modify the RFID/fingerprint node in this phase.

## Display pin map

- CS -> GPIO10 only if physically present on the actual module
- MOSI / SDA -> GPIO11
- SCLK / SCL -> GPIO12
- DC -> GPIO13
- RST / RES -> GPIO14
- BL / BLK -> GPIO15

If the actual module does not expose CS:
- use the correct no-CS handling supported by the real driver/API,
- do not invent a fake CS GPIO.

## Driver rules

- Detect the installed ESP-IDF version.
- Prefer native ESP-IDF `esp_lcd` ST7789 support where supported.
- Do not add TFT_eSPI.
- Do not switch the project to Arduino.
- Update `CMakeLists.txt` `REQUIRES` only where actually needed.
- Add external dependencies only when a real, verified, ESP-IDF-compatible dependency is actually required.
- Do not fabricate library names or versions.

## Runtime requirements

- Initialize the SPI bus only once.
- Prevent double initialization.
- Handle display init failure and update failure.
- Display failure must not crash the core sensor pipeline.
- Avoid blocking sensor acquisition unnecessarily.
- Avoid excessive refresh rate and log spam.
- Create a small display abstraction/module only if the project has no existing equivalent.
- Do not redesign unrelated architecture.

## Display content

Display only real runtime information already available from the actual pipeline:

- temperature
- humidity
- MQ7/CO reading or raw/converted value that the existing system genuinely provides
- BME680 values actually available
- PM2.5/dust reading actually available
- sensor unavailable/error status

Do not invent fake numbers.

If a value does not exist in the current pipeline, show an unavailable/error state instead of fake data.

## Validation

Build the environment firmware.

Run relevant existing tests.

Inspect the final Git diff.

## Completion report

Write:

`/home/huynn/smart_home/docs/agent_handoff/hardware_gpio_display/PHASE_02_COMPLETION_REPORT.md`

Include:
- display API/driver used,
- why it is compatible with the installed ESP-IDF version,
- dependencies changed,
- files changed,
- final display pin map,
- runtime update strategy,
- build/test results,
- remaining hardware TODOs,
- exact scope for PHASE 03.

Stop after completing PHASE 02. Do not start PHASE 03.
