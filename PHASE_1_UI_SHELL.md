# PHASE 1 — UI Shell (Dark Emergency Interface)

Paste with `00_MASTER.md`. Builds on Phase 0. Deliver **every screen as fully-styled static UI** driven by fake/hardcoded state — no real transport, speech, or SOS logic yet. The goal is that a non-technical person could tap through the whole app and understand exactly what it does, before a single line of real functionality exists.

## Screens to build (all in `ui/`)

### 1. Home / Host-Scan-Pair (`ui/home/HomeScreen.kt`)
- Big app wordmark/logo area, minimal.
- Two large primary actions, equally weighted, clearly labelled: **"Host"** (become discoverable, wait for a peer) and **"Scan"** (look for a nearby host).
- Below: a list area for discovered/available peers when scanning (use 2–3 fake peer entries: name + signal-strength-style icon + "Connect" button) — static for now.
- A persistent small "Mock Mode" badge/chip when running the mock flavor, so it's always obvious during a demo that this isn't live radios.

### 2. Pairing/Connecting state (can be a sub-state of Home, or its own screen `ui/home/PairingScreen.kt`)
- Full-screen "Connecting to <name>…" with a clear spinner/progress element (not a generic Material spinner — themed), and a Cancel action.
- Success state transitions to Talk screen (fake `NavController` call for now).

### 3. Talk screen (`ui/talk/TalkScreen.kt`) — the core screen
- **Connection status bar** pinned at top: colored dot + plain text (`Connected to Ravi's phone`, `Searching…`, `Disconnected`), always visible.
- **Transcript/history area**: scrollable list of past messages, each bubble showing sender name, the transcribed text, a timestamp, and a small delivery-state icon (sent/delivered/failed) — populate with 4–5 fake messages, alternating sender.
- **Mic button**: the dominant visual element, large, centered or bottom-anchored, press-and-hold interaction (visually show a "held" pressed state and a "recording" pulsing state using fake local state — no real audio yet). Label under/near it: "Hold to talk."
- **SOS button**: visually distinct (red/orange, separate from the mic, smaller but unmistakable), placed where it can't be hit by accident but is fast to reach — e.g. top corner or a dedicated strip. Tapping it (fake, for now) navigates to a static SOS confirm screen.

### 4. SOS confirm + active screens (`ui/sos/SosConfirmScreen.kt`, `ui/sos/SosActiveScreen.kt`)
- Confirm: "Send SOS to all connected devices?" with a **long-press-to-confirm** control (not a simple tap — must be hard to trigger by accident), big and red, plus Cancel.
- Active: full-screen red/orange takeover, large "SOS ACTIVE" text, "Broadcasting to N peers" (fake number), pulsing/strobing visual treatment, a clear "Cancel SOS" action.

### 5. Settings (`ui/settings/SettingsScreen.kt`)
- Device display name field (fake, local state).
- Mock mode explanation text + (if in a debug/runtime-toggle build) a switch — wire the switch to do nothing yet, just visual.
- Permission status rows (fake "Granted"/"Not granted" chips for each permission from the manifest) — will become real in Phase 6.
- App version/about line.

## State handling
Even though logic is fake, set each screen up correctly for the future: each screen gets its own `data class XyzUiState(...)` and the Composable takes that state as a parameter + lambdas for actions — do **not** hardcode strings directly inside Composables where a ViewModel will later own them. Use a trivial `XyzViewModel` with a `MutableStateFlow` seeded with fake data now, so Phase 2–5 only need to swap the data source, not restructure the UI.

## Acceptance Criteria
- [ ] Every screen above exists, is reachable via navigation, and matches the dark/high-contrast design system from the master doc (no default Material purple/blue anywhere).
- [ ] Mic button is unmistakably the largest, most prominent control on the Talk screen.
- [ ] SOS requires a deliberate long-press-style confirmation, never a single accidental tap.
- [ ] Connection status is visible on every screen where it matters, in plain language.
- [ ] All screens work with rotation/different screen sizes without breaking layout.
- [ ] App still builds and runs with zero crashes navigating through every screen, purely on fake state.
