# Smart Home implementation audit

Audit date: 2026-07-04

## Scope and requirement baseline

Reviewed repositories:

- `/home/huynn/smart_home/node_sensor_enviroment`
- `/home/huynn/smart_home/node_rfid_finger_print`
- `/home/huynn/smart_home/node_camera`
- `/home/huynn/aosp/source/packages/apps/SmartHomeSystem`
- `/home/huynn/smart_home/smart_home_mobile_app`
- `/home/huynn/fall-detection-dataset/human_and_fall_detect`

The latest product request explicitly selects Firebase Realtime Database (RTDB). The external
architecture document still contains Firestore-oriented paths in places. This implementation uses
RTDB and the canonical architecture document should be updated so that cloud schema and security
rules have one authoritative backend.

## Current status

| Subsystem | Status | Operational conclusion |
|---|---|---|
| Environmental ESP32-S3 node | Fails requirements | Sensor drivers publish fallback/fabricated values and several drivers are incomplete. Data must not be treated as measurements. |
| RFID/fingerprint ESP32-S3 node | Unsafe | Unknown cards are granted access and simulated credentials can energize the relay when hardware reads fail. Do not connect this build to a real door. |
| ESP32-CAM node | Fails requirements | Camera initialization/capture and RTP streaming are absent. The RTSP control endpoint does not send a video stream. |
| AOSP gateway app | Builds; camera runtime validation pending | Authenticated UDP RTSP negotiation, strict SDP/RTP/RFC 2435 JPEG ingestion, dual-model ONNX inference, telemetry ingest, and durable RTDB queuing are implemented. Real ESP32-CAM-to-gateway runtime validation and BLE Mesh provisioning remain blockers. |
| Android mobile app | Prototype only | Auth, Firebase data, node/room data, charts, and camera content are simulated or hardcoded. |

The three external firmware/mobile repositories were read-only in this workspace pass. No claim is
made that their critical defects have been fixed.

## Firmware findings

### Environmental node

- DHT22 returns hardcoded temperature/humidity after read failure.
- MQ7 has no heater-cycle state machine or calibrated resistance-to-ppm conversion and publishes
  fallback values as samples.
- GP2Y1014 ADC initialization creates conflicting ADC unit ownership and can leave an invalid
  handle; fallback PM2.5 values are still published.
- CJMCU-680/BME680 code reads identification/status but fabricates the environmental output instead
  of performing forced-mode acquisition, calibration compensation, and gas-resistance processing.
- Uptime is labeled as epoch time.
- HTTP telemetry is unauthenticated, has no offline replay queue, and OTA is absent.
- BLE Mesh message parsing calls `strlen` on a bounded network buffer that is not guaranteed to be
  NUL-terminated.

### RFID/fingerprint node

- Any detected RFID card is accepted without checking an enrolled credential policy.
- Synthetic RFID/fingerprint success paths periodically unlock the relay when physical hardware is
  missing or returns no match.
- A hardcoded salt is used for credential derivation.
- RC522 and TZM1026 UART framing/validation are incomplete; malformed or stale data is not rejected
  consistently.
- Denied-attempt reporting, offline replay, authenticated telemetry, and OTA are absent.
- Raw fingerprint templates are not uploaded, which is consistent with the privacy requirement.

### Camera node

- `esp_camera_init` and `esp_camera_fb_get` are not used.
- The snapshot endpoint returns a dummy 1x1 JPEG.
- RTSP `SETUP` returns a fixed client port and `PLAY` does not start RTP packet transmission.
- Client handling is synchronous and does not satisfy the multi-camera/multi-client requirement.
- SDP/RTP JPEG packetization is incomplete.

## Gateway changes completed in this pass

- Restricted the Soong module to Smart Home sources/resources so inherited AOSP DevCamera/TV
  resources no longer participate in this app build.
- Corrected ONNX Runtime packaging: the AAR is imported with `extract_jni: true`, including both
  `libonnxruntime.so` and `libonnxruntime4j_jni.so` for arm64.
- Validated both model contracts as float NCHW `[1,3,640,640]` to `[1,5,8400]` and implemented the
  training-compatible rounded letterbox, RGB normalization, box unletterboxing, and NMS.
- Runs human and fall models for each accepted frame, closes all ORT resources, serializes inference,
  and drops frames when inference is already busy instead of building an unbounded backlog.
- Replaced fake sensor values in the dashboard with one live card per node and all seven required
  environmental metrics.
- Added bounded LAN HTTP parsing with method/path/body/identifier validation.
- Added a thread-safe, idempotent, atomically persisted RTDB replay queue. Protocol
  `readingId`/`eventId` values are preserved, old sequence-only queue records migrate on load, and
  RTDB writes use `if-match: null_etag` so a retry cannot overwrite an existing record.
- Added a deny-by-default RTDB rules template with home membership roles, gateway-only telemetry
  writes, member command requests, and identical idempotent retry support. Initial `device_admin`
  membership must be bootstrapped by a trusted deployment process, not by a client app.
- Added Firebase password registration/login and Google/Facebook/Apple authorization-code exchange
  configuration without embedded credentials or mock-token fallback.
- Replaced embedded OAuth WebView authorization with the system browser, PKCE S256, exact callback
  matching, CSRF state validation, and a ten-minute flow TTL. Google explicitly rejects embedded
  user-agents: <https://developers.google.com/identity/protocols/oauth2/policies>.
- Persists Firebase ID/refresh tokens as one AES-GCM encrypted session using a non-exportable Android
  Keystore key and refreshes the ID token before RTDB queue replay.
- Loads `home_id` once and disables cloud upload when it is missing/invalid instead of fabricating
  `home_1`.
- Fixed gateway/Firebase service binding order so either service may connect first.
- Made the gateway an ongoing `connectedDevice` foreground service, moved Firebase service ownership
  out of the dashboard, added a user-visible status notification, and added post-boot restart so
  ingestion is not tied to an open Activity.
- Added explicit camera source-to-node/room mapping and isolates frame assembly by source address,
  RTP SSRC, and timestamp so simultaneous camera streams do not share buffers.
- Replaced the passive camera UDP listener with an authenticated RTSP client implementing
  `OPTIONS`, `DESCRIBE`, UDP-unicast `SETUP`, `PLAY`, and `TEARDOWN`. Responses are bounded and
  validated for status, `CSeq`, `Content-Length`, session, timeout, transport, server ports, and
  SSRC; TCP interleaving is rejected.
- Added strict JPEG SDP validation and extraction of payload type, clock rate, control URL,
  `a=x-node-id`, and `a=x-room-id`. SDP identity must match the durable camera descriptor.
- Added RTP validation and RFC 2435 JPEG reconstruction with loss, duplicate, reordering, overlap,
  timeout, quantization-table, malformed-JPEG, and maximum-frame handling. Only complete SOI/EOI
  JPEG frames enter the bounded ONNX pipeline.
- Detection events now retain camera node/room identity and use deterministic per-frame event IDs
  when entering the durable RTDB queue; camera failures do not create detections.
- Added the direct `androidx.compose.runtime_runtime` Soong dependency required by kotlinc to inline
  Compose `remember`/effect APIs. This fixes the `couldn't find inline method ... remember` IR
  backend exception reported while compiling `DashboardActivity.kt`.
- Added Keystore-backed per-home HMAC for stable hardware fingerprints and testable node identity
  assignment.
- BLE provisioning now fails explicitly rather than reporting simulated success. A real Android BLE
  Mesh provisioner stack is still required.

## Gateway gaps that remain

1. RTSP/RTP camera ingestion is implemented and host-tested, but has not yet been exercised against
   a physical ESP32-CAM and Raspberry Pi gateway on the deployment network. Camera descriptors must
   still be provisioned with node ID, room ID, host, port, stream path, and auth key; no field has a
   production hardcoded fallback.
2. Android public BLE APIs provide GATT primitives, not a complete Bluetooth Mesh provisioner/vendor
   model stack. A real, licensed/maintained mesh stack and fixed company/model identifiers must be
   selected before implementation.
3. Provisioned identities/descriptors are not yet integrated with a durable Android store or UI.
4. LAN telemetry validates shape but is not cryptographically authenticated and trusts the node's
   room assignment.
5. The gateway has no authenticated HTTPS endpoint for a separate mobile app to submit OAuth codes.
6. RTDB security rules now have a checked-in template, but home membership/role bootstrap and the
   deployed Firebase project configuration are not present.
7. OAuth requires a browser-capable AOSP image. A TV image without a secure browser needs another
   authorization surface (for example, companion-phone or provider-supported device flow).
8. The rpi4 product policy has no dedicated SELinux domain/file type or provisioning service for
   `/data/secure/smarthome_oauth_secrets.json`. The app therefore cannot be assumed to read this file
   on an enforcing build. Mapping the secret to the shared `platform_app` domain would expose it to
   other platform apps; a dedicated, tested domain or Keystore-backed provisioning service is needed.
9. Android 15 BLE scan/connect permissions need an rpi4 default-permission grant or an interactive
   runtime permission flow. Declaring them in the manifest is not sufficient by itself.
10. The current ONNX files contain Ultralytics AGPL-3.0 metadata. Distribution licensing must be
   reviewed before shipping a product.

## Mobile app gaps that remain

- Login/register/OAuth completion is simulated and no Firebase session is established.
- Nodes, rooms, telemetry, charts, profile, and camera content are hardcoded.
- There is no RTDB listener/repository, offline state, multi-home membership model, command request
  flow, or logout token revocation.
- The current visual language is a generic blue template rather than the requested warm-neutral
  glass system, and template/dead source files remain.
- Only generated example tests exist.

## Verification performed

- Host JUnit via `atest SmartHomeSystemUnitTests`: 23 tests pass, 0 fail. Coverage includes RTSP
  request/response/authorization/transport handling, strict SDP identity mapping, RTP validation,
  packet loss/reordering/duplicates, RFC 2435 reconstruction and malformed/oversized rejection, in
  addition to queue durability/idempotency, provisioning identity, OTA state, and YOLO decode/NMS.
- Auth/session/sync, LAN ingest, RTP ingest, BLE service, dual-model inference, and gateway
  orchestration compile with the AOSP Kotlin compiler against Android 15 system stubs and the
  checked-in ONNX Runtime API.
- `Android.bp` formatting and XML/JSON syntax are checked separately.
- Full Soong target `m SmartHomeSystem -j2` succeeds for `aosp_rpi4-bp1a-userdebug`; the signed APK,
  VDEX, and ODEX install artifacts are generated under
  `out/target/product/rpi4/system/priv-app/SmartHomeSystem/`.
- PlatformIO reaches the installed ESP-IDF builder but fails before compiling project source because
  that environment defines duplicate `mutex.c.o` targets in ESP-IDF Bluetooth/BLE-Mesh components.
- Mobile Gradle cannot start its lock-contention service in this sandbox because no usable wildcard
  network interface is exposed.

## Required implementation order

1. Remove all simulated success/unlock/measurement paths in both ESP32-S3 nodes and add driver/parser
   unit tests before hardware integration.
2. Implement camera capture plus standards-compliant RTSP/RTP JPEG streaming; then implement RTSP
   negotiation and provisioned camera identity on the gateway.
3. Select and integrate a real BLE Mesh provisioner/vendor-model stack and freeze protocol/company/
   model identifiers.
4. Add durable node/descriptors/room storage, telemetry message authentication, RTDB rules, and role
   enforcement.
5. Replace the mobile prototype with Firebase-backed repositories/auth and the requested UI system.
6. Run Soong, firmware, mobile, and device integration tests in environments that permit their build
   daemons and hardware access.
