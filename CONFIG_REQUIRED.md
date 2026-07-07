# CONFIG_REQUIRED — Smart Home System

Every credential, URL, and key the system needs at runtime, and where to put it.
Nothing below is hardcoded in source: the mobile app reads Gradle properties,
the AOSP gateway reads `/data/secure/`, and the ESP32 nodes read NVS.
Search the codebases for `TODO_USER_CONFIG` to find every point that consumes
these values.

| Component | Config mechanism | Location |
|---|---|---|
| Mobile app (`smart_home_mobile_app`) | Gradle properties → `BuildConfig` / `resValue` | `gradle.properties` or `~/.gradle/gradle.properties` |
| AOSP gateway (`packages/apps/SmartHomeSystem`) | JSON file on device | `/data/secure/smarthome_oauth_secrets.json` |
| node1 sensor (`node_sensor_enviroment`) | NVS, namespace `storage` | flashed NVS partition |
| node2 door (`node_rfid_finger_print`) | NVS, namespace `storage` | flashed NVS partition |
| node3 camera (`node_camera`) | NVS, namespace `storage` | flashed NVS partition |

Firebase project: `smart-home-system-e8c91`
Realtime Database URL: `https://smart-home-system-e8c91-default-rtdb.asia-southeast1.firebasedatabase.app`

---

## 1. SECURITY: rotate the exposed Firebase API key first

`smart_home_mobile_app/app/google-services.json` was committed to git and contains
the API key `AIzaSyBZMe6k6LJGTGkuTf64ekpau3i8uuh89vM`
(project `smart-home-system-e8c91`). Treat it as compromised:

1. In Google Cloud Console → APIs & Services → Credentials, **regenerate** the
   Android API key (or create a new one and delete the old one).
2. Add restrictions: Android app restriction (package
   `com.example.smart_home_mobile_app` + release SHA-1) and API restriction
   (Identity Toolkit API, Firebase-related APIs only).
3. Put the new key in `gradle.properties` as `FIREBASE_API_KEY` (below) and in
   the gateway secrets file as `firebase_api_key` (section 3).
4. The Gradle build does **not** apply the `google-services` plugin — the file
   is unused. Delete `app/google-services.json` from the repo and add it to
   `.gitignore` so it cannot leak again.

Also enable Firebase Realtime Database security rules (auth-gated per
`homes/<homeId>/members`); the mobile app and gateway both assume authenticated
access only.

---

## 2. Mobile app (`smart_home_mobile_app`)

Set these in the project `gradle.properties` (do not commit real values) or in
`~/.gradle/gradle.properties` (preferred). All are consumed in
`app/build.gradle` — each site is marked `TODO_USER_CONFIG`.

```properties
# Firebase (console → Project settings → General → your Android app)
FIREBASE_APPLICATION_ID=1:1025473699578:android:d9478c1c276f870051f343
FIREBASE_API_KEY=<rotated Android API key>            # TODO_USER_CONFIG
FIREBASE_DATABASE_URL=https://smart-home-system-e8c91-default-rtdb.asia-southeast1.firebasedatabase.app

# Google sign-in (Credential Manager). This is the *Web* OAuth client ID,
# NOT the Android client ID.
GOOGLE_WEB_CLIENT_ID=<web-client-id>.apps.googleusercontent.com   # TODO_USER_CONFIG
```

Behavior when unset: email/password login still works; the Google button shows
a "not configured" message pointing at this file.

### 2.1 Google provider setup

1. Firebase console → Authentication → Sign-in method → enable **Google**.
2. Firebase console → Project settings → your Android app → add the **SHA-1
   and SHA-256** of every signing key you use:
   - debug: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
   - release: same command against your release keystore.
   This registration is required by **both** Google sign-in (Credential
   Manager) and the Apple web flow (§2.2): Firebase validates the calling
   app's package certificate hash, and an unregistered SHA fails with
   `INVALID_CERT_HASH` ("There was an error while trying to get your package
   certificate hash.").
3. If the `FIREBASE_API_KEY` has Android application restrictions (section 1),
   the restriction entry must list the same package
   (`com.example.smart_home_mobile_app`) and SHA-1 — a mismatch produces the
   same `INVALID_CERT_HASH` failure.
4. Google Cloud Console → Credentials → OAuth 2.0 Client IDs → copy the
   **Web client** ID (auto-created when the Google provider is enabled) into
   `GOOGLE_WEB_CLIENT_ID`.
5. The device/emulator needs Google Play services and a signed-in Google
   account; the Sign in with Google dialog offers to add one if none exists.

### 2.2 Apple provider setup (no local key needed)

Apple sign-in on Android runs entirely through Firebase's web flow
(`OAuthProvider "apple.com"`); the app needs no local Apple key, but the
Firebase project must be configured (requires an Apple Developer account).
The web flow also validates the app's signing certificate, so the SHA-1/SHA-256
registration in §2.1 step 2 is a prerequisite — without it the Apple button
fails with `INVALID_CERT_HASH` before reaching Apple at all:

1. Apple Developer portal: create an **App ID**, a **Services ID**, and a
   **Sign in with Apple key** (.p8, note the Key ID and Team ID).
2. Configure the Services ID with return URL
   `https://smart-home-system-e8c91.firebaseapp.com/__/auth/handler`.
3. Firebase console → Authentication → Sign-in method → enable **Apple**;
   enter Services ID, Team ID, Key ID, and the .p8 key contents.

Until this is done the Apple button fails with Firebase's provider error —
this is expected fail-closed behavior.

---

## 3. AOSP gateway (`packages/apps/SmartHomeSystem`, Raspberry Pi 4)

All secrets live in one root-only JSON file read at runtime
(`OAuthBackendHandler.loadSecrets()`, `FirebaseSyncService`):

**Path:** `/data/secure/smarthome_oauth_secrets.json`

```jsonc
{
  // TODO_USER_CONFIG — rotated Firebase Android/API key (section 1)
  "firebase_api_key": "",
  "firebase_database_url": "https://smart-home-system-e8c91-default-rtdb.asia-southeast1.firebasedatabase.app",

  // TODO_USER_CONFIG — shared secret for node → gateway HTTP ingest HMAC
  // (64 hex chars = 256-bit key; must equal the `auth_key` NVS value
  //  provisioned on every node — see section 4).
  // Generate with: openssl rand -hex 32
  "ingest_hmac_secret": "",

  "oauth_providers": {
    "google": {
      "authorization_endpoint": "https://accounts.google.com/o/oauth2/v2/auth",
      "token_endpoint": "https://oauth2.googleapis.com/token",
      "client_id": "",            // TODO_USER_CONFIG — Web OAuth client
      "client_secret": "",        // TODO_USER_CONFIG
      "redirect_uri": "",         // TODO_USER_CONFIG — registered redirect
      "scope": "openid email profile",
      "firebase_provider_id": "google.com",
      "firebase_credential_field": "id_token",
      "pkce_enabled": true
    },
    "facebook": {
      // Use the current Graph API version for both endpoints, e.g. vXX.0
      "authorization_endpoint": "https://www.facebook.com/vXX.0/dialog/oauth",
      "token_endpoint": "https://graph.facebook.com/vXX.0/oauth/access_token",
      "client_id": "",            // TODO_USER_CONFIG — Facebook App ID
      "client_secret": "",        // TODO_USER_CONFIG — Facebook App Secret
      "redirect_uri": "",         // TODO_USER_CONFIG
      "scope": "email public_profile",
      "firebase_provider_id": "facebook.com",
      "firebase_credential_field": "access_token",
      "pkce_enabled": false
    },
    "apple": {
      "authorization_endpoint": "https://appleid.apple.com/auth/authorize",
      "token_endpoint": "https://appleid.apple.com/auth/token",
      "client_id": "",            // TODO_USER_CONFIG — Apple Services ID
      "client_secret": "",        // TODO_USER_CONFIG — signed JWT (ES256, .p8 key)
      "redirect_uri": "",         // TODO_USER_CONFIG
      "scope": "email name",
      "firebase_provider_id": "apple.com",
      "firebase_credential_field": "id_token",
      "pkce_enabled": true
    }
  }
}
```

Validation enforced in code (`OAuthBackendHandler.loadProviderConfig`): both
endpoints must be `https://`, `client_id`/`redirect_uri`/`scope`/
`firebase_provider_id` must be non-blank, and `firebase_credential_field`
must be `id_token` or `access_token` — otherwise the provider is disabled
(fail closed, logged).

**Install on the device:**

```bash
adb root
adb shell mkdir -p /data/secure
adb push smarthome_oauth_secrets.json /data/secure/smarthome_oauth_secrets.json
# Must be readable by the SmartHomeSystem app UID and nobody else.
# Check the app's UID with: adb shell ps -A | grep smarthome
adb shell chown system:system /data/secure/smarthome_oauth_secrets.json
adb shell chmod 600 /data/secure/smarthome_oauth_secrets.json
```

If SELinux denies the read in enforcing mode, check `adb shell dmesg | grep avc`
and label the file/directory to match the app's existing policy — do not relax
existing policies.

---

## 4. ESP32 nodes — NVS provisioning

All three nodes read namespace **`storage`** from the standard `nvs` partition
(see each project's `partitions.csv`). There is no runtime provisioning
console: generate an NVS partition image with ESP-IDF's
`nvs_partition_gen.py` and flash it. (node3 additionally accepts a
provisioning message over BLE Mesh — `provisioning_parser.c` — when mesh is
active.)

```bash
python $IDF_PATH/components/nvs_flash/nvs_partition_generator/nvs_partition_gen.py \
    generate node_config.csv nvs.bin 0x6000
# Offset/size: read the `nvs` row of the project's partitions.csv
esptool.py --chip <esp32s3|esp32> write_flash <nvs_offset> nvs.bin
```

### 4.1 node1 — environment sensor (`node_sensor_enviroment`, ESP32-S3)

```csv
key,type,encoding,value
storage,namespace,,
wifi_ssid,data,string,TODO_USER_CONFIG
wifi_pass,data,string,TODO_USER_CONFIG
gateway_ip,data,string,TODO_USER_CONFIG        # e.g. 192.168.1.50 (RPi4)
node_id,data,string,env_node_1
room_id,data,string,living_room
auth_key,data,string,TODO_USER_CONFIG          # 64 hex chars; = gateway ingest_hmac_secret
provisioned,data,u8,1
```

Sends readings to `http://<gateway_ip>:8080/api/readings` (port fixed in
`src/main.c`). Every POST carries `X-Auth-Timestamp` / `X-Auth-Nonce` /
`X-Auth-Signature` headers signed with `auth_key` (HMAC-SHA256, see
`include/ingest_auth.h`); the node refuses to boot without `auth_key` and
skips sends until SNTP has synced the wall clock.

### 4.2 node2 — door access (`node_rfid_finger_print`, ESP32-S3)

```csv
key,type,encoding,value
storage,namespace,,
wifi_ssid,data,string,TODO_USER_CONFIG
wifi_pass,data,string,TODO_USER_CONFIG
gateway_ip,data,string,TODO_USER_CONFIG
node_id,data,string,door_node_1
room_id,data,string,entrance
auth_key,data,string,TODO_USER_CONFIG          # 64 hex chars; = gateway ingest_hmac_secret
provisioned,data,u8,1
access_auth,namespace,,
hmac_key,data,hex2bin,TODO_USER_CONFIG         # 64 hex chars → 32-byte blob; openssl rand -hex 32
```

`hmac_key` (namespace `access_auth`) keys the HMAC that hashes RFID UIDs and
fingerprint IDs before storage/compare. It is independent of `auth_key` — do
not reuse the same value. Without it the credential store is not ready and
every credential (and enrollment) is denied.

Same ingest endpoint and signed `X-Auth-*` headers as node1. `access_seq` is an
internal monotonic counter maintained by the firmware — do **not** provision it.

**RFID card enrollment (after flashing + provisioning):**

1. Hold the on-board **BOOT button (GPIO0)** for ≥ 3 seconds. The log prints
   `RFID enrollment window open for 30 s; present a card` and the relay is
   forced off for the duration of the window.
2. Present the card to the MFRC522 reader within 30 seconds. The firmware
   stores only an HMAC-SHA256 hash of the UID (keyed by the node's `hmac_key`,
   never the raw UID) in NVS namespace `access_auth` as `rfid00`…`rfid31`.
3. Repeat per card (max 32). Presenting an already-enrolled card logs
   "was already enrolled" and changes nothing. If the window expires without
   a card, nothing is stored.

Enrollment requires the node to be provisioned with `hmac_key` (32-byte blob
in namespace `access_auth`) — without it the store is not ready and enrollment
fails closed. Cards enrolled this way survive reboots and power loss.

### 4.3 node3 — camera (`node_camera`, ESP32-CAM)

```csv
key,type,encoding,value
storage,namespace,,
wifi_ssid,data,string,TODO_USER_CONFIG
wifi_pass,data,string,TODO_USER_CONFIG
gateway_ip,data,string,TODO_USER_CONFIG
gateway_port,data,u16,8080
node_id,data,string,camera_node_1
room_id,data,string,living_room
snapshot_port,data,u16,8081
rtsp_port,data,u16,8554
auth_key,data,string,TODO_USER_CONFIG          # 64 hex chars; = gateway ingest_hmac_secret
provisioned,data,u8,1
```

- `auth_key` protects the camera's snapshot/stream HTTP endpoints
  (constant-time compare in `camera_server.c`) and must match the gateway's
  `ingest_hmac_secret`. Generate once: `openssl rand -hex 32`.
- RTSP: the camera serves standard RTSP (OPTIONS/DESCRIBE/SETUP/PLAY/TEARDOWN)
  at `rtsp://<camera-ip>:<rtsp_port>/` — the gateway must be configured with
  this URL/port, and `rtsp_port` must be free on your LAN (8554 is the
  conventional non-privileged RTSP port).
- If a port value is 0 or any field is missing, the node logs
  "No complete provisioning record" and keeps camera + Wi-Fi off (fail closed).

---

## 5. Quick checklist

- [ ] Rotate + restrict the exposed API key `AIzaSyBZMe...89vM`; delete `google-services.json` from git
- [ ] Firebase console: enable Email/Password, Google, Apple providers
- [ ] Firebase console: register debug + release SHA-1/SHA-256
- [ ] `gradle.properties`: FIREBASE_APPLICATION_ID / FIREBASE_API_KEY / FIREBASE_DATABASE_URL
- [ ] `gradle.properties`: GOOGLE_WEB_CLIENT_ID (Web client, not Android client)
- [ ] Apple: Services ID + .p8 key configured in Firebase console
- [ ] Gateway: `/data/secure/smarthome_oauth_secrets.json` installed, chmod 600
- [ ] Gateway: `ingest_hmac_secret` generated (`openssl rand -hex 32`)
- [ ] Nodes: NVS CSVs provisioned (Wi-Fi, gateway IP, node/room IDs, camera ports, `auth_key`)
- [ ] node2: `access_auth/hmac_key` provisioned (distinct from `auth_key`), then enroll cards via BOOT-button window
- [ ] Firebase RTDB security rules deployed (no unauthenticated access)
