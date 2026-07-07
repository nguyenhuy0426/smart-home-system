# Sensor calibration and wiring contract

The firmware has no fallback calibration constants. Missing or malformed values keep the affected
metric invalid; the telemetry envelope reports `calibration_missing` and may include the calibrated
ESP32 ADC voltage as `raw_uncalibrated`.

## NVS namespace `sensor_cal`

All integer values are unsigned unless noted.

| Key | Type | Scale and meaning |
|---|---|---|
| `mq7_r0_mohm` | `u32` | Calibrated MQ7 clean-air resistance R0 in milliohms. |
| `mq7_rl_mohm` | `u32` | Board load resistance RL in milliohms. |
| `mq7_vc_mv` | `u32` | MQ7 sensing-circuit supply in millivolts. |
| `mq7_div_ppm` | `u32` | Ratio from ADC-pin voltage to sensor output voltage, multiplied by 1,000,000. |
| `mq7_a_milli` | `u32` | Board/calibration curve coefficient A, multiplied by 1,000. |
| `mq7_b_milli` | `i32` | Negative curve exponent B, multiplied by 1,000. |
| `gp_zero_mv` | `u32` | GP2Y1014 clean-air sensor-output voltage in millivolts. |
| `gp_sens_uv` | `u32` | GP2Y1014 sensitivity in microvolts per µg/m³. |
| `gp_div_ppm` | `u32` | Ratio from ADC-pin voltage to sensor output voltage, multiplied by 1,000,000. |

Every key for a sensor must be present and valid before its engineering-unit metric is emitted.
Calibration values must come from the assembled board and its actual ADC divider, not a generic
example curve.

## MQ7 heater hardware

GPIO 8 is a 1 kHz control signal for an external MOSFET heater stage powered from a regulated 5 V
rail. Never connect the MQ7 heater directly to an ESP32-S3 GPIO. The firmware applies the MQ7 cycle:
60 seconds at full heater power, then 90 seconds at the low-heater PWM power, and permits sampling
only during the last ten seconds of the low phase. The low duty cycle targets 1.4 V RMS from a 5 V
switched rail. Confirm the resulting heater voltage and temperature on the actual board before any
sensor qualification.

## GP2Y1014 optical timing

GPIO 5 drives the module LED input (active low). Each sample starts the LED pulse, acquires ADC at
280 µs, turns the LED off at 320 µs or immediately after ADC completion, and completes a 10 ms
cycle. Ten samples are averaged. The analog output must be divided so the ESP32-S3 ADC pin never
exceeds its electrical limit.
