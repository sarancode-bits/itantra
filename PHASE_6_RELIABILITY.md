# PHASE 6 — Reliability: Foreground Service, Permissions Flow, Reconnection

Paste with `00_MASTER.md`. Builds on Phases 2–5. This phase turns a working demo into something that survives real conditions: screen off, app backgrounded, permissions denied then granted, connection drops.

## Task

### 1. `service/ItantraForegroundService.kt`
- A foreground `Service` (type `connectedDevice`, or `dataSync` if more appropriate to declared category) that:
  - Owns the lifecycle of `P2pTransport` and the payload receive loop once a connection is active or hosting/scanning is in progress, so it keeps running with the screen off.
  - Owns `SosAlertPlayer` so an incoming SOS can fire even if no Activity is in the foreground.
  - Shows a persistent, low-priority notification while active: "iTantra connected to <peer>" / "iTantra searching…", with a tap-through back into the app and a "Disconnect" action.
- Start it when hosting/scanning begins; stop it (or demote it) when fully disconnected and idle, per platform foreground-service lifecycle rules — do not run it forever in the background doing nothing.
- ViewModels/`SessionRepository` bind to this service (or communicate via a shared singleton scope through Hilt — pick one pattern and use it consistently) rather than each owning transport lifecycle independently.

### 2. Runtime permissions flow
Build a single, reusable permissions screen/flow (`ui/permissions/` or folded into onboarding) that:
- Explains **why** each permission is needed, in plain language, *before* the system dialog (per the master doc's rule 3) — e.g. "iTantra needs Nearby Devices access to find other phones nearby, without using the internet."
- Requests, in a sensible order: `RECORD_AUDIO` → nearby/Bluetooth/Wi-Fi permissions (version-appropriate: `NEARBY_WIFI_DEVICES` + `BLUETOOTH_ADVERTISE/CONNECT/SCAN` on 12+/13+, `ACCESS_FINE_LOCATION` on older) → `POST_NOTIFICATIONS`.
- Handles "denied" and "denied forever" distinctly — the latter deep-links to app settings with clear instructions, not a dead-end.
- Blocks Host/Scan actions with an inline, actionable prompt (not a silent no-op) if a required permission is missing, rather than letting the transport layer fail mysteriously.
- Ties into `RadioStateMonitor` from Phase 2: if Wi-Fi/Bluetooth is off, show the same kind of inline actionable prompt ("Turn on Wi-Fi") with a deep link to system settings, per the documented late-2026 API behavior change.

### 3. Reconnection & error resilience
- If a connection drops mid-session (peer walks away, radio hiccup), `SessionRepository`/`NearbyTransport` should attempt a bounded number of automatic reconnect attempts to the last known peer before surfacing `Disconnected` with a manual "Reconnect" button — never an infinite silent retry loop, never a hard failure with no path forward.
- Any transport, speech, or alert error surfaces through one consistent in-app error-presentation pattern (e.g. a small toast/snackbar for transient issues, a full inline state for blocking issues) — audit Phases 2–5's error states against the master doc's rule 6 (plain language, actionable) and fix any that leaked a raw exception/status code.
- Add basic battery-awareness: if this is easy to wire (e.g. via `Presence`'s `batteryPct` field already in the protocol), surface the peer's battery level in the connection status area — useful in a real emergency, and validates the `Presence` message type actually gets used.

## Acceptance Criteria
- [ ] Locking the screen mid-connection keeps the pairing alive and messages still arrive (verify via the persistent notification and by sending a message from the other device while Phone B's screen is off).
- [ ] Fresh install → permission flow explains each ask before the system dialog, in order, and the app is fully usable once all are granted.
- [ ] Denying a permission produces a clear, actionable in-app prompt, not a crash or a silently broken feature.
- [ ] Turning Wi-Fi off while connected and back on triggers a visible reconnect attempt and eventual recovery (or a clear manual "Reconnect" if it can't recover automatically).
- [ ] No unhandled exceptions from transport/speech/alert layers reach the user as a crash — everything maps to an in-app state.
- [ ] Foreground service notification is present exactly when there's an active reason for it (hosting, scanning, or connected) and goes away when idle.
