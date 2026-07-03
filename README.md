# Smart Home System Setup Guide

This guide covers the setup process for the Firebase backend, wiring the physical hardware for the ESP32 nodes, and deploying the system.

## 1. Firebase Setup

The system relies on Firebase for remote synchronization, configuration, and authentication.

### Prerequisites
- Node.js and npm installed
- Firebase CLI installed (`npm install -g firebase-tools`)

### Initialization
1. Create a new Firebase project in the [Firebase Console](https://console.firebase.google.com/).
2. Enable **Firestore**, **Firebase Authentication** (Email/Password), and **Firebase Storage**.
3. In your local terminal, navigate to the project root and login:
   ```bash
   firebase login
   firebase use --add <your-project-id>
   ```
4. Deploy the security rules (implemented in Task 2):
   ```bash
   firebase deploy --only firestore:rules
   ```

### Local Testing (Emulator Suite)
To run the system locally without affecting production data:
```bash
firebase emulators:start --only firestore,auth,storage
```

---

## 2. Hardware Wiring Guide

The smart home nodes use the ESP32-S3 (or similar ESP-IDF compatible boards). Below are the wiring requirements for each node type.

### 2.1 Environment Sensor Node
Collects temperature, humidity, and air quality metrics.

| Component | ESP32 Pin | Notes |
| :--- | :--- | :--- |
| **DHT22** (Temp/Humidity) | Data: GPIO 4 | Primary sensor. Requires 10k pull-up resistor. |
| **CJMCU-680 / BME680** | I2C SDA: GPIO 21<br>I2C SCL: GPIO 22 | Secondary sensor. |
| **MQ7** (Carbon Monoxide) | Analog: GPIO 34<br>Heater PWM: GPIO 18 | Requires 5V for heater cycle. |
| **GP2Y1014** (Dust/PM2.5) | Analog: GPIO 35<br>LED: GPIO 19 | Requires 150-ohm resistor and 220uF capacitor. |

### 2.2 Access Control Node
Manages entry via RFID and Fingerprint matching.

| Component | ESP32 Pin | Notes |
| :--- | :--- | :--- |
| **TZM1026** (Fingerprint) | UART TX: GPIO 17<br>UART RX: GPIO 16 | 3.3V logic. |
| **RC522** (RFID) | SPI MOSI: GPIO 23<br>SPI MISO: GPIO 19<br>SPI CLK: GPIO 18<br>CS/SDA: GPIO 5<br>RST: GPIO 27 | 3.3V logic. |
| **Relay Module** (Door) | Control: GPIO 26 | Triggers the physical strike plate. |

### 2.3 Camera Node
Provides motion detection and RTSP streaming.

| Component | ESP32 Pin | Notes |
| :--- | :--- | :--- |
| **ESP32-CAM** Module | Integrated | Uses the internal OV2640 camera module. |
| **PIR Sensor** (Optional)| GPIO 13 | Can be used to wake the camera from sleep. |

---

## 3. Deployment

### Nodes (ESP-IDF)
Use PlatformIO to build and flash each node. Make sure your `capability_descriptor.*.jsonc` configs are updated.
```bash
cd node_sensor_environment
pio run -t upload
```

### Gateway (AOSP System App)
The gateway is built as an Android System App.
1. Build the APK via AOSP or Gradle.
2. Push to the device's privileged app partition (`/system/priv-app/`).
3. Ensure the gateway has the required permissions in its `AndroidManifest.xml` (Bluetooth, Internet, System Alert Window for UI).

### Mobile App
Build and install via Android Studio. Connect it to the same Firebase project using `google-services.json`.
