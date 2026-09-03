package com.itantra.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeechEngine, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = try {
        TextToSpeech(context, this)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
    private var isInitialized = false

    private val _speakingState = MutableStateFlow<SpeakingState>(SpeakingState.Idle)
    override val speakingState: StateFlow<SpeakingState> = _speakingState.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                _speakingState.value = SpeakingState.Error("TTS language offline package missing.")
            } else {
                isInitialized = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _speakingState.value = SpeakingState.Speaking(utteranceId ?: "")
                    }

                    override fun onDone(utteranceId: String?) {
                        _speakingState.value = SpeakingState.Idle
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _speakingState.value = SpeakingState.Error("TTS playback error")
                    }
                })
            }
        } else {
            _speakingState.value = SpeakingState.Error("Failed to initialize system TextToSpeech.")
        }
    }

    override fun speak(text: String) {
        if (isInitialized) {
            _speakingState.value = SpeakingState.Speaking(text)
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
        } else {
            // Fallback indicator
            _speakingState.value = SpeakingState.Speaking(text)
        }
    }

    override fun stop() {
        tts?.stop()
        _speakingState.value = SpeakingState.Idle
    }
}
