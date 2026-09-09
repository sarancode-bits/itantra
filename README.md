# iTantra — Offline Emergency Peer-to-Peer Communicator

**Platform:** Android (Kotlin, Native)  
**Architecture:** MVVM + Clean Architecture + Hilt + Room + Jetpack Compose  
**Primary Loop:** Off-grid voice speech-to-text transmission over local P2P radios with on-device text-to-speech audio playback.

![iTantra Banner/Screenshot Placeholder](#) *(Add screenshot here)*

---

## 📖 Product Overview

**iTantra** is a fully offline, phone-to-phone emergency walkie-talkie. Two nearby Android devices — without internet, SIM cards, or Wi-Fi routers — discover each other over Wi-Fi Direct and Bluetooth radios, pair, and exchange short transcribed voice messages. 

It is completely self-contained. The APK includes powerful AI models for **Speech-to-Text (STT)** and **Text-to-Speech (TTS)** built directly into the app, meaning it never needs an internet connection to process voice data. By converting heavy PCM audio into tiny JSON text packets, iTantra guarantees communication over extremely low-bandwidth, congested, or unstable ad-hoc connections.

### 🌟 Key Features
1. **100% Offline AI Engines:** Uses OpenAI's Whisper (INT8) for transcription and Piper VITS for speech synthesis directly on-device.
2. **10-Language Support:** Seamlessly supports English, Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, and Bengali.
3. **P2P Transport:** Transmits JSON payloads over Google Nearby Connections API (`P2P_CLUSTER` topology) using Bluetooth and Wi-Fi Direct.
4. **Hands-Free Walkie-Talkie (VAD):** An energy-based Voice Activity Detection (VAD) mode allows continuous, hands-free operation without holding the PTT button.
5. **Tactical SOS Override:** A critical broadcast that forces connected phones to override silent switches, flash camera strobes, vibrate violently, and play a max-volume siren (`STREAM_ALARM`).
6. **Isolated Process Architecture:** AI models run in an isolated `:ai_engine` process to prevent heavy native C++ computations from blocking the main UI or communication threads.
7. **SIH Latency Benchmarking:** A built-in developer overlay measures real-time STT/network latency and calculates the Real-Time Factor (RTF).

---

## 🛠 Tech Stack

| Layer | Technology Choice | Details & Optimizations |
|---|---|---|
| **Language** | Kotlin | Coroutines & Flow for asynchronous callback handling. |
| **UI** | Jetpack Compose | Features a custom `iT` application icon and a sleek White/Orange light theme. |
| **Architecture** | MVVM + Clean Architecture | `StateFlow` and `SharedFlow` reactive unidirectional data flow. |
| **P2P Transport** | Nearby Connections API | Utilizes `Strategy.P2P_CLUSTER` to automate mDNS/BLE discovery without cell towers. |
| **Speech-to-Text** | Sherpa ONNX (Whisper) | STT models are loaded explicitly during a static Splash Screen, and run in a fully isolated `:ai_engine` background process via AIDL to guarantee UI thread stability. |
| **Text-to-Speech** | Sherpa ONNX (Piper VITS) | Dynamically loads TTS models based on the selected language setting. Fully offline text-to-speech. |
| **Persistence** | Room Database | Local message transcript, latency metrics, and peer history persistence. |

---

## ⚙️ How It Works: The AI Model Lifecycle

iTantra bundles the AI models (~165MB) directly inside the APK, guaranteeing it works in total dead zones.

1. **Cold Start (App Launched):** The models are read from storage and loaded into RAM during the initial static Splash Screen. This "Lazy Initialization" takes a couple of seconds and intentionally happens before the UI starts animating to prevent `pthread_mutex` corruption in the Android HWUI renderer.
2. **Warm Start (App Backgrounded):** If you minimize the app, the models stay securely in RAM. Returning to the app is instant, and voice translation triggers with zero-latency.

---

## 🚀 How to Use iTantra (User Guide)

### 1. First Launch & Setup
iTantra automatically detects your Android version and requests exactly what it needs via native system popups on the very first launch:
- **Microphone**: To record your voice for the STT engine.
- **Nearby Devices (Bluetooth/Wi-Fi)**: For the Nearby Connections API to locate other phones without internet.
- **Location**: Required strictly by the Android OS for Wi-Fi Direct scanning.
- **Notifications**: Keeps the peer-to-peer radio connection alive even when your phone screen is locked.

### 2. Connecting Peers (Off-Grid)
1. Ensure both devices have **Bluetooth** and **Wi-Fi** turned ON. *(No router or internet needed!)*
2. On the first phone, tap **Host**. It will begin broadcasting a secure, invisible signal.
3. On the second phone, tap **Scan**. It will listen for the Host's signal.
4. Once they shake hands, you will automatically enter the **Talk** screen.

### 3. Messaging
- **Push-to-Talk:** Press and Hold the large Mic button. Speak your message. Release the button to instantly transcribe and transmit it.
- **Hands-Free Mode:** Toggle "Hands-Free (VAD)" on the Talk screen. Just speak naturally. The app detects pauses in your speech and automatically transmits complete sentences.
- **Playback:** The receiving peer receives the text packet and reads it out loud automatically using their currently selected language TTS voice.

### 4. Emergency SOS
The SOS feature is designed for absolute emergencies.
1. Tap the **SOS** button in the header or bottom bar.
2. Tap **Confirm** (or long-press the SOS button).
3. A critical payload is blasted across the mesh network.
4. Connected phones will immediately trigger a high-priority alarm, vibration, and flashlight strobe.

---

## 💻 Developer Guide: Build Flavors & Deployment

iTantra features two distinct build flavors configured via Gradle product flavors and Hilt DI modules:

### 1. `mock` (Default / Demo Loop)
- **Command:** `./gradlew assembleMockDebug`
- **Behavior:** Binds `MockTransport` and `MockSpeechToText`. Simulates peer discovery and canned speech responses. Ideal for emulators or single-device live demonstrations where hardware radios aren't available.

### 2. `prod` (Physical Device Deployment)
- **Command:** `./gradlew assembleProdDebug`
- **Behavior:** Binds `NearbyTransport` and `SherpaSttEngine`. Connects two physical Android devices over real hardware radios and uses the real ONNX AI models.

### Step-by-Step Installation on Mobile
1. Go to **Settings > About Phone** and tap **Build Number** 7 times.
2. Go to **Settings > System > Developer options** and enable **USB debugging**.
3. Plug in your phone via USB and tap **Allow USB Debugging**.
4. Run the following command to build and install the real P2P version:
   ```bash
   ./gradlew installProdDebug
   ```
