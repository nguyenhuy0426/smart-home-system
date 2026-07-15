# Sensor calibration and wiring contract

The firmware has no fallback calibration constants. Missing or malformed values keep the affected
metric invalid; the telemetry envelope reports `calibration_missing` and may include the calibrated
ESP32 ADC voltage as `raw_uncalibrated`.

Current installed wiring is MQ7 AO on GPIO2/ADC1_CH1 and BME680 I2C on
GPIO8/GPIO9. DHT22 is permanently removed and GPIO5 is unused by this node.
GP2Y1014 is not installed, has no ADC assignment, and remains disabled. GPIO2
is exclusive to MQ7.

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

## MQ7 module and raw acquisition

The installed three-pin module exposes only VCC, GND, and AO. AO is wired to GPIO2/ADC1_CH1. There
is no MCU heater-control input, so firmware must not initialize PWM/LEDC or gate ADC acquisition on
software heater-phase metadata. Every diagnostic cycle logs raw sample range/average and calibrated
ESP32-pin millivolts. CO ppm remains invalid until the assembled module and any ADC divider have
real NVS calibration values. Verify AO never exceeds the ESP32-S3 ADC electrical limit.

## GP2Y1014 status

GP2Y1014 is not installed. Its driver is retained in the source tree but is never initialized or
sampled, its reserved LED GPIO6 remains undriven, and no ADC channel is assigned. PM2.5 is reported
as `not_connected`. A future installation requires a new free ADC pin; GPIO2/ADC1_CH1 cannot be
reused because it is exclusively assigned to MQ7.
