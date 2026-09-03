package com.itantra.core.speech

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockSpeechToText @Inject constructor() : SpeechToText {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    override val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val phrases = listOf(
        "Help, I'm at the north gate",
        "We're okay, moving to shelter",
        "Need water, two people injured",
        "Bridge blocked at sector 4",
        "All clear at checkpoint bravo"
    )
    private var phraseIndex = 0

    override fun startListening() {
        _state.value = SttState.Listening
        scope.launch {
            // Simulate live audio waveform levels while recording
            for (i in 1..8) {
                _rmsLevel.value = (3..9).random().toFloat()
                delay(120)
            }
        }
    }

    override fun stopListening() {
        _state.value = SttState.Processing
        scope.launch {
            delay(500)
            val phrase = phrases[phraseIndex % phrases.size]
            phraseIndex++
            _rmsLevel.value = 0f
            _state.value = SttState.Result(phrase)
            delay(100)
            _state.value = SttState.Idle
        }
    }
}

@Singleton
class MockTextToSpeech @Inject constructor() : TextToSpeechEngine {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _speakingState = MutableStateFlow<SpeakingState>(SpeakingState.Idle)
    override val speakingState: StateFlow<SpeakingState> = _speakingState.asStateFlow()

    override fun speak(text: String) {
        _speakingState.value = SpeakingState.Speaking(text)
        scope.launch {
            delay(1500)
            _speakingState.value = SpeakingState.Idle
        }
    }

    override fun stop() {
        _speakingState.value = SpeakingState.Idle
    }
}
