package com.itantra.core.speech

import kotlinx.coroutines.flow.StateFlow

sealed class SttState {
    object Idle : SttState()
    object Listening : SttState()
    object Processing : SttState()
    data class Result(val text: String) : SttState()
    data class RmsLevel(val rmsDb: Float) : SttState()
    data class Error(val message: String) : SttState()
}

interface SpeechToText {
    val isMockMode: StateFlow<Boolean>
    val isReady: StateFlow<Boolean>
    val state: StateFlow<SttState>
    val rmsLevel: StateFlow<Float>

    /**
     * Explicitly initialize the underlying AI model.
     * MUST be called from the main thread while HWUI is not animating
     * to avoid native pthread_mutex corruption (SIGABRT).
     */
    suspend fun initialize()
    fun startListening()
    fun startContinuousListening() {} // Default empty for mock backward compatibility
    fun stopListening()
    fun setLanguage(language: SupportedLanguage) {}
}

sealed class SpeakingState {
    object Idle : SpeakingState()
    data class Speaking(val text: String) : SpeakingState()
    data class Error(val message: String) : SpeakingState()
}

interface TextToSpeechEngine {
    val isMockMode: StateFlow<Boolean>
    val isReady: StateFlow<Boolean>
    val speakingState: StateFlow<SpeakingState>

    /**
     * Explicitly initialize the underlying AI model.
     * MUST be called from the main thread while HWUI is not animating
     * to avoid native pthread_mutex corruption (SIGABRT).
     */
    suspend fun initialize()
    fun speak(text: String)
    fun stop()
    fun setLanguage(language: SupportedLanguage) {}
}
