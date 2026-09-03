# PHASE 7 — Polish, Empty/Error States, Demo Readiness

Paste with `00_MASTER.md`. Builds on all prior phases. This phase makes iTantra feel finished and makes it trivially demoable to someone who has never seen it before (a judge, a first responder evaluating it, etc.).

## Task

### 1. Micro-interactions (state-communicating only, per master doc Section 5)
- Mic button: refine the press/hold/recording/sending animation into one smooth continuous transition rather than discrete jumps between states.
- Connection status dot: subtle pulse while `Connecting`/`Searching`, solid while `Connected`, no motion while `Disconnected` (motion should mean "something is happening," not decorate a static state).
- Message bubbles: a brief, cheap entrance animation on arrival (incoming) — nothing that delays the message being legible.
- SOS active screen: a controlled pulse/strobe treatment that reads as urgent without being a literal photosensitivity hazard — cap flash rate within safe limits (avoid ~3–30Hz strobing per standard photosensitive-seizure guidance) if flashlight/screen strobing is used.

### 2. Empty and edge states
- Home screen with zero peers found yet during Scan: clear "Still searching… make sure the other phone is hosting and both have Wi-Fi/Bluetooth on" — not a blank list.
- Talk screen before any messages: a short instructional empty state ("Hold the mic button to send your first message"), not a blank scroll area.
- Very long transcript sessions: confirm scroll performance and that auto-scroll-to-latest behaves correctly without fighting a user who's scrolled up to review history.
- Low/no battery warnings if easy to add (device's own battery, and peer's via `Presence`), styled consistently with other status messaging.

### 3. Accessibility pass
- Content descriptions on all icon-only controls (mic, SOS, status dot).
- Confirm color contrast ratios against the design system's palette meet at least WCAG AA (AAA where feasible per the master doc) for all text-on-background pairs actually shipped.
- Confirm touch targets (mic, SOS, connect buttons) meet minimum 48dp.
- TalkBack pass: mic button and SOS button states are announced meaningfully, not just "Button."

### 4. Demo readiness
- Write `DEMO_SCRIPT.md`: a step-by-step script for a 2-minute live demo using **mock mode on a single device** (no second phone needed) — host mode discovery, a couple of mock voice exchanges, and one SOS trigger/cancel — plus a second short script for the **two-real-device** version if a second phone is available.
- Final `README.md` pass: what the app does, the tech stack table from the master doc, how to build both flavors, the honest SOS capability caveat from Phase 5, and known limitations (e.g. offline STT accuracy depends on installed language packs; effective range depends on Nearby Connections' underlying radio choice).
- Sanity-check app icon, app name, and splash/launch behavior look intentional, not default-template.

## Acceptance Criteria
- [ ] A first-time user can go from cold app launch to sending and receiving a mock-mode voice message in under 30 seconds without instruction, guided only by on-screen copy.
- [ ] Every screen has a considered empty/loading/error state — none show blank space or a raw stack trace.
- [ ] Accessibility pass items above are verifiable (contrast checked, TalkBack announces sensibly, touch targets measured).
- [ ] `DEMO_SCRIPT.md` and final `README.md` exist and a person unfamiliar with the codebase could run the demo from them alone.
- [ ] Full click-through of Phases 1–6's acceptance criteria still all pass after this phase's polish changes (regression check).
