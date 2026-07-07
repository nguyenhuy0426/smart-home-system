# Deferred reliability work after real sensor acquisition

These items are deliberately not represented as implemented features:

- Offline replay: add a bounded, checksummed NVS or flash queue behind `post_telemetry()` in
  `src/main.c`. Queue records must preserve the existing persistent `sequence`, replay in order, and
  be deleted only after a 2xx gateway response. Flash wear and power-loss recovery need tests before
  enabling it.
- OTA: add a signed-image state machine outside the sampling task. It must verify an authenticated
  manifest and image digest, use the existing OTA partitions, mark the new image valid only after a
  sensor/self-test checkpoint, and roll back on failure.
- Wall-clock time: `observedAtEpochMs` remains JSON `null` until authenticated time synchronization
  exists. `observedAtUptimeMs` is monotonic ESP timer uptime and is never labeled as epoch time.
- LAN authentication: the current HTTP transport is unauthenticated. HMAC-authenticated telemetry is
  scheduled with the gateway phase; until then, use only an isolated test network.

The persistent sequence allocator reserves blocks of 1,024 values in NVS. Reboots can create gaps,
but cannot reuse a previously reserved sequence; this reduces NVS write wear while retaining stable
reading IDs.
