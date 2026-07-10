# PHASE 00 — Audit and Current State

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


## Target hardware

Both nodes use **ESP32-S3 DevKitC-1**.

Do not assume ESP32 classic pin capabilities. Validate all GPIO, ADC, USB, strapping, flash/PSRAM, UART, SPI, and I2C usage against ESP32-S3 and the actual board/project configuration.

## Goals

- Inspect both complete projects.
- Inspect existing `board_pins.h`, `main.c`, sensor drivers, display code, `platformio.ini`, `CMakeLists.txt`, tests, and current Git diff.
- Determine exactly what previous Claude sessions already changed.
- Verify whether previous pin changes are correct or incomplete.
- Identify all remaining conflicts, obsolete code, missing display drivers, build failures, and existing unrelated test failures.
- Do not implement functional changes unless absolutely required to complete the audit.

## Confirmed target pin map — environment node

Project:

`/home/huynn/smart_home/node_sensor_enviroment`

MQ-7:
- Module has only VCC, GND, AO.
- No externally controlled heater GPIO.
- VCC -> 5V
- GND -> GND
- AO -> GPIO1 / ADC1_CH0

DHT22:
- DATA -> GPIO4

CJMCU-680 / BME680:
- SDA -> GPIO8
- SCL -> GPIO9

GP2Y1014 / GP2Y1010:
- Analog output -> GPIO2 / ADC1_CH1
- LED control -> GPIO6

ST7789 1.3-inch 240x240 SPI:
- CS -> GPIO10 only if the physical module exposes CS
- MOSI / SDA -> GPIO11
- SCLK / SCL -> GPIO12
- DC -> GPIO13
- RST / RES -> GPIO14
- BL / BLK -> GPIO15

If the actual ST7789 module does not expose CS, use the correct no-CS handling supported by the real driver/API. Do not invent a fake CS GPIO.

## Confirmed target pin map — RFID/fingerprint node

Project:

`/home/huynn/smart_home/node_rfid_finger_print`

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
- V_TOUCH -> 3V3
- TOUCH_OUT -> GPIO15, optional
- VCC -> 3V3
- Fingerprint TX -> ESP32 RX GPIO17
- Fingerprint RX -> ESP32 TX GPIO18
- GND -> GND

Relay/door lock:
- Preserve the existing working relay GPIO unless a verified conflict exists.

## Completion report

Write:

`/home/huynn/smart_home/docs/agent_handoff/hardware_gpio_display/PHASE_00_COMPLETION_REPORT.md`

The report must include:

1. Files currently modified before this phase.
2. Existing completed work.
3. Existing incomplete work.
4. Existing incorrect assumptions.
5. Current actual pin map from source code.
6. Target final pin map.
7. Differences between current and target state.
8. Existing build status.
9. Existing test status.
10. Exact scope that PHASE 01 should continue with.

Stop after completing PHASE 00. Do not start PHASE 01.
