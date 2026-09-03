# PHASE 0 — Project Setup & Skeleton

Paste this together with `00_MASTER.md`. Build target: a repo that **compiles and launches a blank dark-themed screen in mock mode**, with the architecture wired but no real feature logic yet.

## Task

You are setting up the Android project for **iTantra** exactly as described in the master document. Do the following:

1. **New Android Studio project**: Kotlin, Jetpack Compose, min SDK 26 (Android 8.0 — Nearby Connections' practical floor), target/compile SDK = latest stable. Package: `com.itantra`.

2. **Gradle**:
   - Add `com.google.android.gms:play-services-nearby` (latest stable).
   - Add Hilt (`com.google.dagger:hilt-android` + compiler) and the Hilt Gradle plugin.
   - Add Room + kapt/ksp.
   - Add Compose BOM, Material 3, `androidx.navigation:navigation-compose`.
   - Add Vosk Android dependency (`com.alphacephei:vosk-android`) — leave it wired but unused until Phase 3.
   - Set up **two product flavors**: `mock` and `prod`, both sharing `dimension = "mode"`. `mock` is the default/debug-friendly flavor.

3. **Hilt wiring**: `ItantraApp : Application()` annotated `@HiltAndroidApp`. Create empty `di/TransportModule.kt`, `di/SpeechModule.kt`, `di/AlertModule.kt` — each will later provide the interface bound to either the mock or real implementation based on flavor (use `src/mock/java/...` and `src/prod/java/...` source sets for the flavor-specific `@Module` classes, both binding the same interface — this is the mechanism, not an `if (BuildConfig...)` branch).

4. **Package skeleton**: create the full folder structure from the master doc's Section 3 (`core/transport`, `core/speech`, `core/alert`, `core/radio`, `core/protocol`, `data/db`, `data/repository`, `service`, `ui/theme`, `ui/home`, `ui/talk`, `ui/sos`, `ui/settings`) with empty/placeholder files where logic doesn't exist yet — this is scaffolding so later phases know exactly where code goes.

5. **Theme stub**: implement `ui/theme/Color.kt`, `Type.kt`, `Theme.kt` per the master doc's design system (Section 5) — dark background, safety-orange or alert-red accent, high-contrast text. No screens use it yet beyond a placeholder.

6. **Navigation stub**: a `NavHost` with three empty destinations — `home`, `talk`, `settings` — each just a `Text("Home")`-style placeholder Composable in the themed dark background, so the app is navigable and visibly on-theme from commit one.

7. **AndroidManifest**: declare (but do not yet request at runtime) the permissions the whole app will eventually need, with comments explaining each:
   - `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`
   - `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (Android 12+)
   - `NEARBY_WIFI_DEVICES` (Android 13+)
   - `ACCESS_FINE_LOCATION` (still required by Nearby Connections on many OS versions for Wi-Fi/BT scanning)
   - `RECORD_AUDIO`
   - `POST_NOTIFICATIONS` (Android 13+, needed for the foreground service)
   - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`
   - `VIBRATE`
   - `CAMERA` (only if flashlight strobe is kept — comment it as optional)

8. **README.md** at repo root: one paragraph on what iTantra is, how to build the `mock` flavor and run it (this is the primary dev/demo loop), and a note that `prod` needs a real second device to test pairing.

## Acceptance Criteria
- [ ] `assembleMockDebug` builds with zero errors.
- [ ] App launches showing the dark emergency theme (not default Material colors).
- [ ] Bottom/side nav (or equivalent) moves between Home / Talk / Settings placeholders.
- [ ] Hilt compiles with empty modules (no unresolved bindings needed yet).
- [ ] No permission dialogs appear on launch (nothing requested yet — that's Phase 6).
- [ ] Folder structure matches Section 3 of the master doc exactly, so later phase prompts can reference exact file paths.
