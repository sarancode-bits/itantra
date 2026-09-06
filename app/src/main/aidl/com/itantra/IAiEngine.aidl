// IAiEngine.aidl
package com.itantra;

import com.itantra.IAiEngineCallback;

/**
 * AIDL interface for the AI engine service running in :ai_engine process.
 * Provides cross-process access to STT and TTS functionality.
 *
 * This architecture guarantees that even if the ONNX native C++ runtime
 * crashes (SIGABRT, SIGSEGV, OOM), only the :ai_engine process is killed.
 * The main app (UI, messaging, SOS) remains perfectly alive and responsive.
 */
interface IAiEngine {
    /**
     * Register a callback to receive state updates from the AI engine.
     */
    void registerCallback(IAiEngineCallback callback);

    /**
     * Unregister a previously registered callback.
     */
    void unregisterCallback(IAiEngineCallback callback);

    /**
     * Initialize all AI models (STT + TTS).
     * This is safe to call from any thread since the models are loaded
     * in the :ai_engine process which has no HWUI renderer.
     */
    void initialize();

    /**
     * Start recording and processing speech-to-text.
     */
    void startListening();

    /**
     * Stop recording and trigger transcription.
     */
    void stopListening();

    /**
     * Speak the given text aloud using TTS.
     */
    void speak(String text);

    /**
     * Stop any active TTS playback.
     */
    void stopSpeaking();

    /**
     * Check if the AI engine has been initialized.
     */
    boolean checkReady();
}
