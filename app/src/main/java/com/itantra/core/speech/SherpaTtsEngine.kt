package com.itantra.core.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SherpaTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mockFallback: MockTextToSpeech
) : TextToSpeechEngine {

    companion object {
        private const val TAG = "SherpaTtsEngine"
        private const val EN_MODEL_DIR = "models/tts/en"
        private const val HI_MODEL_DIR = "models/tts/hi"
    }

    private val _speakingState = MutableStateFlow<SpeakingState>(SpeakingState.Idle)
    override val speakingState: StateFlow<SpeakingState> = _speakingState.asStateFlow()

    private val _isMockMode = MutableStateFlow(false)
    override val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private var enTts: OfflineTts? = null
    private var hiTts: OfflineTts? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var mockStateJob: Job? = null
    private var initialized = false

    /**
     * Lazily initialize Sherpa TTS models on first use.
     * Loading ONNX native libraries from a background thread concurrently
     * with HWUI causes pthread_mutex corruption (SIGABRT).
     */
    private fun ensureInitialized() {
        if (!initialized) {
            initialized = true
            initSherpaTts()
        }
    }

    private fun initSherpaTts() {
        enTts = tryLoadVitsModel(
            modelDir = EN_MODEL_DIR,
            modelFile = "en_US-amy-low.onnx",
            label = "English"
        )
        hiTts = tryLoadVitsModel(
            modelDir = HI_MODEL_DIR,
            modelFile = "hi_IN-priyamvada-medium.onnx",
            label = "Hindi"
        )

        if (enTts == null && hiTts == null) {
            Log.w(TAG, "No TTS models loaded, falling back to mock mode.")
            _isMockMode.value = true
        }
    }

    /**
     * Attempts to load a Piper VITS model from the given asset directory.
     * Returns null (without crashing) if any required file is missing.
     */
    private fun tryLoadVitsModel(
        modelDir: String,
        modelFile: String,
        label: String
    ): OfflineTts? {
        return try {
            // Verify required files exist
            val requiredFiles = listOf(
                "$modelDir/$modelFile",
                "$modelDir/tokens.txt"
            )
            for (file in requiredFiles) {
                context.assets.open(file).close()
            }
            // Also verify espeak-ng-data directory exists
            val espeakList = context.assets.list("$modelDir/espeak-ng-data")
            if (espeakList == null || espeakList.isEmpty()) {
                Log.w(TAG, "$label espeak-ng-data missing, skipping.")
                return null
            }

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = "$modelDir/$modelFile",
                        lexicon = "",
                        tokens = "$modelDir/tokens.txt",
                        dataDir = "$modelDir/espeak-ng-data"
                    ),
                    numThreads = 1,
                    debug = false,
                    provider = "cpu"
                )
            )
            val tts = OfflineTts(assetManager = context.assets, config = config)
            Log.i(TAG, "$label TTS loaded (sample rate: ${tts.sampleRate()}).")
            tts
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $label TTS model", e)
            null
        }
    }

    /**
     * Selects the appropriate TTS engine based on a simple heuristic:
     * if the text contains Devanagari characters, use Hindi; otherwise English.
     */
    private fun selectTtsForText(text: String): OfflineTts? {
        val hasDevanagari = text.any { it.code in 0x0900..0x097F }
        return if (hasDevanagari && hiTts != null) hiTts else enTts ?: hiTts
    }

    override fun speak(text: String) {
        ensureInitialized()

        if (_isMockMode.value) {
            mockStateJob?.cancel()
            mockStateJob = scope.launch {
                mockFallback.speakingState.collect { _speakingState.value = it }
            }
            mockFallback.speak(text)
            return
        }

        playJob?.cancel()
        playJob = scope.launch {
            _speakingState.value = SpeakingState.Speaking(text)
            try {
                val tts = selectTtsForText(text)
                if (tts == null) {
                    _speakingState.value = SpeakingState.Error("No TTS model available for this text.")
                    return@launch
                }

                // Sherpa API: generate(text, sid, speed)
                val audio = tts.generate(text = text, sid = 0, speed = 1.0f)
                val sampleRate = tts.sampleRate()

                if (audio.samples.isEmpty()) {
                    _speakingState.value = SpeakingState.Error("TTS produced no audio.")
                    return@launch
                }

                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                ).coerceAtLeast(audio.samples.size * 4) // ensure buffer fits all samples

                audioTrack?.release()
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack?.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
                audioTrack?.play()

                // Wait for the duration of the audio
                val durationMs = (audio.samples.size.toFloat() / sampleRate * 1000).toLong()
                delay(durationMs + 100) // small buffer to ensure full playback

                _speakingState.value = SpeakingState.Idle
            } catch (e: CancellationException) {
                throw e // don't swallow cancellation
            } catch (e: Exception) {
                Log.e(TAG, "TTS playback error", e)
                _speakingState.value = SpeakingState.Error("Playback error: ${e.message}")
            } finally {
                try {
                    audioTrack?.stop()
                } catch (_: IllegalStateException) { /* already stopped */ }
                audioTrack?.release()
                audioTrack = null
            }
        }
    }

    override fun stop() {
        if (_isMockMode.value) {
            mockFallback.stop()
            return
        }
        playJob?.cancel()
        try {
            audioTrack?.stop()
        } catch (_: IllegalStateException) { /* already stopped */ }
        audioTrack?.release()
        audioTrack = null
        _speakingState.value = SpeakingState.Idle
    }
}
