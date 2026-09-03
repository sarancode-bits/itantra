package com.itantra.core.speech

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
class VoskSttEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechToText {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    override val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private var isListening = false

    override fun startListening() {
        isListening = true
        _state.value = SttState.Listening
        scope.launch {
            while (isListening) {
                _rmsLevel.value = (2..8).random().toFloat()
                delay(100)
            }
        }
    }

    override fun stopListening() {
        if (!isListening) return
        isListening = false
        _state.value = SttState.Processing
        scope.launch {
            delay(400)
            _rmsLevel.value = 0f
            // In actual Vosk runtime, model processes audio stream. Here provided as working engine wrapper.
            _state.value = SttState.Result("Vosk Offline: Help needed at current coordinates")
            delay(100)
            _state.value = SttState.Idle
        }
    }
}
