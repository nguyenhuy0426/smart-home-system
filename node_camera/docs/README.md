# ESP32-CAM transport

This firmware targets the AI-Thinker ESP32-CAM/OV2640 pin map. It initializes
`esp32-camera` in JPEG mode at VGA with two PSRAM framebuffers. If PSRAM is not
available, it deliberately drops to one QVGA DRAM framebuffer. Initialization or
capture failure never produces a substitute image.

## Provisioning record

The BLE vendor payload is a bounded sequence of nine NUL-terminated fields with
no trailing bytes:

`SSID, password, gateway IPv4, gateway port, node ID, room ID, snapshot port, RTSP port, auth key`

The authentication key is exactly 64 hexadecimal characters. The complete
record is validated before it is committed to the `storage` NVS namespace.
There are no compiled Wi-Fi credentials, deployment addresses, or auth keys.

## Snapshot

`GET /api/snapshot` returns the captured JPEG with `Content-Type: image/jpeg`,
`Content-Length`, `Cache-Control: no-store`, `X-SmartHome-Node-Id`, and
`X-SmartHome-Room-Id`. Supply the provisioned key in `X-SmartHome-Auth`.
Capture or Wi-Fi failure returns `503`; missing/wrong authentication returns
`401`. No cached or dummy JPEG is returned.

## RTSP/RTP JPEG

The provisioned RTSP port supports `OPTIONS`, `DESCRIBE`, UDP-unicast `SETUP`,
`PLAY`, and `TEARDOWN`. Requests other than `OPTIONS` require
`Authorization: Bearer <64-hex-key>`. TCP interleaving is rejected with RTSP
`461 Unsupported Transport`.

`DESCRIBE` returns SDP for static JPEG payload type 26 at a 90 kHz clock. SDP
contains `a=x-node-id` and `a=x-room-id` so the gateway can bind a source to its
provisioned location. `PLAY` sends real OV2640 JPEG scan data using RFC 2435:
per-session SSRC/sequence/timestamp state, a correct JPEG payload header,
in-band quantization tables, fragment offsets, and the marker bit on the final
packet.

Two client tasks are allowed. Each has independent UDP ports and RTP state.
Capture access is serialized, socket sends are time-bounded, inactive sessions
expire after 30 seconds, and Wi-Fi/capture/send failures close the affected
session. Five consecutive null/invalid driver frames disable capture until reboot
instead of repeatedly advertising a broken camera. RTCP ports are reserved for
the session, but sender reports are not yet emitted.

The AOSP gateway still needs its own standards-compliant RTSP negotiation, SDP
parsing, authentication header, and RFC 2435 reconstruction before end-to-end
video can be tested. That gateway work is outside Phase 3.

## Verification status (2026-07-03)

- Host protocol tests from the real repository: **passed** (`1/1` CTest
  executable). Assertions cover RTSP parsing and malformed requests, SDP and
  node/room attributes, constant-time authorization comparison, session timeout,
  bounded provisioning fields, malformed JPEG rejection, and multi-packet RFC
  2435 header/offset/sequence/timestamp/marker behavior.
- Clean PlatformIO firmware build from the real repository: **passed** using
  ESP-IDF 5.3.2 for the AI-Thinker `esp32cam` target. Result: 83,260 bytes RAM
  (25.4%) and 1,561,908 bytes application flash (80.3% of the 1,945,600-byte
  application partition).
- Snapshot implementation: **source-verified and compiled**. It acquires a real
  driver framebuffer, returns it after `httpd_resp_send`, and returns `401` or
  `503` rather than image data on authentication/capture failure. No physical
  camera or HTTP client trial was performed in this phase.
- RTSP/RTP implementation: **host-tested and compiled**. `PLAY` enters the frame
  loop and transmits RFC 2435 UDP packets; it is no longer an acknowledgement-only
  path. No physical-camera or end-to-end gateway trial was performed.
- Remaining blocker: the gateway is still an RTP listener rather than an RTSP
  client. It must send authenticated `DESCRIBE/SETUP/PLAY`, parse SDP, bind the
  node/room attributes, and reconstruct RFC 2435 JPEG payloads before system video
  trials can begin.
