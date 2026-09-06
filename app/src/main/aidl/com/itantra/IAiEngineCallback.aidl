// IAiEngineCallback.aidl
package com.itantra;

/**
 * Callback interface for the AI engine running in :ai_engine process.
 * Delivers STT/TTS state changes back to the main UI process.
 */
interface IAiEngineCallback {
    /**
     * Called when the STT state changes.
     * @param stateCode 0=Idle, 1=Listening, 2=Processing, 3=Result, 4=Error
     * @param text The result text (for Result state) or error message (for Error state)
     */
    void onSttStateChanged(int stateCode, String text);

    /**
     * Called when the RMS audio level changes during recording.
     * @param rmsLevel The current RMS level (0-10 range)
     */
    void onRmsLevelChanged(float rmsLevel);

    /**
     * Called when the TTS speaking state changes.
     * @param stateCode 0=Idle, 1=Speaking, 2=Error
     * @param text The text being spoken or error message
     */
    void onSpeakingStateChanged(int stateCode, String text);

    /**
     * Called when the AI engine has finished initializing.
     * @param sttReady Whether STT initialized successfully
     * @param ttsReady Whether TTS initialized successfully
     * @param isMockMode Whether engines fell back to mock mode
     */
    void onInitialized(boolean sttReady, boolean ttsReady, boolean isMockMode);
}
