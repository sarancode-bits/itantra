# PHASE 3 — Speech-to-Text / Text-to-Speech Pipeline

Paste with `00_MASTER.md`. Builds on Phase 2. Deliver a **working local loop**: hold mic → speak → see transcribed text → (separately) feed text in → hear it spoken — all testable on a single device before any two-phone transport is involved.

## Task

### 1. `core/speech/SpeechToText.kt` — interface
```kotlin
interface SpeechToText {
    val state: StateFlow<SttState>     // Idle, Listening, Processing, Result(text), Error(message)
    fun startListening()
    fun stopListening()
}
```

### 2. `core/speech/MockSpeechEngine.kt`
- `MockSpeechToText`: on `startListening()`, wait a short realistic delay, then emit `Result` with a **rotating set of canned emergency phrases** ("Help, I'm at the north gate", "We're okay, moving to shelter", "Need water, two people injured", etc.) — enough variety that a demo doesn't look scripted-identical every time.
- `MockTextToSpeech`: on `speak(text)`, just log/emit a "spoke: <text>" state change after a short delay — no real audio required, but expose enough state (`SpeakingState.Speaking` / `Idle`) that the UI can show a real "speaking…" indicator.
- Both must be swappable via the same interfaces the real engines use.

### 3. `core/speech/AndroidSttEngine.kt` — real, primary
- Wrap `SpeechRecognizer` (`SpeechRecognizer.createSpeechRecognizer(context)`).
- Build the recognizer `Intent` with `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`, and **`EXTRA_PREFER_OFFLINE = true`**.
- On Android 13+ (API 33), check `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` before starting; if true, prefer `createOnDeviceSpeechRecognizer` for a stronger offline guarantee.
- Map `RecognitionListener` callbacks (`onResults`, `onError`, `onRmsChanged` for a live mic-level indicator) into `SttState`.
- If the device has no offline recognition available at all, surface a clear `Error` state — do **not** silently fall through to a network call (that would violate the offline requirement); instead this is exactly the signal to fall back to Vosk.

### 4. `core/speech/VoskSttEngine.kt` — real, offline-guaranteed fallback
- Bundle a small Vosk offline model (English small model, a few dozen MB) as an app asset or first-run download-to-local-storage step — **first-run download must be clearly optional/skippable and the app must still function in mock mode without it.**
- Implement the same `SpeechToText` interface using Vosk's streaming recognizer over the mic `AudioRecord` stream.
- This is what CI/instrumented tests exercise instead of the system recognizer, since it has no dependency on device-specific offline language packs.

### 5. `core/speech/AndroidTtsEngine.kt` — real
- Wrap `android.speech.tts.TextToSpeech`, initialized once and reused (not re-created per message — that's slow).
- Check `isLanguageAvailable`; if the needed voice data isn't installed, surface a clear state so the UI can point the user at Android's offline TTS data settings, rather than failing silently.
- Queue mode `QUEUE_ADD` so rapid incoming messages don't cut each other off mid-sentence.

### 6. Wire into Talk screen (still single-device at this phase — no transport yet)
- Press-and-hold mic → `startListening()`; visualize `Listening`/`Processing` states (use `onRmsChanged`/mock equivalent to animate the mic button, not a static spinner).
- On `Result(text)`, append it as a local "You" bubble in the transcript **and** immediately call `TextToSpeech.speak(text)` so a single device can be fully tested end-to-end before Phase 4 adds the second phone.
- Errors map to the same plain-language rule as transport errors.

## Acceptance Criteria
- [ ] Mock flavor: holding the mic produces a rotating canned phrase as a transcript bubble within ~1s of release, and it's "spoken" (state indicator shows Speaking) without any real audio permission needed.
- [ ] Prod flavor, real device: holding the mic and speaking produces an accurate-enough transcript with `EXTRA_PREFER_OFFLINE`, confirmed by testing with Wi-Fi/mobile data both **off**.
- [ ] If on-device STT is unavailable, the app falls back to Vosk without the user having to do anything manual.
- [ ] TTS speaks incoming/self text audibly on speaker, queues rather than clips overlapping messages.
- [ ] `RECORD_AUDIO` permission is properly checked before `startListening()` is ever called (coordinate with Phase 6's permission flow — for now, a simple check-and-request is acceptable).
