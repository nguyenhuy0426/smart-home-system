# Firebase setup for the Smart Home system

The mobile app and AOSP gateway must use the same Firebase project and Realtime Database. Do not
commit API keys, account passwords, OAuth client secrets, service-account keys, or the deployed
gateway configuration.

## 1. Create the Firebase project

1. Open <https://console.firebase.google.com/> and select **Create a project**.
2. Choose a permanent project ID. Google Analytics is optional for this system.
3. In **Project settings > General**, add an Android app with package name
   `com.example.smart_home_mobile_app`.
4. The current mobile build initializes Firebase from Gradle properties, so `google-services.json`
   is not consumed. You may download and archive it securely, but do not place it in version control.
5. Record the Android **App ID** and **Web API key** shown in Project settings.

## 2. Enable Authentication

1. Open **Build > Authentication > Get started > Sign-in method**.
2. Enable **Email/Password**. Do not enable email-link-only authentication.
3. Create separate accounts for the gateway operator and mobile users, either through the app's
   registration screen or **Authentication > Users**.
4. Record each account UID from the Users table; RTDB membership uses the UID, not the email.

Google and Apple require deployed provider configuration (see CONFIG_REQUIRED.md §2) and show a
"not configured" message until it exists. Facebook login is not supported. The app never uses an
embedded OAuth WebView or simulated provider login.

## 3. Create Realtime Database and deploy rules

1. Open **Build > Realtime Database > Create Database**.
2. Choose the region closest to the home deployment and start in **Locked mode**.
3. Copy the complete rules from
   `/home/huynn/aosp/source/packages/apps/SmartHomeSystem/config/firebase_database.rules.example.json`
   into the Firebase **Rules** tab and publish them.
4. Record the database URL, for example
   `https://YOUR_PROJECT_ID-default-rtdb.REGION.firebasedatabase.app` or the exact URL shown by the
   console. Do not guess the regional suffix.

The rules deny root `/homes` listing. This is intentional, but means the app cannot automatically
discover memberships. A user adds an assigned home ID locally, then Firebase verifies access at
`homes/{homeId}`.

## 4. Bootstrap the first home and roles

The deny-by-default rules cannot create the first administrator membership from an untrusted client.
Use the Firebase console's Data tab or a one-time trusted Admin SDK deployment process to create:

```json
{
  "homes": {
    "home_my_house": {
      "displayName": "My house",
      "members": {
        "MOBILE_USER_UID": { "role": "device_admin" },
        "GATEWAY_USER_UID": { "role": "gateway_service" }
      },
      "rooms": {
        "room_living": { "label": "Living room" }
      }
    }
  }
}
```

Available roles are `home_member`, `access_admin`, `device_admin`, and `gateway_service`. Use
`access_admin` only for a mobile account that may request access actions. Never use the gateway
account for normal phone use.

## 5. Configure and build the mobile app

Put these values in the developer machine's `~/.gradle/gradle.properties`:

```properties
FIREBASE_APPLICATION_ID=1:1234567890:android:replace_with_android_app_id
FIREBASE_API_KEY=replace_with_web_api_key
FIREBASE_DATABASE_URL=https://exact-url-from-realtime-database-console
```

Then rebuild so the deployment values are compiled into that local APK:

```bash
cd /home/huynn/smart_home/smart_home_mobile_app
./gradlew testDebugUnitTest assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`, sign in, select **Add home**, and enter the exact
home ID such as `home_my_house`. A permission-denied result means the authenticated UID is missing
from `homes/{homeId}/members` or the deployed rules differ from the repository template.


## Production home membership flow without Cloud Functions

For the current Spark-plan friendly build, the mobile app writes home management data
directly to Realtime Database under restrictive rules:

1. A signed-in user creates a home from **Account > Manage homes > Create home**.
2. The app creates `homes/{homeId}`, assigns the creator as `device_admin`, and writes
   `userHomes/{uid}/{homeId}` so the app can rediscover the home.
3. A home admin creates an invite from **Manage homes > Create invite**.
4. Another signed-in user opens **Join home**, enters the invite code, and the app writes
   their own `homes/{homeId}/members/{uid}` plus `userHomes/{uid}/{homeId}`.

Deploy the Realtime Database rules before testing this flow:

```bash
firebase deploy --only database --project smart-home-system-5f4a3
```

This avoids Cloud Functions/Blaze for development. A production release should move
create-home and invite redemption back to a trusted backend.

## 6. Configure the AOSP gateway for the same project

Start from
`/home/huynn/aosp/source/packages/apps/SmartHomeSystem/config/smarthome_oauth_secrets.example.json`.
Set `home_id`, `firebase_api_key`, and `firebase_database_url` to the same values used above, then
provision the resulting file as `/data/secure/smarthome_oauth_secrets.json` with the intended SELinux
label and restrictive permissions. Do not commit the deployed file. Sign the gateway app in using
the Firebase account whose membership role is `gateway_service`.

The gateway's `/data/secure` SELinux/provisioning path remains a documented Phase 4 deployment
blocker and must be validated on the Raspberry Pi image before production use.

## 7. Current camera limitation

The mobile app reads real human/fall detection records from `homes/{homeId}/events`. Live camera
preview remains disabled until the physical ESP32-CAM-to-gateway RTSP runtime validation passes.
No fake frame or detection is substituted.

Debug APKs show **Preview main interface (debug only)** on the login screen. This route carries no
Firebase identity, reads no home data, and cannot submit commands. Release APKs do not show it.

