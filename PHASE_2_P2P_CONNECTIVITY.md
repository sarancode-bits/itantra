# PHASE 2 — P2P Connectivity Layer

Paste with `00_MASTER.md`. Builds on Phase 1. Deliver **real host/scan/pair working end to end** — first in mock mode (simulated peer), then wired to real Nearby Connections in the `prod` flavor.

## Task

### 1. `core/transport/P2pTransport.kt` — the interface
Define a transport-agnostic interface, roughly:
```kotlin
interface P2pTransport {
    val connectionState: StateFlow<ConnectionState>       // Idle, Advertising, Discovering, Connecting, Connected(peerName), Disconnected, Error
    val discoveredPeers: StateFlow<List<PeerInfo>>
    val incomingPayloads: SharedFlow<ByteArray>

    suspend fun startHosting(localName: String)
    suspend fun startScanning()
    suspend fun connectTo(peer: PeerInfo)
    suspend fun send(payload: ByteArray)
    suspend fun disconnect()
    suspend fun stop()
}
```
Keep it deliberately small — everything above transport (protocol encoding, retries, UI) is built on top of this in later phases, and it must be identically implementable by a fake and a real backend.

### 2. `core/transport/MockTransport.kt`
- Simulates a peer: `startHosting`/`startScanning` populate `discoveredPeers` with 1–2 fake devices after a short realistic delay (~1–2s, not instant — so the UI's "Searching…" state is actually exercised).
- `connectTo` transitions through `Connecting` → `Connected` with a short delay.
- `send` echoes back a scripted/canned response on `incomingPayloads` after a delay, so the whole Talk screen loop can be tested with no real peer device at all.
- Add a small amount of simulated unreliability (occasional delay spike) so error/retry UI (built in Phase 6) has something real to react to.

### 3. `core/transport/NearbyTransport.kt` — real implementation
Wrap `ConnectionsClient` (`Nearby.getConnectionsClient(context)`):
- Use **`Strategy.P2P_CLUSTER`** (many-to-many, matches "two nearby phones" but doesn't artificially cap the topology — note in a comment that `P2P_POINT_TO_POINT` is the stricter alternative if the product is later locked to exactly 2 devices).
- Implement `startAdvertising` / `startDiscovery` with a stable `SERVICE_ID` (e.g. `"com.itantra.SERVICE_ID"`).
- Implement `ConnectionLifecycleCallback` (accept connections automatically after discovery — no manual confirm code needed for a 2-person emergency flow, but log/expose the peer name), and `PayloadCallback` for `Payload.Type.BYTES`.
- Map every `ConnectionsClient` callback and status code into the same `ConnectionState` sealed type the mock uses — the ViewModel layer must never know which transport it's talking to.
- Handle `STATUS_ENDPOINT_UNKNOWN`, `STATUS_ERROR`, timeouts, etc. by mapping to `ConnectionState.Error(message)` with a **plain-language** message per the master doc's rules (never surface a raw Play Services status code to the user).

### 4. `core/radio/RadioStateMonitor.kt`
- Expose a `StateFlow<RadioState>` reporting whether Wi-Fi and Bluetooth are currently on.
- This exists because of the documented late-2026 Nearby Connections change: the API will stop auto-enabling radios. `NearbyTransport.startHosting`/`startScanning` must check this **before** calling into Nearby, and if a radio is off, transition to a new `ConnectionState.RadiosOff(missing: List<String>)` instead of silently failing — the UI (Phase 6) turns this into "Turn on Wi-Fi to continue" messaging with a deep link to system Wi-Fi/Bluetooth settings.

### 5. Wire into Phase 1 UI
- `HomeViewModel` now depends on `P2pTransport` (injected via Hilt — `mock` flavor binds `MockTransport`, `prod` binds `NearbyTransport`).
- Host/Scan buttons call the real `startHosting`/`startScanning`.
- Discovered peer list now reflects `discoveredPeers` live.
- Tapping Connect calls `connectTo` and drives the Pairing screen off real `connectionState`.
- Talk screen's connection status bar now reflects real `connectionState`.

## Acceptance Criteria
- [ ] In **mock flavor**: Host → (nothing to do, waits) and Scan → discovers the fake peer within ~2s → Connect → reaches Connected state → Talk screen shows "Connected to <fake peer name>."
- [ ] In **prod flavor**, on two physical devices with Wi-Fi/Bluetooth on: one taps Host, the other taps Scan, the host device appears in the list within a few seconds, connecting succeeds, both Talk screens show Connected.
- [ ] Turning Wi-Fi off before hosting/scanning produces the `RadiosOff` state, not a silent hang or crash.
- [ ] Disconnecting one device is reflected as `Disconnected` on the other within a few seconds.
- [ ] No transport-specific type (Nearby's `ConnectionInfo`, `Payload`, etc.) leaks outside `core/transport/` — ViewModels only see `ConnectionState`/`PeerInfo`.
