# CONFIG_REQUIRED — SmartHomeSystem gateway (AOSP / Raspberry Pi 4)

The gateway reads every secret from one root-installed JSON file at runtime.
Nothing is compiled in; with the file absent, Firebase sync and OAuth login
are disabled and log a warning (fail closed).

**Path:** `/data/secure/smarthome_oauth_secrets.json`
Consumers: `security/OAuthBackendHandler.kt` (OAuth providers, Firebase key),
`firebase/FirebaseSyncService.kt` (`firebase_api_key`, `firebase_database_url`).

## Schema

```jsonc
{
  "firebase_api_key": "",          // TODO_USER_CONFIG — Firebase Web API key (rotate the leaked AIzaSyBZMe...89vM key first)
  "firebase_database_url": "https://smart-home-system-e8c91-default-rtdb.asia-southeast1.firebasedatabase.app",

  // TODO_USER_CONFIG — 64 hex chars (openssl rand -hex 32); must equal the
  // `auth_key` NVS value provisioned on the ESP32 nodes. Used to HMAC-verify
  // node → gateway HTTP ingest on port 8080.
  "ingest_hmac_secret": "",

  "oauth_providers": {
    "google": {
      "authorization_endpoint": "https://accounts.google.com/o/oauth2/v2/auth",
      "token_endpoint": "https://oauth2.googleapis.com/token",
      "client_id": "",             // TODO_USER_CONFIG
      "client_secret": "",         // TODO_USER_CONFIG
      "redirect_uri": "",          // TODO_USER_CONFIG
      "scope": "openid email profile",
      "firebase_provider_id": "google.com",
      "firebase_credential_field": "id_token",
      "pkce_enabled": true
    },
    "facebook": {
      "authorization_endpoint": "https://www.facebook.com/vXX.0/dialog/oauth",   // current Graph API version
      "token_endpoint": "https://graph.facebook.com/vXX.0/oauth/access_token",
      "client_id": "",             // TODO_USER_CONFIG — Facebook App ID
      "client_secret": "",         // TODO_USER_CONFIG — Facebook App Secret
      "redirect_uri": "",          // TODO_USER_CONFIG
      "scope": "email public_profile",
      "firebase_provider_id": "facebook.com",
      "firebase_credential_field": "access_token",
      "pkce_enabled": false
    },
    "apple": {
      "authorization_endpoint": "https://appleid.apple.com/auth/authorize",
      "token_endpoint": "https://appleid.apple.com/auth/token",
      "client_id": "",             // TODO_USER_CONFIG — Apple Services ID
      "client_secret": "",         // TODO_USER_CONFIG — ES256-signed JWT from the .p8 key
      "redirect_uri": "",          // TODO_USER_CONFIG
      "scope": "email name",
      "firebase_provider_id": "apple.com",
      "firebase_credential_field": "id_token",
      "pkce_enabled": true
    }
  }
}
```

Validation enforced by `OAuthBackendHandler.loadProviderConfig`: endpoints must
be HTTPS; `client_id`, `redirect_uri`, `scope`, `firebase_provider_id` must be
non-blank; `firebase_credential_field` must be `id_token` or `access_token`.
A provider failing validation is disabled and logged — never mocked.

## Install

```bash
adb root
adb shell mkdir -p /data/secure
adb push smarthome_oauth_secrets.json /data/secure/smarthome_oauth_secrets.json
adb shell chown system:system /data/secure/smarthome_oauth_secrets.json   # match the app's UID
adb shell chmod 600 /data/secure/smarthome_oauth_secrets.json
```

If SELinux (enforcing) denies the read, inspect `dmesg | grep avc` and label
the file to match existing policy; do not weaken existing policies.

See `smart_home/CONFIG_REQUIRED.md` (master document) for the mobile-app keys,
Firebase console provider setup, and ESP32 NVS provisioning that must match
`ingest_hmac_secret`.
