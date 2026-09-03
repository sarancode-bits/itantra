# PHASE 4 — Messaging: Wire Protocol, Transcript, Delivery Status

Paste with `00_MASTER.md`. Builds on Phases 2 and 3. This is where the app becomes an actual two-phone walkie-talkie: STT output goes out over the transport, arrives on the other phone, and comes out as TTS — with a persistent, honest delivery status.

## Task

### 1. `core/protocol/Messages.kt`
Implement the sealed `ItantraMessage` type from the master doc (`Voice`, `Sos`, `Ack`, `Presence`) with `kotlinx.serialization` (or a minimal hand-rolled encoder if you want zero extra dependency weight — either is fine, but pick one and document it) to turn it into the `ByteArray` that `P2pTransport.send` expects, and back.

### 2. `data/repository/SessionRepository.kt`
This becomes the **single source of truth** the Talk screen observes — it does not talk to `P2pTransport` or `SpeechToText` directly, it combines them:
- Owns a `StateFlow<List<TranscriptEntry>>` (each entry: id, sender, text, timestamp, `DeliveryStatus` = Sending/Sent/Delivered/Failed, isOwn).
- On local STT `Result`, wraps it in `ItantraMessage.Voice`, appends a `Sending` entry immediately (optimistic UI), calls `transport.send(encoded)`, and on success flips to `Sent`; on `Ack` receipt (see below) flips to `Delivered`; on send failure or timeout without an Ack, flips to `Failed` with a retry affordance.
- On `transport.incomingPayloads`, decodes the message: `Voice` → append a remote transcript entry **and** trigger `TextToSpeech.speak(text)` immediately (this is the core walkie-talkie behavior — remote voice messages play automatically, they are not tap-to-play), then send back an `Ack`. `Sos` → forwarded to the SOS layer (Phase 5) regardless of current screen. `Presence` → updates peer metadata for the status bar.
- Exposes a lightweight retry: re-`send` a `Failed` entry's original payload on user tap.

### 3. `data/db/` — Room persistence
- `MessageEntity` mirroring `TranscriptEntry`, `PeerEntity` for "recently paired with" history (name + last-connected time, so Home screen can offer "Reconnect to <name>" as a shortcut, not just cold Scan every time).
- `SessionRepository` writes through to Room so the transcript survives app restarts/backgrounding — an emergency log should not vanish if the app is killed.
- A basic DAO query for "messages in the current session" vs. full history (keep the Talk screen showing current session by default; full history can be a simple scrollback, not a separate screen, unless trivial to add).

### 4. Wire into Talk screen for real
- Transcript list now renders from `SessionRepository`'s live state — remove the Phase 1 fake seed data.
- Delivery-status icon on each own-message bubble reflects real `DeliveryStatus`.
- Incoming messages appear and are audibly spoken without the recipient needing to tap anything.
- Connection status bar's peer name now comes from real `Presence`/`ConnectionState`, not a placeholder.

## Acceptance Criteria
- [ ] Mock flavor: sending a message shows Sending → Sent → Delivered within a couple seconds against `MockTransport`'s scripted echo/Ack.
- [ ] Prod flavor, two real devices connected: speaking on Phone A produces an audible spoken message on Phone B within a few seconds, and Phone A's bubble correctly reaches `Delivered`.
- [ ] Killing and reopening the app preserves the transcript for the current pairing (Room persistence).
- [ ] A forced failure (e.g. disconnecting mid-send) results in a `Failed` state with a working retry tap, not a stuck `Sending` spinner forever.
- [ ] No raw audio bytes are ever sent over the transport — only encoded `ItantraMessage` payloads (confirm by checking payload sizes are text-scale, not audio-scale).
