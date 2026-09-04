# How to Use iTantra (Detailed User Guide)

Welcome to iTantra! This document is a comprehensive guide to understanding and operating the offline emergency walkie-talkie app.

## 1. First Launch & Setup
When you first open iTantra, you will be greeted by the custom `iT` application icon. 

### Automatic Permissions
Unlike traditional apps where you must dig through Settings, iTantra automatically detects your Android version and requests exactly what it needs via native system popups.
- **Microphone**: Essential for recording your voice for the STT engine.
- **Nearby Devices (Bluetooth/Wi-Fi)**: Essential for the Google Nearby Connections API to locate other phones without internet.
- **Location**: Required strictly by the Android OS for Wi-Fi Direct scanning.
- **Notifications**: Keeps the peer-to-peer radio connection alive even when your phone screen is locked.

## 2. Peer-to-Peer Networking
iTantra uses a `P2P_CLUSTER` network topology. This means it creates a localized mesh network between devices.

### To Connect Two Devices:
1. Ensure both devices have **Bluetooth** and **Wi-Fi** turned ON. (You do NOT need to be connected to a Wi-Fi router; the hardware radios just need to be enabled).
2. On the first phone, tap **Host**. It will begin broadcasting a secure, invisible signal.
3. On the second phone, tap **Scan**. It will listen for the Host's signal.
4. Once they shake hands, you will automatically be dropped into the **Talk** screen.

## 3. The Offline AI Engine (STT & TTS)
iTantra is massive (~333MB) because it bundles real AI models inside the app itself, guaranteeing it works in total dead zones.

### The "Lazy Load" Mechanics (And the 1-Second Freeze)
The models (OpenAI's Whisper for Speech-to-Text, and Piper VITS for Text-to-Speech) are about 165MB. When you open the app, they sit quietly in your phone's storage. 
- **The First Use:** The very first time you tap the Microphone button, the app will freeze for about 1 to 2 seconds. 
- **Why?** This is an intentional engineering mechanism called "Lazy Initialization". We move the 165MB AI models into RAM safely on the main thread. If we did this in the background, a native bug in the AI engine (ONNX Runtime) would corrupt Android's user interface and crash the app.
- After this initial 1-second freeze, the models are securely in memory, and all subsequent voice translations will happen instantly!

### Bilingual Support
The TTS engine automatically detects the language of incoming messages. If a peer sends a message in Devanagari script, the app dynamically switches from the English voice (`amy`) to the Hindi voice (`priyamvada`) to read it out loud.

## 4. Emergency SOS
The SOS feature is designed for absolute emergencies.
1. Tap the **SOS** button in the header or bottom bar.
2. Tap **Confirm** (or long-press the SOS button).
3. A critical payload is blasted across the mesh network to all connected peers.
4. It commands their phones to override silent switches, flash their camera strobes, vibrate violently, and play a max-volume siren (`STREAM_ALARM`).
5. Tap **Stop Siren** to cease the alert.

---
*For development information and build instructions, see the main [README.md](./README.md).*
