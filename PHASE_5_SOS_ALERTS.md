# PHASE 5 — SOS Alerts (Loud, Vibrating, Silent-Mode-Overriding Broadcast)

Paste with `00_MASTER.md`. Builds on Phase 4. This is the highest-stakes phase — be explicit about what can and cannot actually be guaranteed on Android, and never let the UI claim a guarantee the code can't back up.

## Important honesty constraint (read first)

There is **no Android API that forces sound out of a phone the user has fully silenced via the hardware ringer switch on some OEM skins**, nor one that bypasses a user-enabled Do Not Disturb "total silence" mode without the user having pre-authorized alarm exceptions. What iTantra *can* legitimately do, and should:
- Play the SOS tone on **`AudioManager.STREAM_ALARM`**, at max volume for that stream — this is the stream that "silent mode" (ringer silent) does **not** mute on stock Android, because alarms are designed to still sound. This is the mechanism, not a workaround of it.
- Vibrate with a strong, distinctive `VibrationEffect` pattern, which works even when the ringer is silent (vibration is a separate system from ringer volume) and, where available, ignore user vibration-off settings only to the extent the OS allows for accessibility/emergency-style alerts — do not claim it always will on every OEM.
- Optionally strobe the camera flashlight as a non-audio channel.
- Show a full-screen, high-priority takeover UI + a high-priority/full-screen notification if backgrounded, so even a phone with audio truly muted still surfaces the alert visually the instant it's looked at.

Document this caveat directly in the Settings/About screen copy in Phase 7 — do not oversell "even if the phone is silent" to a user in a real emergency without the honest caveat about hardware ringer switches / total-silence DND.

## Task

### 1. `core/alert/SosAlertPlayer.kt`
```kotlin
interface SosAlertPlayer {
    val isActive: StateFlow<Boolean>
    fun start()   // begins looping alarm tone + vibration + optional strobe
    fun stop()
}
```
- Real implementation: request **audio focus** for `USAGE_ALARM`/`CONTENT_TYPE_SONIFICATION`, set the stream to `STREAM_ALARM` at (or ramped up to) max volume for that stream, loop a short, distinct, unmistakable alert tone (not a generic ringtone — a clear siren/pulse pattern, can be a small bundled asset or a generated tone).
- Vibration: a strong repeating pattern via `VibratorManager`/`VibrationEffect.createWaveform`, distinct from a normal notification buzz.
- Flashlight: toggle the camera torch (`CameraManager.setTorchMode`) in a strobe pattern, guarded by the `CAMERA`/flash-availability check — must degrade gracefully (skip silently) on devices without a flash.
- `stop()` must cleanly release audio focus, stop vibration, stop strobing — no leaked loops if the user cancels or the app is killed (tie lifecycle to the foreground service from Phase 6, not just an Activity, so `stop()` is reachable even if the UI is gone).
- Mock flavor: same interface, but skip actual audio focus/hardware calls if easier for headless testing — a debug log entry per state change is enough; a real device build (even under the `mock` flavor) should still actually play/vibrate so the SOS UX itself is demoable without needing two devices.

### 2. Wire `ItantraMessage.Sos` end to end
- Sending device: long-press-confirmed SOS (from Phase 1's `SosConfirmScreen`) triggers `SessionRepository` to broadcast an `ItantraMessage.Sos` to **every** connected peer (not just one), and immediately shows the local `SosActiveScreen`.
- Receiving device(s): on receipt of `Sos`, immediately call `SosAlertPlayer.start()` **and** navigate/overlay to `SosActiveScreen` regardless of what screen the app was on — this must work even if the app is backgrounded (coordinate with the Phase 6 foreground service: the payload callback must be able to fire this from the service, not only from a foregrounded Activity).
- `SosActiveScreen` shows who sent it (`senderName`) and a live "Broadcasting to N peers" / "Alert from <name>" as appropriate to sender vs. receiver, with a clear, deliberate Cancel/acknowledge action that calls `SosAlertPlayer.stop()` and clears the takeover.
- An SOS received while the mic/transcript flow is mid-message must not corrupt or block the ongoing walkie-talkie state — SOS interrupts visually/audibly but the transcript session keeps working underneath.

### 3. Full-screen intent / high-priority notification (backgrounded case)
- When the app is backgrounded and an `Sos` arrives, post a high-priority notification with `fullScreenIntent` so the SOS screen can appear over the lock screen (respect the platform's rules for this — request the appropriate permission/behavior per current Android notification guidelines, and fail gracefully to a loud heads-up notification + alarm sound if full-screen-intent isn't permitted on that device/OS version).

## Acceptance Criteria
- [ ] Triggering SOS on Phone A causes Phone B to audibly alarm (on `STREAM_ALARM`) and vibrate strongly within ~1s of receipt, **with Phone B's ringer set to silent** (not airplane/DND total-silence — that's the documented limit).
- [ ] SOS still triggers correctly when Phone B's screen is off / app backgrounded.
- [ ] Cancel/acknowledge on either device cleanly stops tone + vibration + strobe with no leaked loop.
- [ ] SOS confirm requires the deliberate long-press gesture — a stray tap never fires a real SOS.
- [ ] Accidentally triggering SOS twice in a row doesn't stack overlapping tone loops.
- [ ] SOS broadcasts to all connected peers if more than one is connected (if the topology supports >2 devices; if the product is locked to exactly 2, note that explicitly instead).
