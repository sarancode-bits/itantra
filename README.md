# iTantra — Offline Emergency Peer-to-Peer Communicator

**Platform:** Android (Kotlin, Native)  
**Architecture:** MVVM + Clean Architecture + Hilt + Room + Jetpack Compose  
**Primary Loop:** Off-grid voice speech-to-text transmission over local P2P radios with on-device text-to-speech audio playback.

---

## Product Overview

iTantra is a **fully offline**, phone-to-phone emergency walkie-talkie. Two nearby Android devices — without internet, SIM cards, or Wi-Fi routers — discover each other over Wi-Fi Direct and Bluetooth radios, pair, and exchange short transcribed voice messages:

1. **User speaks:** Press and hold the mic button on the Talk screen.
2. **On-device STT:** Converts speech into light text packets using Android `SpeechRecognizer` (`EXTRA_PREFER_OFFLINE`) or Vosk fallback.
3. **P2P Transport:** Transmits serialized JSON payloads over Google Nearby Connections API (`P2P_CLUSTER`).
4. **On-device TTS:** Receiving device converts text into speech and plays it out loud on the speaker automatically.
5. **SOS Emergency Siren:** Long-press SOS triggers a high-priority `STREAM_ALARM` siren, vibration waveform, and flashlight strobe across all paired endpoints.

---

## Tech Stack

| Layer | Technology Choice | Rationale / Details |
|---|---|---|
| **Language** | Kotlin | Coroutines & Flow for asynchronous callback handling |
| **UI** | Jetpack Compose (Material 3) | Emergency dark theme (`#0A0A0A`), Safety Orange (`#FF5A1F`), Alert Red (`#FF2E2E`) |
| **Architecture** | MVVM + Unidirectional State | `StateFlow` and `SharedFlow` reactive architecture |
| **DI** | Hilt (Dagger) | Flavor-based interface binding for `mock` and `prod` source sets |
| **P2P Transport** | Nearby Connections API | Strategy `P2P_CLUSTER` over local Wi-Fi & Bluetooth |
| **Speech-to-Text** | Android STT + Vosk Fallback | Offline speech recognition (`EXTRA_PREFER_OFFLINE = true`) |
| **Text-to-Speech** | System `TextToSpeech` engine | `QUEUE_ADD` audio playback for hands-free walkie-talkie |
| **Audio Alerts** | `AudioManager` + `Vibrator` | `STREAM_ALARM` max volume audio + waveform vibration + safe strobe |
| **Persistence** | Room Database | Local message transcript and peer history persistence |
| **Foreground Service** | `ItantraForegroundService` | Maintains radio session with screen off or backgrounded |

---

## Build Flavors & Development Loop

iTantra features two distinct build flavors configured via Gradle product flavors and Hilt DI modules:

### 1. `mock` (Default / Demo Loop)
- **Command:** `./gradlew assembleMockDebug`
- **Behavior:** Binds `MockTransport` and `MockSpeechToText`. Simulates peer discovery, handshake, network delay spikes, and canned speech responses. Ideal for emulators, CI/CD pipelines, and single-device live demonstrations.

### 2. `prod` (Physical Device Deployment)
- **Command:** `./gradlew assembleProdRelease`
- **Behavior:** Binds `NearbyTransport` (Google Nearby Connections) and `AndroidSttEngine` (system recognizer / Vosk). Connects two physical Android devices over real radios without internet.

---

## Important SOS Capability Disclaimer

iTantra plays emergency SOS alert siren audio on **`AudioManager.STREAM_ALARM`**, which is designed on Android OS to bypass standard hardware ringer mute settings. However, hardware mute switches on specific OEM Android implementations or total-silence Do Not Disturb modes may block audio unless alarm exceptions are allowed by the user.

---

## How to Run on Mobile Device

### Prerequisites
- **Android Studio:** Jellyfish / Koala / Ladybug or newer.
- **Java Development Kit (JDK):** Version 17.
- **Android Physical Device:** Android 8.0 (API 26) or higher, with Bluetooth and Wi-Fi enabled.
- **USB Cable / Wireless Debugging:** To connect your phone to your computer.

---

### Step-by-Step Installation

#### 1. Enable Developer Options & USB Debugging on Mobile
1. Open **Settings** on your Android phone.
2. Go to **About Phone** and tap **Build Number** 7 times until you see `"You are now a developer!"`.
3. Go back to **Settings > System > Developer options**.
4. Enable **USB debugging** (and **Install via USB** if present on MIUI/ColorOS/OxygenOS).

#### 2. Connect Your Phone to Computer
- Plug in your phone via USB.
- On your phone, tap **Allow USB Debugging** when prompted (check *"Always allow from this computer"*).
- Verify connection in your terminal:
  ```bash
  adb devices
  ```
  *(Your device ID should appear in the list).*

#### 3. Build & Install via Command Line
Run one of the following commands in the project root:

- **For Physical P2P Peer-to-Peer Deployment (`prod` flavor):**
  ```bash
  # Option A: Build and install directly to connected phone/emulator
  ./gradlew installProdDebug

  # Option B: Generate standalone APK file (no device required)
  ./gradlew assembleProdDebug
  # APK location: app/build/outputs/apk/prod/debug/app-prod-debug.apk
  ```
- **For Single-Device / Simulator Testing (`mock` flavor):**
  ```bash
  # Option A: Build and install directly to connected phone/emulator
  ./gradlew installMockDebug

  # Option B: Generate standalone APK file (no device required)
  ./gradlew assembleMockDebug
  # APK location: app/build/outputs/apk/mock/debug/app-mock-debug.apk
  ```

> [!TIP]
> **Troubleshooting `DeviceException: No connected devices!`**
> If you get `No connected devices!` when running `installProdDebug` or `installMockDebug`, it means no phone/emulator is currently connected via USB/WiFi debugging.
> - **To install to a phone**: Plug in your phone via USB, enable **USB Debugging** in Developer Options, and re-run `installProdDebug` or `installMockDebug`.
> - **To get the APK without connecting a phone**: Run `assembleProdDebug` or `assembleMockDebug`. The resulting `.apk` file will be created in `app/build/outputs/apk/prod/debug/` or `app/build/outputs/apk/mock/debug/`. You can send this file to your phone and install it manually!


#### 4. Build & Install via Android Studio
1. Open the project folder in **Android Studio**.
2. Select **Build Variants** tab on the left margin.
3. Select `prodDebug` (for real physical hardware radios) or `mockDebug` (for simulated testing).
4. Select your connected device from the top target device dropdown menu.
5. Click the green **Run** button (or press `Shift + F10`).

#### 5. Grant Permissions on First Launch
Upon launching iTantra on your phone:
1. Tap **Grant Permissions**.
2. Allow permissions for **Nearby Devices**, **Location** (required by Android OS for Bluetooth/Wi-Fi Direct discovery), **Microphone** (for voice recording), and **Notifications** (for background P2P service).

---

## How to Use iTantra

### 1. Connecting Peers
- **Prod Mode (Two Physical Phones):** Open iTantra on both physical phones with Bluetooth & Wi-Fi turned on (no SIM or internet needed). The app will automatically discover and form a `P2P_CLUSTER` mesh connection.
- **Mock Mode (Single Phone / Demo):** Open the app in `mock` flavor. The built-in simulator automatically spawns canned peer handshakes for testing without needing a second device.

### 2. Push-to-Talk (PTT) Messaging
1. Go to the **Talk** tab.
2. **Press and Hold** the large circular Mic button while speaking your message.
3. Release the button when finished.
4. The app instantly transcribes your voice to text offline (via Android STT / Vosk) and broadcasts the payload to all connected peers over the P2P radio.

### 3. Hands-Free Audio Playback
- When an incoming message is received from a peer, the recipient's phone automatically converts the text payload back to speech via on-device **Text-to-Speech (TTS)** and plays it out loud.
- Full transcript history is saved to the local **Room Database**.

### 4. Triggering Emergency SOS Siren
1. Tap the **SOS** button on the bottom bar or top header.
2. Tap **Confirm Emergency Siren** (or long-press the SOS trigger).
3. The app triggers a high-decibel siren on `STREAM_ALARM` max volume, strobe flashlight pulses, and heavy vibration across all connected devices in the mesh network.
4. Tap **Stop Siren** to deactivate.

---

## How to Push Changes to the Repository

Follow these steps to stage, commit, and push updates to the remote GitHub repository (`https://github.com/Kkushak16/itantra.git`):

### 1. Verify Remote Setup
Ensure your local workspace points to the correct GitHub repository:
```bash
git remote -v
```
If `origin` is missing or incorrect, set it using:
```bash
git remote add origin https://github.com/Kkushak16/itantra.git
```

### 2. Check Repository Status
See changed, created, or deleted files:
```bash
git status
```

### 3. Stage & Commit Changes
Stage all modified and new files:
```bash
git add -A
```

Create a descriptive commit message following conventional commit guidelines:
```bash
git commit -m "feat: add mobile installation guide and git workflow to README"
```

### 4. Push to Remote Branch
Push your commit to the `main` branch on GitHub:
```bash
git push origin main
```

If working on a feature branch (e.g., `feature/p2p-optimization`):
```bash
git checkout -b feature/p2p-optimization
git push -u origin feature/p2p-optimization
```

---

## How to Run the Demo

For a step-by-step 2-minute demonstration script for judges or first responders, see [DEMO_SCRIPT.md](./DEMO_SCRIPT.md).
