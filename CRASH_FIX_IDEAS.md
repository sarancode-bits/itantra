# Future Architectural Fixes for Native Crash Prevention

## The Problem
During development, we encountered a severe `SIGABRT` native crash:
`FORTIFY: pthread_mutex_lock called on a destroyed mutex`

### Root Cause Analysis
The ONNX Runtime C++ native library uses an internal thread pool (often OpenMP) to parallelize model execution when `numThreads > 1`. 
When the `SherpaSttEngine` (Whisper) or `SherpaTtsEngine` (Piper VITS) was initialized on a Kotlin background coroutine (`Dispatchers.IO`) *while* Android's Hardware UI renderer (`hwuiTask`) was actively drawing frames (e.g., during startup animations), the native thread pool creation violently corrupted shared memory mutexes used by the Android OS. 

This resulted in Android instantly terminating the app to protect system stability.

---

## 1. The Current Implementation (The Stable Fix)
**Status: IMPLEMENTED**

We fixed the crash by configuring the AI models to use **`numThreads = 1`** and initializing them lazily on the main thread exactly when the user requests them (e.g., pressing the mic button).
* **Why it works:** By forcing ONNX Runtime to execute sequentially on a single thread, it completely avoids spawning the buggy internal thread pool, bypassing the memory corruption entirely.
* **The Trade-off:** The very first time the user taps the mic button, the main thread freezes for 1-2 seconds while ~165MB of models are loaded into RAM.

---

## Future Production Architectures (For Commercial Scale)

If iTantra is scaled into a massive commercial product, the following architectures should be considered to eliminate the UI freeze while maintaining 100% stability.

### 2. The Ultimate Best: NNAPI / XNNPACK Hardware Acceleration
**Status: RECOMMENDED FOR PRODUCTION**

Instead of using the standard `"cpu"` Execution Provider, we can recompile the Sherpa-ONNX C++ libraries to target Android's **Neural Networks API (NNAPI)** or the **XNNPACK** delegate.
* **How it works:** It offloads the AI processing directly to the phone's GPU or Neural Processing Unit (NPU). 
* **Benefits:** It natively avoids the CPU thread pool bugs entirely. It reduces battery drain significantly and makes voice transcription 2x to 3x faster.
* **Complexity:** High. Requires custom compilation of the C++ ONNX runtime specifically for Android NDK hardware targets.

### 3. The Most Robust Architecture: Dedicated OS Process (IPC)
**Status: BEST FOR STABILITY**

Move the `SherpaSttEngine` and `SherpaTtsEngine` into a dedicated background Android `Service` running in an entirely separate memory space.
* **How it works:** Defined in the Manifest via `android:process=":ai_engine"`. 
* **Benefits:** Guarantees 100% UI stability. Even if the AI model runs out of memory or hits a fatal native C++ error, it only kills the hidden background process. The main app (UI, messaging, SOS) remains perfectly alive and responsive, and can silently restart the AI engine in the background. (This is how Google Assistant works).
* **Complexity:** Medium. Requires writing Android IPC (Inter-Process Communication) code (AIDL or Messengers) to send audio bytes back and forth between the UI process and the AI process.

### 4. The "Band-Aid" UX Fix: Static Splash Screen
**Status: NOT RECOMMENDED**

Block the user on a static splash screen during app launch to load the models in the background.
* **How it works:** Because the UI is static and not animating, the `hwuiTask` is dormant, significantly reducing the chance of the race condition triggering when ONNX creates its thread pool.
* **Benefits:** Very easy to implement.
* **Complexity:** Low. However, it does not actually fix the underlying native bug; it just masks it by avoiding concurrent UI rendering.
