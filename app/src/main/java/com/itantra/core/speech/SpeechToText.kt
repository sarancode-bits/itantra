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
    val state: StateFlow<SttState>
    val rmsLevel: StateFlow<Float>
    fun startListening()
    fun stopListening()
}

sealed class SpeakingState {
    object Idle : SpeakingState()
    data class Speaking(val text: String) : SpeakingState()
    data class Error(val message: String) : SpeakingState()
}

interface TextToSpeechEngine {
    val speakingState: StateFlow<SpeakingState>
    fun speak(text: String)
    fun stop()
}
