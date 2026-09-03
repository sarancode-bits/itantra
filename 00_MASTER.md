# iTantra — Master Build Document

**Repo:** https://github.com/sarancode-bits/itantra
**Platform:** Android (Kotlin, native)
**Type:** Offline peer-to-peer emergency walkie-talkie

This is the master reference for building iTantra. Every phase file (`PHASE_0` … `PHASE_7`) is a self-contained prompt you feed to a coding assistant (Claude Code / Android Studio Gemini / Cursor) **in order**. This file is the constitution all of them must obey — paste it as context (or keep it in the repo as `ARCHITECTURE.md`) before running any phase prompt.

---

## 1. Product Summary

iTantra is a **fully offline**, phone-to-phone emergency communicator. Two nearby Android phones — no internet, no SIM, no router required — discover each other over Wi-Fi/Bluetooth radios, pair, and exchange short voice messages:

1. Person A holds the mic button and speaks.
2. Phone A converts speech → text **on-device**.
3. The text (a few bytes) is sent over the local P2P link.
4. Phone B converts text → speech **on-device** and plays it out loud.
5. Either phone can fire an **SOS** broadcast: a loud alarm-stream tone + strong vibration pattern that overrides silent/DND, sent to all connected peers simultaneously.

Text is the wire format, not raw audio — this is deliberate: it survives flaky links, costs near-zero bandwidth, is trivially logged for a rescue timeline, and degrades gracefully (if TTS fails, the receiver still sees the text).

**Non-negotiables:**
- Must work with **zero internet, zero cellular, zero cloud calls** at runtime.
- Must have a **mock mode** that runs the full pipeline (discovery → pairing → STT → transport → TTS → SOS) with fake data when real radios/models aren't available — so it's demoable on an emulator or in a bad-signal room.
- Must feel **fast and reliable** over "feature-complete." A judge/rescuer tapping the mic must see something happen within ~200ms.

---

## 2. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | **Kotlin** | standard, coroutines-first |
| UI | **Jetpack Compose** (Material 3, custom dark theme) | fast iteration, easy to make a distinctive emergency look |
| Architecture | **MVVM** + unidirectional state (`StateFlow`) | testable, easy to mock at the ViewModel boundary |
| DI | **Hilt** | swap real/mock implementations by build variant, not by if-else |
| Async | **Kotlin Coroutines + Flow** | radios and speech APIs are all callback-based; wrap once, use everywhere |
| P2P transport | **Nearby Connections API** (`com.google.android.gms:play-services-nearby`), strategy `P2P_CLUSTER` | Google's supported abstraction over Wi-Fi Direct / Wi-Fi hotspot / Bluetooth — handles the "local hotspot or Wi-Fi, no internet" requirement without hand-rolling Wi-Fi Direct. Nearby **Messages** is deprecated (removed end of 2023) — do not use it. Nearby **Connections** is the current, supported API for this. |
| Speech-to-text | **Android `SpeechRecognizer`** with `RecognizerIntent.EXTRA_PREFER_OFFLINE`, on-device recognition where available (Android 13+ `isOnDeviceRecognitionAvailable`) | ships with the OS, no extra model download UX to build first |
| STT fallback | **Vosk Android** (offline, on-device, small footprint models, Apache-2.0) | guarantees offline STT on devices/emulators where the system recognizer needs connectivity or has no offline pack installed; also what **mock mode** and CI/demo builds default to for determinism |
| Text-to-speech | **Android `TextToSpeech` engine** (system, offline voices) | ships with OS, reliable, no model management needed |
| Audio alerts | **`AudioManager` on `STREAM_ALARM`** + `Vibrator`/`VibrationEffect` + optional camera flashlight strobe | `STREAM_ALARM` is the one stream Do Not Disturb/silent profiles are built to still allow through when the user explicitly permits alarms — this is how the SOS "plays loudly even if the phone is silent" requirement is met honestly (see Phase 5 for the exact caveats — there is no API to force audio through a phone the user has fully muted; document what iTantra can and can't guarantee) |
| Background reliability | **Foreground `Service`** (`connectedDevice` / `dataSync` type) holding the Nearby connection + audio session while the app is backgrounded | connections and SOS must survive the screen locking |
| Persistence | **Room** (message log, paired-device history) | offline-first, no cloud sync needed |
| Testing | JUnit + Turbine (Flow testing) + Compose UI tests, all runnable against the **mock** transport/speech implementations | CI never touches real radios or real speech models |

**Explicitly out of scope / not used:** Nearby Messages (deprecated), any cloud STT/TTS API (Google Cloud Speech, Azure, etc. — violates the offline requirement), Firebase, any networking library that assumes internet (Retrofit/OkHttp for app data — Nearby's own payload transport replaces this).

**Known platform change to plan for:** Google announced that starting **late 2026**, Nearby Connections will **no longer auto-toggle Wi-Fi/Bluetooth radios on** for the app — the app must check radio state itself and prompt the user to enable it manually before advertising/discovering. Build the radio-state check as its own component from Phase 2 onward rather than relying on the API to do it silently, so the app already behaves correctly.

---

## 3. Module / File Structure

```
itantra/
├── app/
│   ├── src/main/java/com/itantra/
│   │   ├── ItantraApp.kt                  (Application, Hilt entry)
│   │   ├── di/                            (Hilt modules: TransportModule, SpeechModule, AlertModule)
│   │   ├── core/
│   │   │   ├── transport/
│   │   │   │   ├── P2pTransport.kt        (interface: advertise/discover/connect/send/receive/disconnect)
│   │   │   │   ├── NearbyTransport.kt     (real impl, wraps ConnectionsClient)
│   │   │   │   └── MockTransport.kt       (simulated peer, fake latency/loss)
│   │   │   ├── speech/
│   │   │   │   ├── SpeechToText.kt        (interface)
│   │   │   │   ├── AndroidSttEngine.kt    (SpeechRecognizer impl)
│   │   │   │   ├── VoskSttEngine.kt       (Vosk impl)
│   │   │   │   ├── TextToSpeech.kt        (interface)
│   │   │   │   ├── AndroidTtsEngine.kt    (TextToSpeech impl)
│   │   │   │   └── MockSpeechEngine.kt    (canned STT/TTS for demo/tests)
│   │   │   ├── alert/
│   │   │   │   └── SosAlertPlayer.kt      (STREAM_ALARM tone + vibration pattern + flashlight)
│   │   │   ├── radio/
│   │   │   │   └── RadioStateMonitor.kt   (Wi-Fi/BT on-off state, prompts to enable)
│   │   │   └── protocol/
│   │   │       └── Messages.kt            (sealed class wire protocol: Voice, Sos, Ack, Presence)
│   │   ├── data/
│   │   │   ├── db/                        (Room: MessageEntity, PeerEntity, daos)
│   │   │   └── repository/
│   │   │       └── SessionRepository.kt   (single source of truth: connection + message state)
│   │   ├── service/
│   │   │   └── ItantraForegroundService.kt
│   │   └── ui/
│   │       ├── theme/                     (Color.kt, Type.kt, Theme.kt — emergency dark theme)
│   │       ├── home/                      (Host / Scan / Pair screen)
│   │       ├── talk/                      (main walkie-talkie screen: mic, transcript, status)
│   │       ├── sos/                       (SOS confirm + active-alert screen)
│   │       └── settings/                  (mock mode toggle, device name, permissions)
│   ├── src/mock/                          (build variant: MockTransport + MockSpeechEngine wired by default)
│   └── src/prod/                          (build variant: real Nearby + real speech engines)
```

Two build variants (**mock**, **prod**) selected via Hilt module swap + `productFlavors` in Gradle — this is how "mock mode" is implemented, not a runtime toggle buried in code. (A runtime debug toggle in Settings is added in Phase 6 for convenience, but the flavor split is what guarantees mock mode always builds and runs even if real dependencies are broken.)

---

## 4. Wire Protocol (what actually goes over Nearby's payload channel)

Keep it a tiny sealed, JSON- or Protobuf-encoded packet — text only, no audio blobs, so it works over a marginal link:

```kotlin
sealed class ItantraMessage {
    data class Voice(val id: String, val senderName: String, val text: String, val timestampMs: Long) : ItantraMessage()
    data class Sos(val id: String, val senderName: String, val note: String?, val timestampMs: Long) : ItantraMessage()
    data class Ack(val forId: String) : ItantraMessage()
    data class Presence(val deviceName: String, val batteryPct: Int?) : ItantraMessage()
}
```

`Voice` is the STT output text, not audio. `Sos` is broadcast to every connected endpoint and triggers `SosAlertPlayer` on receipt regardless of what screen the receiving app is on. `Ack` lets the sender's UI show delivered/undelivered. `Presence` is a light heartbeat for the connection-status UI.

---

## 5. Design System (dark, high-contrast, emergency)

- **Palette:** near-black background (`#0A0A0A` / `#121212`), one alarm accent — **safety orange `#FF5A1F`** or **alert red `#FF2E2E`** — used *only* for the mic button, SOS button, and critical status; everything else stays greyscale so the accent always reads as "this matters." White/`#F5F5F5` text at high contrast (WCAG AAA where feasible — this app must be legible in panic, low light, shaking hands).
- **Typography:** one bold, geometric sans (e.g. `Inter`/`Roboto Flex`), large touch targets, big numerals for status/countdowns.
- **Mic button:** the single largest element on the Talk screen — a full-width or large circular press-and-hold target, not a small icon. Visibly pulses while recording, visibly shows "sending / delivered / failed."
- **SOS:** a separate, unmistakable control (long-press to arm, to avoid accidental triggers) with its own red state that takes over the screen while active — big "SOS ACTIVE — broadcasting to N peers" with a cancel.
- **Connection status:** always visible, plain language, three states minimum — *Searching…*, *Connected to <name>*, *Disconnected* — with a colored dot, never just an icon.
- No decorative animation that costs latency; motion is used only to communicate state (recording, sending, connected).

Do not use a generic Material blue/purple default theme anywhere — every screen must read as "emergency tool," not "generic chat app."

---

## 6. How To Use the Phase Files

Run phases **in order**; each assumes the previous phase's code exists and compiles. Each phase file is self-contained: paste it plus this master file into your coding agent. Each ends with an explicit **Acceptance Criteria** checklist — do not proceed to the next phase until those pass, in **mock mode**, on a real device or emulator.

| Phase | File | Delivers |
|---|---|---|
| 0 | `PHASE_0_SETUP.md` | Repo skeleton, Gradle, flavors, Hilt wiring, theme stub, CI-friendly mock variant that boots |
| 1 | `PHASE_1_UI_SHELL.md` | All screens as static UI (dark emergency theme), navigation, no real logic yet |
| 2 | `PHASE_2_P2P_CONNECTIVITY.md` | `P2pTransport` interface + `NearbyTransport` + `MockTransport`, host/scan/pair working end to end |
| 3 | `PHASE_3_SPEECH_PIPELINE.md` | STT + TTS interfaces, Android + Vosk + Mock implementations, mic → text → speech loop working locally |
| 4 | `PHASE_4_MESSAGING.md` | Wire protocol wired through transport, transcript UI, message log (Room), delivery status |
| 5 | `PHASE_5_SOS_ALERTS.md` | SOS broadcast, alarm-stream audio, vibration pattern, flashlight, full-screen alert UI |
| 6 | `PHASE_6_RELIABILITY.md` | Foreground service, runtime permissions flow, radio-enable prompts, reconnect logic, error states |
| 7 | `PHASE_7_POLISH_DEMO.md` | Animations/micro-interactions, empty/error states, demo script, README, final QA pass |

---

## 7. Rules Every Phase Must Follow

1. **Never let the mock variant depend on network, real radios, or real speech models.** If a phase adds a real capability, add its mock twin in the same phase.
2. **Compile and run after every phase** — a phase isn't done if the app doesn't launch in mock mode.
3. **All permissions are requested with a plain-language rationale screen first**, then the system dialog — never a bare permission popup on launch.
4. **No blocking calls on the main thread** — radios, speech engines, and DB access are all suspend functions or Flows.
5. **State lives in one place per screen** (a `StateFlow<UiState>` in the ViewModel) — Composables are dumb renderers of that state.
6. **Every user-visible failure has a message a scared, non-technical person can act on** ("Turn on Wi-Fi to connect" — not "ERROR: ADAPTER_DISABLED").
7. **Keep commits/PRs scoped to one phase** so the repo history mirrors this document.
