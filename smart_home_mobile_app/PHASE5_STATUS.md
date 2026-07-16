# Phase 5 mobile implementation status

Status date: 2026-07-04

## Implemented

- Removed simulated provider success flows. Email/password registration and sign-in now use
  Firebase Authentication. Google sign-in is configured per CONFIG_REQUIRED.md;
  Facebook login has been removed from the app entirely.
- Firebase session continuity uses the SDK refresh session plus an AES-GCM Android Keystore-encrypted
  local session marker. Logout signs out Firebase and clears the marker.
- Added authenticated RTDB listeners and parsing for home membership, rooms, generic/future node
  types, node readings, access events, human/fall detections, descriptor actions, and command status.
- Added loading, empty, offline, permission-denied, configuration-error, and malformed-data states.
- Removed fixed user/profile, room, node, chart, telemetry, IP/firmware, camera-frame, and device-state
  values. Charts and metric cards are populated only from RTDB readings.
- Invalid, missing, stale, errored, and explicitly uncalibrated sensor metrics are labeled and are not
  presented as valid measurements.
- Access history displays result, credential kind, node, room, and time, without surfacing raw RFID
  UIDs or fingerprint templates.
- Camera UI displays only RTDB detection history with confidence and bounding boxes. Live preview is
  explicitly blocked pending Phase 4 gateway RTSP runtime validation.
- Commands write new records only under `homes/{homeId}/commandRequests/{requestId}` with user,
  home, node, action, timestamp, request ID, and pending status. Access actions require the
  `access_admin` role in the client and still require gateway authorization.
- Removed the unused template Activity/fragments/layout/navigation resources, in-memory placeholder
  repositories/models, fake provisioning/profile/chatbot screens, and sample tests.

## Verification

- `./gradlew testDebugUnitTest`: 7 tests passed, 0 failed.
- Covered auth success/failure, secure-session cleanup contract, empty home, permission denied,
  multiple rooms/nodes, invalid/stale telemetry, event parsing, and command request construction.
- `./gradlew assembleDebug`: passed in the real repository.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Deployment/runtime blockers

- Firebase build properties, Email/Password provider enablement, deployed RTDB rules, and trusted home
  membership bootstrap are required; see `FIREBASE_SETUP.md`.
- Current gateway rules deny reads at `/homes`, so automatic multi-home discovery is impossible.
  Users add assigned home IDs locally and membership is verified at each home path. A future
  protected `userHomes/{uid}` index would remove this onboarding limitation.
- The gateway must publish durable node metadata/descriptors for descriptor-declared action buttons
  and rich node labels. The mobile parser safely falls back to generic node rendering when absent.
- Camera live preview remains blocked until Phase 4 RTSP runtime validation succeeds.
- No physical phone/Firebase-project runtime trial was performed in this phase.

