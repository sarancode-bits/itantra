# iTantra — Offline Emergency Peer-to-Peer Communicator

**Platform:** Android (Kotlin, Native)  
**Architecture:** MVVM + Clean Architecture + Hilt + Room + Jetpack Compose  
**Primary Loop:** Off-grid voice speech-to-text transmission over local P2P radios with on-device text-to-speech audio playback.

---

## Product Overview

iTantra is a **fully offline**, phone-to-phone emergency walkie-talkie. Two nearby Android devices — without internet, SIM cards, or Wi-Fi routers — discover each other over Wi-Fi Direct and Bluetooth radios, pair, and exchange short transcribed voice messages. 

It is completely self-contained. The ~165MB APK includes powerful AI models for **Speech-to-Text (STT)** and **Text-to-Speech (TTS)** built directly into the app, meaning it never needs an internet connection to process voice data.

### Key Features
1. **Automatic Permissions:** The app automatically requests all required permissions natively on launch.
2. **On-device STT (Whisper):** Converts speech into light text packets using an embedded Whisper int8 ONNX model. Supports multilingual transcription out-of-the-box.
3. **P2P Transport:** Transmits serialized JSON payloads over Google Nearby Connections API (`P2P_CLUSTER`).
4. **10-Language On-device TTS (Piper VITS):** Receiving device converts text into speech using Piper models and plays it out loud automatically. Fully supports English, Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, and Bengali.
5. **SOS Emergency Siren:** Long-press SOS triggers a high-priority `STREAM_ALARM` siren, vibration waveform, and flashlight strobe across all paired endpoints.
6. **Hands-Free (VAD) Mode:** An energy-based silence detection mode allows continuous, hands-free operation without needing to hold the PTT button.
7. **SIH Latency Benchmarking:** A built-in developer overlay that measures real-time STT/network latency and calculates the Real Time Factor (RTF) of voice transcription, perfect for evaluation.

---

## Tech Stack & Recent Engineering Fixes

| Layer | Technology Choice | Details & Optimizations |
|---|---|---|
| **Language** | Kotlin | Coroutines & Flow for asynchronous callback handling |
| **UI** | Jetpack Compose | Features a custom `iT` application icon, Dark theme, and Safety Orange accents |
| **Architecture** | MVVM + Unidirectional State | `StateFlow` and `SharedFlow` reactive architecture |
| **P2P Transport** | Nearby Connections API | Strategy `P2P_CLUSTER` over local Wi-Fi & Bluetooth |
| **Speech-to-Text** | Sherpa ONNX (Whisper) | STT models are now loaded explicitly during a static Splash Screen, and run in a fully isolated `:ai_engine` background process via AIDL to guarantee UI thread stability. Includes energy-based VAD for continuous hands-free listening. |
| **Text-to-Speech** | Sherpa ONNX (Piper VITS) | Dynamically loads TTS models based on the selected language setting. Supports 10 Indian regional languages for fully offline text-to-speech. Also runs in the isolated `:ai_engine` process. |
| **Persistence** | Room Database | Local message transcript, latency metrics, and peer history persistence |

---

## Build Flavors

iTantra features two distinct build flavors configured via Gradle product flavors and Hilt DI modules:

### 1. `mock` (Default / Demo Loop)
- **Command:** `./gradlew assembleMockDebug`
- **Behavior:** Binds `MockTransport` and `MockSpeechToText`. Simulates peer discovery and canned speech responses. Ideal for emulators or single-device live demonstrations.

### 2. `prod` (Physical Device Deployment)
- **Command:** `./gradlew assembleProdRelease`
- **Behavior:** Binds `NearbyTransport` (Google Nearby Connections) and `SherpaSttEngine`. Connects two physical Android devices over real radios without internet and uses the real AI models.

---

## How to Run on Mobile Device

### Prerequisites
- **Android Physical Device:** Android 8.0 (API 26) or higher, with Bluetooth and Wi-Fi enabled.
- **USB Cable / Wireless Debugging:** To connect your phone to your computer.

### Step-by-Step Installation

#### 1. Enable Developer Options & USB Debugging on Mobile
1. Go to **Settings > About Phone** and tap **Build Number** 7 times.
2. Go to **Settings > System > Developer options**.
3. Enable **USB debugging**.

#### 2. Connect & Build
1. Plug in your phone via USB and tap **Allow USB Debugging**.
2. Run the following command to build and install the real P2P version:
   ```bash
   ./gradlew installProdDebug
   ```
   *(Or just run `android run` if using Antigravity).*

#### 3. Launch & Permissions
Upon launching iTantra on your phone, you will see the new `iT` app icon. The app will immediately and **automatically prompt you via native Android dialogs** to grant the following permissions:
- **Nearby Devices / Bluetooth:** To find other phones.
- **Location:** Required by Android for Wi-Fi Direct scanning.
- **Microphone:** To record your voice.
- **Notifications:** To keep the P2P radio alive in the background.

---

## How to Use iTantra (Quick Start)

### 1. Connecting Peers
- Open iTantra on both physical phones with Bluetooth & Wi-Fi turned on.
- On Phone A, tap **Host**.
- On Phone B, tap **Scan**.
- They will automatically connect and drop you into the Talk screen.

### 2. Startup & AI Model Loading
- The embedded AI models (Whisper and Piper) are quite large (~165MB) and are bundled inside the APK.
- **IMPORTANT UX NOTE:** On app launch, you will see a static **Splash Screen** with a progress bar. This safely loads the AI models into memory while the UI is dormant, avoiding C++ threading conflicts.
- Once loaded, the models run in a completely isolated background process (`:ai_engine`). This means even if the AI engine crashes, the main walkie-talkie UI and SOS features remain perfectly alive!

### 3. Push-to-Talk (PTT) Messaging
- **Press and Hold** the large circular Mic button. Speak your message.
- Release the button. It transcribes instantly offline and sends it to your peer.
- The peer receives it and reads it out loud automatically using the offline TTS voice!

### 4. Triggering Emergency SOS Siren
- Tap the **SOS** button on the bottom bar.
- Tap **Confirm Emergency Siren**. This will override silent mode and blast a siren on all connected phones.
