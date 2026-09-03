package com.itantra.core.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSttEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voskFallbackEngine: VoskSttEngine
) : SpeechToText {

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    override val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var useVoskFallback = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = SttState.Listening
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            _rmsLevel.value = rmsdB.coerceIn(0f, 10f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _state.value = SttState.Processing
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    // Force fallback to Vosk offline speech engine if system requires internet
                    useVoskFallback = true
                    "Network error. Switching to Vosk offline speech recognition engine."
                }
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly."
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                SpeechRecognizer.ERROR_CLIENT -> "Client error in speech engine."
                else -> "Speech recognition error ($error)"
            }
            if (useVoskFallback) {
                voskFallbackEngine.startListening()
            } else {
                _state.value = SttState.Error(message)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()
            if (!text.isNullOrEmpty()) {
                _state.value = SttState.Result(text)
            } else {
                _state.value = SttState.Error("No text transcribed.")
            }
            _rmsLevel.value = 0f
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun startListening() {
        if (useVoskFallback) {
            voskFallbackEngine.startListening()
            return
        }

        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                if (speechRecognizer == null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                    ) {
                        speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    } else {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    }
                    speechRecognizer?.setRecognitionListener(recognitionListener)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                _state.value = SttState.Listening
                speechRecognizer?.startListening(intent)
            } else {
                useVoskFallback = true
                voskFallbackEngine.startListening()
            }
        } catch (e: Exception) {
            // SpeechRecognizer can throw if system service unavailable or
            // called from wrong thread. Fall back to Vosk silently.
            e.printStackTrace()
            useVoskFallback = true
            voskFallbackEngine.startListening()
        }
    }

    override fun stopListening() {
        try {
            if (useVoskFallback) {
                voskFallbackEngine.stopListening()
            } else {
                _state.value = SttState.Processing
                speechRecognizer?.stopListening()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = SttState.Idle
        }
    }
}
