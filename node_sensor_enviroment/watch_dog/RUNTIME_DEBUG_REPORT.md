# Runtime Debug Report

Persistent evidence log for the ESP32-S3 environmental sensor node. Runtime
claims in this file require captured serial output; build success alone is not
treated as hardware verification.

## 2026-07-14T20:25:43+07:00 — Baseline audit

- Firmware/git state: repository `main` at `7d32b8c`; pre-existing uncommitted
  changes are present in this node and are preserved. Sibling-node changes are
  explicitly out of scope.
- Toolchain: PlatformIO `espressif32 53.3.11`, ESP-IDF `5.3.2`, esptool `4.8.5`.
- Board/runtime configuration: ESP32-S3 dual-core, 160 MHz, FreeRTOS 100 Hz;
  task watchdog enabled with a 5 s timeout and both idle tasks watched.
- Previous wiring at this timestamp (superseded later): MQ7 AO on the former
  ADC input and DHT22 on the former digital input; BME680
  I2C SDA GPIO8/SCL GPIO9; ST7789 MOSI GPIO11/SCLK GPIO12/RST GPIO14/DC
  GPIO13/BL GPIO15/no CS; GP2Y absent and disabled.
- Build/upload result: not run yet for this audit entry.
- Serial evidence: `/dev/ttyACM0` and `/dev/serial/by-id/` were not visible in
  the sandbox during the baseline check.
- Symptom: hardware state not yet established in this run.
- Verified root cause: none yet. Static audit found duplicate unreachable MQ7
  code after an unconditional return, and a blocking reconnect delay inside
  the shared Wi-Fi event-loop callback; neither is yet tied to a hardware
  symptom.
- Attempted fix: none; baseline captured before edits.
- Result after retest: pending first build/upload/serial cycle.
- Remaining issue: obtain a clean build, verify upload target `0x00020000`,
  access the real serial device, and collect sensor/display boot evidence.

## 2026-07-14T20:31:00+07:00 — Baseline build and device discovery

- Firmware/git state: same `7d32b8c` baseline plus the pre-existing target
  changes and this report.
- Build result: `pio run` succeeded under ESP-IDF 5.3.2. RAM usage 69,184 /
  327,680 bytes (21.1%); application image 1,729,560 / 2,097,152 bytes
  (82.5%).
- Upload-offset evidence: generated `flasher_args.json` maps
  `smarthome_node_sensor.bin` to `0x20000`; no `0x210000` application entry
  exists.
- Serial device: stable path
  `/dev/serial/by-id/usb-Espressif_USB_JTAG_serial_debug_unit_3C:0F:02:D3:F2:4C-if00`
  resolves to `/dev/ttyACM0`.
- Symptom/root cause/fix/retest: no runtime symptom tested yet; upload and
  serial capture are next.
- Remaining issue: upload the baseline image, reset, and capture boot plus
  multiple sensor cycles.

## Static task/core and BLE Mesh audit baseline

- The only application-created task is `environment_telemetry`, priority 5,
  stack 8192 bytes, pinned to core 1. It performs DHT22, MQ7, BME680, display,
  optional JSON construction, and a potentially 5 s blocking HTTP request in
  sequence, then delays 5 s.
- DHT22 holds a core-local critical section only from bus release through the
  bounded 40-bit frame acquisition (nominally about 4 ms). The 20 ms start-low
  pulse and 2.1 s retry delay occur outside the critical section.
- ST7789 uses a single DMA stripe protected by a binary semaphore and releases
  it from the SPI completion ISR. Each wait is bounded to 1 s.
- BLE Mesh is started only while NVS is ready and the node lacks a valid
  provisioning record. Provisioned boots start Wi-Fi instead, so this firmware
  does not intentionally run BLE Mesh and Wi-Fi concurrently.
- BLE controller/Bluedroid/Mesh initialization and callbacks use ESP-IDF 5.3.2
  APIs. The node composition contains a Configuration Server on element 0, a
  vendor server model on element 0, and a Configuration Client on element 1.
  A valid vendor configuration message is saved to NVS, acknowledged, then
  rebooted after 1.5 s.
- No protocol change is justified from static inspection alone. Runtime BLE
  errors still need serial evidence.

## 2026-07-14T20:36:00+07:00 — Baseline upload and runtime capture

- Firmware/git state: unchanged baseline application image built from the
  current working tree.
- Upload result: success through the stable serial-by-id path. The upload hook
  printed `ESP32_APP_OFFSET 0x210000 -> 0x20000`, and serial boot evidence
  reported `Loaded app from partition at offset 0x20000`.
- Reset-loop result: no reset loop observed across more than 98 seconds of
  continuous monitoring; sensor cycles continued at bounded intervals.
- BME680 serial evidence: repeated valid samples around 31.7 degC, 70.5-70.8%
  RH, 1008.5-1008.6 hPa, and 1.30-1.35 Mohm gas resistance. This verifies the
  real I2C acquisition path and compensated runtime data.
- DHT22 serial evidence: every read ended at `phase=response-start` with
  `idle_level=1`, `release_rise=1-2us`, and `resp_start=-1us`. The ESP32
  successfully drove/released the former DHT22 GPIO and the line had a working pull-up, but
  no sensor response-low pulse occurs within the bounded 120 us window after
  a valid 20 ms start signal.
- DHT22 verified blocker on the previous wiring: electrical/device-side non-response (for
  example missing sensor power/ground/signal continuity or a failed sensor),
  not checksum decoding, bit timing, task starvation, or a stuck-low bus.
  Physical wiring/power inspection is required before software can receive a
  frame.
- MQ7 serial evidence on the previous wiring: the former ADC channel repeatedly reported raw average 0 or 1 and
  calibrated 0 mV. Raw acquisition runs during both `high_heat` and
  `low_heat` metadata phases, proving the metadata no longer gates ADC reads.
- MQ7 verified result/blocker: the real ADC input is at or extremely near
  ground. No calibrated CO ppm is emitted (`nan`, status `out_of_range`).
  Physical AO voltage, common ground, module power, and the required ADC-safe
  divider must be checked before calibration or ppm conversion is possible.
- BLE/Wi-Fi evidence: this capture showed no reset or starvation from the
  network path. Complete early-boot BLE initialization messages were missed by
  monitor attachment, so BLE runtime status remains unverified.
- Display evidence: application render calls ran in the sampling task, but the
  baseline has no per-render serial marker and physical pixels have not been
  confirmed. UI implementation will add bounded render evidence before asking
  for physical inspection.
- Attempted fix: none in this entry; this is the verified pre-UI runtime
  baseline.
- Remaining issue: improve raw MQ7 trace/status clarity, remove unreachable
  code, add successful BME chip-ID evidence, implement the two-theme truthful
  UI, then rebuild/upload/monitor and request physical screen confirmation.

## 2026-07-14T20:45:02+07:00 — Final firmware and UI runtime verification

- Firmware/git state: `main` base `7d32b8c` with target-only working-tree
  changes. No file in `node_camera` or `node_rfid_finger_print` was edited by
  this run. The pre-existing uncommitted target work remains uncommitted.
- Files changed by this run: `include/ble_mesh_handler.h`,
  `include/display_st7789.h`, `src/adc_reader.c`, `src/ble_mesh_handler.c`,
  `src/bme680_i2c.c`, `src/display_st7789.c`, `src/main.c`, `src/mq7.c`, and
  this report. Pre-existing target edits in pin/status/config/DHT files and the
  upload-offset script were preserved.
- Final build: success with ESP-IDF 5.3.2. RAM 69,032 / 327,680 bytes (21.1%);
  application image 1,732,516 / 2,097,152 bytes (82.6%). `git diff --check`
  passed.
- Host tests: CMake build succeeded with `-Wall -Wextra -Werror -pedantic`;
  `environment_safety_tests` passed 1/1.
- Final upload: success through the stable serial-by-id path. The hook again
  printed `ESP32_APP_OFFSET 0x210000 -> 0x20000`. One preceding upload attempt
  failed before flashing because the serial monitor still held the exclusive
  port lock; closing that verified owner resolved the failure without a code
  change.
- Reset/watchdog result: no reset loop, task-watchdog message, panic, or
  starvation was observed. Earlier continuous capture exceeded 137 seconds;
  the final image then produced repeated bounded sensor and render cycles.

### Task/core result

- `environment_telemetry` remains the only application-created task: priority
  5, 8192-byte stack, pinned to core 1. BLE controller/Mesh work remains on the
  radio side/core 0; final runtime sampling continued while Mesh reported
  ready.
- DHT22 disables interrupts only for its bounded response/frame window. The
  20 ms host start pulse and 2.1 s retry delay are outside the critical
  section. Final failures consistently happened before bit capture, so task
  preemption is not the active cause.
- MQ7 acquisition busy-waits for about 30 ms total (16 samples spaced 2 ms),
  far below the 5 s task-watchdog threshold. BME680 long delays use
  `vTaskDelay`. The display uses one 11,520-byte DMA band with a semaphore and
  1 s bounded waits; observed ten-band renders completed in about 80-90 ms.
- Residual provisioned-only risks found by inspection: HTTP POST still runs in
  the sampling task with a 5 s timeout, so an unreachable gateway can lengthen
  the sample period; the Wi-Fi disconnect event callback directly delays the
  shared event-loop task during exponential backoff. Neither path runs on this
  unprovisioned local/Mesh boot, and neither caused the measured sensor issue.
  They were not refactored without a verified runtime network failure.

### BLE Mesh/gateway result

- Provisioning state keeps BLE Mesh and Wi-Fi mutually exclusive: unprovisioned
  boots run local sensing plus Mesh; provisioned boots run local sensing plus
  Wi-Fi. There is no intentional simultaneous Mesh/Wi-Fi path in this firmware.
- Composition and vendor provisioning protocol were preserved. The final code
  now checks `esp_ble_mesh_node_prov_enable()` and publishes an atomic
  `ble_mesh_handler_is_ready()` state instead of inferring connectivity.
- Final serial evidence repeatedly showed `BLE[ready=yes]`; render evidence
  showed `network=BLE READY`. No BLE Mesh error, reset, or sensor starvation
  appeared. Gateway interaction was not exercised because the node remained
  unprovisioned; no speculative protocol change was made.

### Final sensor result

- MQ7: the full trace now logs sample count, first/min/max raw counts, averaged
  raw count, calibrated millivolts, ADC status, calibration presence, and
  conversion status. Final examples were `raw_first=0 raw_min=0 raw_max=9
  raw_avg=1 calibrated=0 mV`; earlier captures occasionally averaged 2-9
  counts and produced 1-7 mV. Raw acquisition continued in both metadata
  phases. Calibration is missing, so CO remains `nan` and no ppm is shown.
  The verified electrical blocker is AO at/near ground; check module power,
  common ground, AO continuity/voltage, and ADC-safe divider before calibration.
- DHT22: final failures remained `response-start`, `idle_level=1`,
  `release_rise=1us`, `resp_start=-1us`. The MCU drive/release and pull-up are
  working, but the sensor never asserted the response-low pulse on the former wiring. Check signal
  signal continuity, VCC/GND, pull-up placement, and the physical sensor.
- BME680: final real samples were about 32.0 degC, 70.6-70.7% RH, 1008.8 hPa,
  with valid gas resistance around 1.30-1.48 Mohm. The init path now logs the
  detected address, chip ID, and Bosch API result; runtime values remained
  valid throughout the final capture.
- GP2Y1014: remains physically absent and disabled; no GPIO6 drive or floating
  ADC dust acquisition was added.

### Final ST7789 UI result

- Layout: thin node/network header; large primary temperature; paired humidity
  and pressure section; paired BME gas and MQ7 raw/CO section; compact truthful
  DHT/BME/MQ7/network status footer.
- Truthful fallback: because DHT22 is unavailable, the large primary value is
  explicitly labeled `BME680 TEMP`. MQ7 displays raw mV plus `RANGE`/`UNCAL`,
  never ppm without calibration. BLE is shown only when the real ready flag is
  true.
- Themes: exact black background/white text and white background/black text,
  with theme-appropriate muted/divider/status colors. Physical review mode
  alternates DARK/LIGHT every 30 seconds so both can be inspected without an
  invented button or GPIO.
- Render evidence: final serial lines included `render=2 theme=DARK
  primary=32.0C source=BME680 TEMP ... network=BLE READY` and `render=5
  theme=LIGHT primary=32.0C source=BME680 TEMP ... network=BLE READY`.
- Physically verified: firmware upload, boot/runtime continuity, real sensor
  values/statuses entering both render paths, and successful DMA render API
  completion (no draw/semaphore errors).
- Still unverified: the actual visible panel pixels, rotation, color inversion,
  backlight polarity, and visual spacing cannot be confirmed from serial. The
  reference attachment directory contained only `goal-objective.md`, not an
  image, so the textual hierarchy requirements were used. User physical screen
  inspection is required before any claim of display success or visual tuning.

## 2026-07-14T21:34:02+07:00 — GPIO2/GPIO5 rewiring baseline

- Source/git state: continued from the exact dirty `main` working tree at base
  `7d32b8c`; completed local-first, BME680, display, BLE-ready, and upload-offset
  work is preserved. Sibling-node changes remain out of scope.
- New physical source of truth: MQ7 AO GPIO2/ADC1_CH1; DHT22 OUT GPIO5; BME680
  SDA GPIO8/SCL GPIO9; ST7789 GPIO11/12/13/14/15 with no CS; GP2Y absent.
- Previous comparison: the former MQ7 ADC input produced mostly 0 mV with
  occasional 1-7 mV. The former DHT22 input rose normally but never received a
  response-low pulse.
- Symptom/hypothesis: runtime behavior on the newly wired GPIO2/GPIO5 paths is
  not yet measured. The pin map still targeted the former pins and GP2Y still
  reserved ADC1_CH1, so the firmware could not test the new wiring correctly.
- Fix prepared: move MQ7 to GPIO2/ADC1_CH1, move DHT22 to GPIO5, remove the
  GP2Y ADC assignment and all GP2Y initialization/sample branches, add explicit
  mapping validation/logs, and make a real zero-mV uncalibrated MQ7 reading
  report `calibration_missing` rather than a calibrated range conclusion.
- Build result: host safety tests passed 1/1 and ESP-IDF 5.3.2 firmware build
  succeeded. RAM 69,032 / 327,680 bytes (21.1%); application 1,733,012 /
  2,097,152 bytes (82.6%). Upload/runtime remain pending.
- Active board port: `/dev/ttyACM0`; stable by-id path remains
  `/dev/serial/by-id/usb-Espressif_USB_JTAG_serial_debug_unit_3C:0F:02:D3:F2:4C-if00`.
- Remaining issue: build, upload at 0x20000, capture multiple GPIO2/GPIO5
  cycles, compare against the former wiring, and prove either valid readings
  or specific electrical blockers without disturbing verified subsystems.

## 2026-07-14T21:39:00+07:00 — GPIO2/GPIO5 post-fix hardware result

- Source/git state: continued from the same dirty `main` working tree at base
  `7d32b8c`. This rewiring pass changed only target-node files:
  `include/board_pins.h`, `src/main.c`, `src/dht22.c`, `src/mq7.c`,
  `src/mq7_conversion.c`, `test/test_environment_safety.c`,
  `docs/SENSOR_CALIBRATION.md`, `docs/README.md`,
  `config/capability_descriptor.env.jsonc`,
  `src/environment_capability_descriptor.c`, and this report. No sibling node
  was modified.
- Symptom: the previous firmware pin map still targeted the superseded MQ7 and
  DHT22 inputs, while absent GP2Y also reserved ADC1_CH1. That prevented a
  truthful test of the new physical wiring and risked two logical owners of
  the same ADC channel.
- Hypothesis: assigning GPIO2/ADC1_CH1 exclusively to MQ7 would reveal whether
  its AO is electrically present; assigning GPIO5 to DHT22 would determine
  whether the previous missing response-low was tied to the old input pin.
- Fix: MQ7 now exclusively owns GPIO2/ADC1_CH1 and GP2Y has no ADC assignment
  or initialization/sample path. DHT22 now uses GPIO5. Compile-time and runtime
  pin/channel checks, boot/sample logs, capability descriptions, tests, and
  documentation were updated. MQ7 sampling remains independent of heater-phase
  metadata; no PWM/LEDC behavior was invented for the actual VCC/GND/AO module.
- Build/test result: host tests passed 1/1 with
  `-Wall -Wextra -Werror -pedantic`. PlatformIO/ESP-IDF 5.3.2 build succeeded:
  RAM 69,032 / 327,680 bytes (21.1%), application 1,733,012 / 2,097,152 bytes
  (82.6%). The final image mapping contains the application only at `0x20000`.
- Upload result: success through
  `/dev/serial/by-id/usb-Espressif_USB_JTAG_serial_debug_unit_3C:0F:02:D3:F2:4C-if00`.
  The preserved upload hook printed
  `ESP32_APP_OFFSET 0x210000 -> 0x20000 (factory app partition)`.

### Post-fix runtime evidence

- MQ7 GPIO2/ADC1_CH1: the first captured cycles reported averaged raw counts
  604, 600, 603, and 600 with calibrated ADC voltages 527, 524, 526, and
  524 mV. Across more than 137 seconds, observed averages were approximately
  580-606 counts and 506-529 mV. This is a stable, non-zero real AO signal and
  is decisively different from the former input's mostly 0 mV and occasional
  1-7 mV. Per-cycle logs include channel, sample count, first/min/max/average
  raw values, voltage, and status.
- MQ7 phase behavior: acquisition continued in both `high_heat` and
  `low_heat`. Example low-phase evidence was `raw_first=615 raw_min=591
  raw_max=655 raw_avg=606 calibrated=529 mV`. Because no valid sensor
  calibration is installed, every conversion truthfully reports
  `calibration_missing` and CO remains `nan`; no ppm was fabricated.
- DHT22 GPIO5: repeated reads still fail at `phase=response-start` with
  `idle_level=1`, `release_rise=1us`, `resp_start=-1us`, `resp_low=-1us`,
  `resp_high=-1us`, and `failed_bit=-1`. The MCU drives/releases the new GPIO
  and the line returns high, but the sensor never asserts its response-low.
  Moving from GPIO4 to GPIO5 therefore did not change the failure signature.
  The specific remaining blocker is electrical/device-side: verify DHT22 VCC,
  common ground, GPIO5 signal continuity/pinout, pull-up placement, and the
  sensor itself. Bounded waits and strict checksum validation remain intact.
- BME680 regression check: repeated valid samples remained around
  31.7-31.8 degC, 69.3-69.5% RH, 1009.8-1009.9 hPa, with approximately
  1.43-1.51 Mohm gas resistance.
- Display/UI: repeated render logs continued in both DARK and LIGHT themes,
  using BME680 as the truthful primary temperature, showing DHT timeout, MQ7
  raw millivolts/uncalibrated state, and `network=BLE READY`. The user's prior
  physical ST7789 confirmation remains the physical-display evidence; this
  serial run proves that current values/statuses continued entering the render
  path without display-driver errors.
- Stability: no reset loop, panic, task-watchdog event, or sampling starvation
  occurred during more than 137 seconds of continuous capture.

### Task/core and BLE/gateway conclusion

- `environment_telemetry` remains priority 5, 8192-byte stack, pinned to core
  1. DHT22's critical section covers only the bounded response/frame timing;
  its 20 ms start pulse and 2.1 s retry delay remain outside it. MQ7's 16
  samples take about 30 ms, BME680's long wait yields with `vTaskDelay`, and
  bounded display renders continued. Runtime evidence shows no scheduling,
  affinity, critical-section, watchdog, or BLE interference, so no scheduling
  change is justified.
- BLE readiness repeatedly reported `BLE[ready=yes]` while local sensing and
  display updates continued. Local-first behavior and the existing mutually
  exclusive unprovisioned-Mesh/provisioned-Wi-Fi design were preserved.
  Gateway interaction was not exercised on this unprovisioned run. Existing
  provisioned-only HTTP/event-loop latency risks remain separately documented;
  no speculative Mesh or gateway protocol change was made.

### Remaining hardware/calibration blockers

- MQ7 now has proven GPIO2 ADC evidence, but trustworthy CO ppm still requires
  a documented sensor calibration and verification that the module AO voltage
  is always within the ESP32-S3 ADC-safe range (use a divider if required).
- DHT22 produces no response-low on GPIO5. Software cannot decode temperature
  or humidity until power, ground, signal continuity/pinout, pull-up, or the
  sensor itself is corrected/replaced.

## 2026-07-14T22:39:34+07:00 — Permanent DHT22 removal and UI/time build

- Source state: continued from the existing dirty `main` tree at base
  `7d32b8c`; sibling-node changes remain untouched. DHT22 is now permanently
  removed from this node rather than diagnosed as a failed installed sensor.
- Runtime/build removal: `dht22_init()` and `dht22_read()` calls, GPIO5 pin
  ownership, DHT sensor values/logs/UI rows, DHT protocol tests, the obsolete
  DHT/BME fusion path, DHT component sources, headers, and the PlatformIO DHT
  library dependency were removed. The deleted files are `src/dht22.c`,
  `src/dht22_protocol.c`, `include/dht22.h`, `include/dht22_protocol.h`,
  `src/environment_sensor_fusion.c`, and
  `include/environment_sensor_fusion.h`.
- Schema compatibility: schema-v1 retains only
  `sensorStatus.dht22="unsupported"`; it emits no DHT value. Stable
  `ambientTemperature` and `relativeHumidity` keys now use real BME680 values
  and explicitly identify `BME680` as their source.
- Final static pin contract: MQ7 GPIO2/ADC1_CH1; BME680 SDA GPIO8/SCL GPIO9;
  ST7789 MOSI GPIO11/SCLK GPIO12/DC GPIO13/RST GPIO14/BL GPIO15/no CS;
  GP2Y disabled with no ADC assignment; GPIO5 unowned.
- UI implementation: the established 240x240 DMA-band layout and exact
  black/white DARK/LIGHT theme contrast are preserved. The header now has
  `HH:MM:SS` plus `DD/MM/YYYY`; sensor sections contain BME680 temperature,
  humidity, pressure and gas resistance plus MQ7 raw mV/CO status; the footer
  contains only BME680, MQ7, local/BLE/Wi-Fi and theme state. DHT fields are
  absent. Coordinates remain within y=0..237 and x=0..232; the longest clock,
  date, period, metric and footer strings fit their allocated regions.
- Time truth: the existing SNTP path is unchanged and starts only for a
  provisioned Wi-Fi boot. `observed_at_epoch_ms=0` renders `--:--:--` and
  `DATE UNSYNCED` with no period icon. A valid synchronized epoch with no
  configured `TZ` renders truthful UTC and no DAY/NIGHT icon. A non-empty
  runtime `TZ` plus successful `localtime_r()` enables the custom sun/DAY icon
  from 06:00 through 17:59 and moon/NIGHT otherwise. No timezone was
  hardcoded or invented.
- Host verification: CMake configured and built the safety suite with
  `-Wall -Wextra -Werror -pedantic`; `ctest` passed 1/1. Tests verify BME680
  is the ambient telemetry source, the DHT compatibility status is
  `unsupported`, and no DHT source value appears.
- Firmware build: PlatformIO `espressif32 53.3.11` / ESP-IDF 5.3.2 succeeded
  from a read-identical `/tmp` mirror because this session cannot write the
  target `.pio` directory. RAM is 69,112 / 327,680 bytes (21.1%); application
  usage is 1,733,652 / 2,097,152 bytes (82.7%); `firmware.bin` is 1,734,032
  bytes. The final build manifest maps `smarthome_node_sensor.bin` to
  `0x20000`; there is no `0x210000` application entry.
- Pending hardware phase: resync the final whitespace-only cleanup into the
  temporary build, upload that image through the stable serial port, capture
  boot and repeated sample/render cycles, and verify BME680, MQ7, BLE/time
  state, display render completion, reset/panic/watchdog absence, and the
  booted application offset. Physical pixel/layout success still requires
  user visual confirmation.

## 2026-07-14T22:42:27+07:00 — Final static image and hardware-access blocker

- Final-source rebuild after footer spacing and whitespace cleanup succeeded.
  RAM remains 69,112 / 327,680 bytes (21.1%); final application usage is
  1,733,656 / 2,097,152 bytes (82.7%); `firmware.bin` is 1,734,032 bytes.
- `git diff --check` passes. The final PlatformIO `compile_commands.json` and
  `build.ninja` contain no DHT driver/protocol/fusion source, and the linked
  ELF contains no `dht22_init`, `dht22_read`, DHT frame-rejection, or DHT GPIO
  initialization symbol/string. Compatibility/documentation strings remain
  intentionally limited to `unsupported`/`removed` truth.
- Upload-offset evidence remains exact: `app-flash_args`,
  `flash_project_args`, and `flasher_args.json` all map
  `smarthome_node_sensor.bin` to `0x20000`; no `0x210000` entry exists.
- Physical phase blocker: this execution sandbox exposes neither
  `/dev/serial/by-id/` nor `/dev/ttyACM0`. The required read-only device
  discovery outside the sandbox was rejected by the environment policy, so
  this session cannot open the USB serial device for upload or monitor.
- Consequently, the new image has not been flashed and no new-image serial
  evidence is claimed for BME680, MQ7, BLE/time render data, boot offset,
  panic/reset/watchdog absence, or visible pixels. The earlier GPIO2/BME680
  measurements in this report remain valid evidence for the preceding image,
  not proof of this new DHT-free image. Resume with the ESP32 USB device
  exposed to the session, then run upload plus a multi-cycle monitor capture.
