# Environmental node host tests

Run without hardware:

```sh
cmake -S test -B /tmp/environment-node-tests
cmake --build /tmp/environment-node-tests
ctest --test-dir /tmp/environment-node-tests --output-on-failure
```

The tests cover protocol decoding, invalid-data rejection, calibration gating,
heater phases, bounded provisioning parsing, BME680 status flags, and telemetry omission rules.
