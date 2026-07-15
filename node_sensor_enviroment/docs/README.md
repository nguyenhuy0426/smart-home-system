<!-- Responsibility: documents the current environment-node firmware and physical wiring contract. -->

# Environment Sensor Node

Native ESP-IDF/PlatformIO firmware for an ESP32-S3 DevKitC-1. Local sensor acquisition and the
ST7789 display run without provisioning, Wi-Fi, a gateway, or cloud connectivity. BLE Mesh is used
only for unprovisioned setup; provisioned boots use Wi-Fi.

Installed wiring:

- MQ7 AO: GPIO2 / ADC1_CH1; raw mV is always sampled, ppm requires real calibration.
- BME680: SDA GPIO8, SCL GPIO9.
- ST7789: MOSI GPIO11, SCLK GPIO12, DC GPIO13, RST GPIO14, BLK GPIO15, no CS.
- GP2Y1014: not installed and disabled; it has no ADC assignment.
- DHT22: permanently removed; GPIO5 is not owned or initialized by this node.

The ST7789 shows real BME680 and MQ7 data plus BLE/local state. Wall-clock
display uses the existing SNTP-synchronized system clock only after it passes
the validity check. Before synchronization it shows `--:--:--` and
`DATE UNSYNCED`. The firmware has no configured timezone source: synchronized
time is therefore labeled UTC, and DAY/NIGHT is withheld. If a real runtime
`TZ` configuration is supplied later, local time is used and the sun/moon
period icon is enabled; no timezone offset is hardcoded.

Schema-v1 telemetry keeps only `sensorStatus.dht22="unsupported"` for backward
compatibility. It emits no DHT22 value; the stable ambient temperature and
humidity metric keys now carry real BME680 values with source `BME680`.

See `SENSOR_CALIBRATION.md` and `../watch_dog/RUNTIME_DEBUG_REPORT.md` for the calibration contract
and timestamped hardware evidence.
