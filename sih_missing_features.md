# SIH20173 Gap Analysis & Missing Features

This document outlines the specific missing features and gaps in iTantra when evaluated strictly against the **SIH20173** problem statement. Implementing these features is required to achieve a 100% compliance score for the hackathon submission.

## 1. The 10-Language Requirement (Critical Gap)
**SIH Requirement:** *"...STT and TTS models for 10 Indian Languages (Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English)"*

**Current State:** 
iTantra currently only bundles and routes 2 languages: English (`en_US-amy`) and Hindi (`hi_IN-priyamvada`).

**Required Actions:**
1. **TTS (Text-to-Speech):** Download the Piper VITS ONNX models for the missing 8 languages (Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali) from the Sherpa-ONNX model repository.
2. **STT (Speech-to-Text):** Ensure the Whisper Tiny INT8 model being used is the **multilingual** variant, not the English-only (`tiny.en`) variant. 
3. **Storage/APK Size:** Bundle all 10 TTS models into the `assets/models/tts/` directory. Accept the APK size increase (likely +150MB), as compliance with the language metric is mandatory.

## 2. Language Selection UI
**SIH Requirement:** System must cater to the 10 languages dynamically.

**Current State:**
The app currently relies on a hardcoded heuristic (checking for Devanagari characters) to switch between English and Hindi TTS.

**Required Actions:**
1. Build a Language Selection Dropdown in the App Settings.
2. Store the user's selected language in `SharedPreferences` or `DataStore`.
3. Update `SherpaTtsEngine.kt` to load and use the TTS model corresponding to the user's selected language preference.
4. Update `SherpaSttEngine.kt` to pass the correct language code to the Whisper model configuration.

## 3. Pause Detection vs. Push-To-Talk (VAD Integration)
**SIH Requirement:** *"The system’s STT module when activated after detecting pauses and stoppages should form the sentences detected..."* AND *"it should work like a walkie talkie using push to talk feature"*

**Current State:** 
iTantra fully satisfies the Push-To-Talk (PTT) requirement. However, it lacks continuous listening with Voice Activity Detection (VAD) "pause detection."

**Required Actions:**
1. Integrate Silero VAD (Voice Activity Detection) or use Sherpa-ONNX's built-in endpointing.
2. Create an optional "Hands-Free / Continuous Mode" toggle in the UI. When active, the app streams audio, detects a pause (silence), automatically stops listening, and transmits the payload without requiring a button press.

## 4. Latency & RTF Benchmarking (For Evaluation)
**SIH Requirement:** *"Latency: The Time delay between the Words said and STT completion... along with RTF (Real Time Factor)."*

**Current State:**
The app performs quickly, but there are no visible metrics for the judges to evaluate.

**Required Actions:**
1. Add internal timestamp logging.
   - Timestamp A: PTT button released.
   - Timestamp B: STT result generated.
   - Timestamp C: JSON payload transmitted over Nearby Connections.
   - Timestamp D: Payload received by peer.
   - Timestamp E: TTS audio generation completed.
2. Calculate RTF: `(STT Processing Time) / (Duration of Audio Recorded)`.
3. Create a hidden "Debug/Metrics Overlay" in the UI that displays these MS timings and RTF so judges can easily verify the 20% Evaluation Metric for Latency.
