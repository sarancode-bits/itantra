# iTantra — 2-Minute Live Demo Script

This script allows anyone (a judge, first responder, or technical evaluator) to demonstrate **iTantra** end-to-end in under 2 minutes, using **mock mode on a single device or emulator** (no second phone or real radios required).

---

## 1. Single-Device Mock Mode Demo (Recommended for Presentations)

### Step 1: Cold Launch & Theme (0:00 – 0:20)
1. Launch the app (`assembleMockDebug` build).
2. Point out the high-contrast **dark emergency design system**:
   - Safety Orange accents (`#FF5A1F`) and Alert Red buttons (`#FF2E2E`).
   - "MOCK MODE" badge visible at the top right, confirming deterministic offline testing.

### Step 2: Host & Scan Peer Discovery (0:20 – 0:50)
1. Tap the **"Scan"** button on the Home screen.
2. Watch the status transition to *Scanning...* for ~1.5 seconds.
3. Observe **"Ravi's Emergency Device"** appear in the Discovered Peers list with live RSSI indicator.
4. Tap **"CONNECT"**.
5. Observe the high-contrast progress screen (*Connecting to Ravi's Emergency Device...*) followed by automatic transition to the **Talk** screen.
6. Note the top status bar: **Connected to Ravi's Emergency Device** with peer battery indicator.

### Step 3: PTT Voice Exchange & Walkie-Talkie Loop (0:50 – 1:30)
1. **Hold down the large central Mic button** ("HOLD MIC TO TALK").
2. Speak a phrase into the device or let mock STT complete. Notice the button pulse and live audio RMS wave animation.
3. **Release the button**.
4. The transcript displays your message with a `Sending...` status indicator, updating to `Sent` and `Delivered`.
5. Simultaneously, notice the TTS engine automatically playing the voice response out loud.
6. After ~2 seconds, a simulated incoming reply from Ravi ("Copy that, I'm holding at Sector B") appears in the transcript and plays audibly.

### Step 4: Emergency SOS Trigger & Dismissal (1:30 – 2:00)
1. Tap the **"SOS"** button in the top right header bar.
2. Notice the safety guard: simple tap does not trigger SOS.
3. **Press and hold the red SOS button for 1.5 seconds**. Watch the circular progress ring fill.
4. The screen transitions to the full-screen **EMERGENCY SOS ACTIVE** takeover screen.
5. The device triggers `STREAM_ALARM` audio, emergency vibration waveform, and flashlight strobe.
6. Tap **"DISMISS / CANCEL SOS"**. The siren and vibration stop instantly, returning to the Talk screen.

---

## 2. Two-Device Real Radio Demo (Production Build)

1. Install `assembleProdDebug` or `assembleProdRelease` on two physical Android devices.
2. Ensure Wi-Fi and Bluetooth radios are ON on both devices (no internet connection required).
3. Device A: Tap **HOST**.
4. Device B: Tap **SCAN**.
5. Device B sees Device A in the discovered list and taps **CONNECT**.
6. Hold mic on Device A, speak. Device B transcribes text on-device, transmits over Nearby Connections, and plays TTS audibly out loud.
7. Tap SOS on Device A -> Device B immediately triggers full-screen SOS siren overlay.
