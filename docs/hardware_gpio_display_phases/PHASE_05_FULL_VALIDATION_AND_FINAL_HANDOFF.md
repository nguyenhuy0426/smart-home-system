# PHASE 05 — Full Validation and Final Handoff

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

Read all reports from PHASE 00 through PHASE 04.

Inspect the actual current code and Git diff.

Do not redo implementation from previous phases.

## Final expected mapping — environment node

- MQ7 AO -> GPIO1 / ADC1_CH0
- GP2Y analog -> GPIO2 / ADC1_CH1
- DHT22 DATA -> GPIO4
- GP2Y LED -> GPIO6
- BME680 SDA -> GPIO8
- BME680 SCL -> GPIO9
- ST7789 CS -> GPIO10 if present
- ST7789 MOSI -> GPIO11
- ST7789 SCLK -> GPIO12
- ST7789 DC -> GPIO13
- ST7789 RST -> GPIO14
- ST7789 BL -> GPIO15

## Final expected mapping — RFID/fingerprint node

- Relay -> preserve existing valid pin, expected GPIO4 if unchanged
- OLED SDA -> GPIO8
- OLED SCL -> GPIO9
- RC522 CS -> GPIO10
- RC522 MOSI -> GPIO11
- RC522 SCK -> GPIO12
- RC522 MISO -> GPIO13
- RC522 RST -> GPIO14 if supported by the real driver
- TZM1026 TOUCH_OUT -> GPIO15 optional
- TZM1026 ESP32 RX -> GPIO17
- TZM1026 ESP32 TX -> GPIO18

## Validation requirements

1. Search for stale or duplicated pin constants.
2. Search for obsolete MQ7 heater code.
3. Search for GPIO35 analog usage.
4. Search for the old GPIO8 MQ7 conflict.
5. Search for the old GPIO9 OLED/RC522 conflict.
6. Verify no duplicate SPI/I2C/UART initialization.
7. Verify no fake values or fake status states.
8. Verify no Arduino framework/library was introduced.
9. Verify no hardcoded secrets were added.
10. Verify display failure does not crash core sensor/access functionality.
11. Verify access control remains fail-closed.
12. Run both firmware builds.
13. Run all existing relevant tests.
14. Inspect the final `git diff`.
15. Clearly distinguish:
    - build-verified,
    - test-verified,
    - statically verified,
    - not hardware-verified.

Do not claim real hardware success unless actual devices were flashed and tested.

## Final report

Write:

`/home/huynn/smart_home/docs/agent_handoff/hardware_gpio_display/PHASE_05_FINAL_REPORT.md`

The final report must include:

1. Executive summary.
2. All files changed across all phases.
3. Final pin map for both nodes.
4. Obsolete code removed.
5. Drivers/dependencies added.
6. Build commands and exact results.
7. Test commands and exact results.
8. Existing unrelated failures, if any.
9. Security verification.
10. Hardware electrical warnings.
11. Remaining TODOs.
12. Exact flash/monitor commands the user should run next.
13. Explicit statement of what was and was not verified on physical hardware.

Stop after completing PHASE 05.
