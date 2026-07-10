# PHASE 01 — Environment GPIO and MQ-7 Driver Correction

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

`/home/huynn/smart_home/docs/agent_handoff/hardware_gpio_display/PHASE_00_COMPLETION_REPORT.md`

Then verify the current source code and Git diff again.

## Scope

Modify only:

`/home/huynn/smart_home/node_sensor_enviroment`

Do not implement ST7789 display support yet.

## Confirmed environment-node pin map

- MQ7 AO -> GPIO1 / ADC1_CH0
- GP2Y1014 analog -> GPIO2 / ADC1_CH1
- DHT22 DATA -> GPIO4
- GP2Y1014 LED -> GPIO6
- BME680 SDA -> GPIO8
- BME680 SCL -> GPIO9

## MQ-7 correction

The MQ-7 module has only:
- VCC
- GND
- AO

There is no external heater-control GPIO.

Therefore:

- Completely remove obsolete external heater-control GPIO logic if it still exists.
- Remove definitions such as:
  - `MQ7_HEATER_CONTROL_PIN`
  - `BOARD_MQ7_HEATER_GPIO`
- Remove heater GPIO initialization and runtime `gpio_set_level()` control.
- If `mq7_init()` currently requires a heater GPIO solely because of the old architecture, minimally refactor the real API and implementation.
- Preserve valid ADC sampling, calibration, filtering, conversion, timing, timeout, validation, and error handling.
- Do not pass a fake `GPIO_NUM_NC` if the driver still attempts to control a heater.

Electrical safety note:
- MQ-7 VCC is 5V.
- Do not assume AO is automatically safe for the ESP32-S3 ADC input.
- Document that the real AO voltage must be measured/verified.
- If required, use an external voltage divider.
- Do not invent an exact AO voltage range without the exact breakout-board datasheet.

## Other requirements

- Centralize pin mappings using the existing `board_pins.h` architecture.
- Verify no GPIO conflict remains.
- Verify GPIO1 and GPIO2 are valid ADC1 pins on ESP32-S3.
- Remove obsolete GPIO35 analog usage.
- Preserve existing architecture and tests.
- Do not refactor unrelated modules.

## Validation

Build the environment firmware using the existing PlatformIO setup.

Run relevant existing tests.

If an unrelated pre-existing test fails:
- reproduce it,
- document it,
- prove whether files changed by this phase are involved,
- do not silently fix unrelated modules.

## Completion report

Write:

`/home/huynn/smart_home/docs/agent_handoff/hardware_gpio_display/PHASE_01_COMPLETION_REPORT.md`

Include:
- files changed,
- exact MQ7 heater code removed,
- final environment sensor pin map,
- build command/result,
- test command/result,
- existing unrelated failures,
- exact remaining scope for PHASE 02.

Stop after completing PHASE 01. Do not start PHASE 02.
