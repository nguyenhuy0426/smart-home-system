# BLE Mesh — Phase 1 vs Phase 2 Decision

Status: **decided 2026-07-06**. This document records what BLE Mesh is used
for *today* (Phase 1) and what remains deliberately deferred (Phase 2), based
on what the code actually implements — not on the target architecture in
[ARCHITECTURE.md](ARCHITECTURE.md).

## Decision

**BLE Mesh is Phase 1 for node provisioning only. All runtime data and
control traffic is Wi-Fi/HTTP. The mesh control plane described in
ARCHITECTURE.md (runtime telemetry, heartbeats, OTA manifests, descriptor
exchange over vendor models) is Phase 2 and is not implemented.**

## What is implemented today (Phase 1)

All three ESP32 nodes contain a real ESP-IDF BLE Mesh **provisioning server**
(`esp_ble_mesh_*` APIs — common/provisioning/networking/config-model), plus a
vendor model that receives the provisioning payload:

| Node | File | Behavior |
|------|------|----------|
| node1 environment | `node_sensor_enviroment/src/ble_mesh_handler.c` | Mesh starts **only when NVS has no provisioning record** (`main.c`: unprovisioned branch calls `ble_mesh_handler_init()` and returns). |
| node2 door access | `node_rfid_finger_print/src/ble_mesh_handler.c` | Same fail-closed pattern; relay forced off while unprovisioned. |
| node3 camera | `node_camera/src/ble_mesh_handler.c` | Same pattern. |

The vendor model accepts the 6-field provisioning record
(`SSID\0PASS\0GATEWAY_IP\0NODE_ID\0ROOM_ID\0AUTH_KEY\0`, parsed by
`provisioning_parser.c`) and persists it to NVS. Once provisioned, a node
boots directly into Wi-Fi operation and **never starts the mesh stack again**
— telemetry/events go to the gateway over HTTP with signed `X-Auth-*` headers.

The NVS CSV method in [CONFIG_REQUIRED.md](../CONFIG_REQUIRED.md) §4 is the
equivalent offline provisioning path; both write the same record.

## What is deferred (Phase 2)

On the AOSP gateway, the mesh side is explicitly stubbed:

- `src/com/android/smarthome/mesh/BleMeshService.kt` — only BLE scanning for
  the standard Mesh Provisioning Service UUID (`00001827-…`) to discover
  unprovisioned nodes over PB-GATT. It does not run a provisioner.
- `src/com/android/smarthome/mesh/BleMeshProvisionerStub.java` and
  `VendorModelProtocolStub.java` — placeholder boundaries; no mesh stack,
  no key distribution, no vendor-model traffic.

Phase 2 therefore covers:

1. Gateway-side mesh provisioner + proxy (AOSP has no in-tree Bluetooth Mesh
   stack; this requires integrating an external mesh library and allocating
   real Bluetooth SIG company/model IDs — marked `[TBD]` in ARCHITECTURE.md).
2. Runtime mesh control plane: heartbeats, descriptor exchange, command
   fan-out, and OTA manifest/rollback signaling over vendor models.
3. Wi-Fi transfer windows announced over mesh (2.4 GHz contention management).

## Why this split is correct

- The mesh vendor-model IDs and company ID are unallocated (`[TBD]` in
  ARCHITECTURE.md); shipping runtime vendor-model traffic with made-up IDs
  would violate the "real protocols only" project rule.
- The gateway currently reaches every node over the LAN, and the HMAC ingest
  contract already authenticates that path end to end.
- Node provisioning is the only flow that *cannot* use Wi-Fi (the node has no
  credentials yet), so it is the one mesh feature Phase 1 genuinely needs —
  and it is fully implemented on the node side. Until the gateway provisioner
  exists, use the NVS CSV provisioning path in CONFIG_REQUIRED.md §4.

## Operational consequence

- Provision nodes via the NVS CSV method (recommended today) or a
  standard BLE Mesh provisioner app that can deliver the 6-field payload.
- Do not expect node heartbeats/telemetry over mesh; node liveness is
  determined by ingest recency (`gatewayReceivedAtEpochMs staleness`).
