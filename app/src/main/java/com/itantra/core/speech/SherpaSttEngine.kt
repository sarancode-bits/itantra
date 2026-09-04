package com.itantra.core.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class SherpaSttEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mockFallback: MockSpeechToText
) : SpeechToText {

    companion object {
        private const val TAG = "SherpaSttEngine"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "models/stt/whisper"
    }

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    override val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _isMockMode = MutableStateFlow(false)
    override val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private var recognizer: OfflineRecognizer? = null

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Thread-safe buffer for accumulated audio samples
    private val samplesMutex = Mutex()
    private val audioSamples = mutableListOf<Float>()

    private var mockStateJob: Job? = null
    private var mockRmsJob: Job? = null
    private var initialized = false

    /**
     * Lazily initialize the Sherpa recognizer on first use.
     * Loading ONNX native libraries from a background thread concurrently
     * with HWUI causes pthread_mutex corruption (SIGABRT).
     * Lazy init avoids this by loading safely on the main thread when needed.
     */
    private fun ensureInitialized() {
        if (!initialized) {
            initialized = true
            initSherpaRecognizer()
        }
    }

    private fun initSherpaRecognizer() {
        try {
            // Verify all three required model files exist
            val requiredFiles = listOf(
                "$MODEL_DIR/tiny-encoder.int8.onnx",
                "$MODEL_DIR/tiny-decoder.int8.onnx",
                "$MODEL_DIR/tiny-tokens.txt"
            )
            for (file in requiredFiles) {
                context.assets.open(file).close()
            }

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = SAMPLE_RATE,
                    featureDim = 80
                ),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "$MODEL_DIR/tiny-encoder.int8.onnx",
                        decoder = "$MODEL_DIR/tiny-decoder.int8.onnx",
                        language = "en",
                        task = "transcribe"
                    ),
                    tokens = "$MODEL_DIR/tiny-tokens.txt",
                    numThreads = 1,
                    debug = false,
                    provider = "cpu"
                )
            )
            recognizer = OfflineRecognizer(
                assetManager = context.assets,
                config = config
            )
            Log.i(TAG, "Sherpa STT initialized successfully with Whisper Tiny INT8.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Sherpa STT, falling back to mock mode", e)
            _isMockMode.value = true
        }
    }

    @SuppressLint("MissingPermission")
    override fun startListening() {
        ensureInitialized()

        if (_isMockMode.value) {
            mockStateJob?.cancel()
            mockRmsJob?.cancel()
            mockStateJob = scope.launch {
                mockFallback.state.collect { _state.value = it }
            }
            mockRmsJob = scope.launch {
                mockFallback.rmsLevel.collect { _rmsLevel.value = it }
            }
            mockFallback.startListening()
            return
        }

        if (recordingJob?.isActive == true) return

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _state.value = SttState.Error("Microphone initialization failed. Check audio permissions.")
                return
            }

            scope.launch { samplesMutex.withLock { audioSamples.clear() } }
            audioRecord?.startRecording()
            _state.value = SttState.Listening

            recordingJob = scope.launch {
                val shortBuffer = ShortArray(bufferSize / 2) // bufferSize is in bytes, shorts are 2 bytes
                while (isActive) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (read > 0) {
                        var sum = 0.0f
                        val floats = FloatArray(read)
                        for (i in 0 until read) {
                            val f = shortBuffer[i] / 32768.0f
                            floats[i] = f
                            sum += f * f
                        }
                        samplesMutex.withLock {
                            for (f in floats) audioSamples.add(f)
                        }
                        val rms = sqrt((sum / read).toDouble()).toFloat()
                        _rmsLevel.value = (rms * 100f).coerceIn(0f, 10f)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording error", e)
            _state.value = SttState.Error("Recording error: ${e.message}")
        }
    }

    override fun stopListening() {
        if (_isMockMode.value) {
            mockFallback.stopListening()
            return
        }

        _state.value = SttState.Processing
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) { /* already stopped */ }
        audioRecord?.release()
        audioRecord = null
        _rmsLevel.value = 0f

        scope.launch {
            try {
                val rec = recognizer
                if (rec == null) {
                    _state.value = SttState.Error("Recognizer not initialized")
                    return@launch
                }

                val samples: FloatArray
                samplesMutex.withLock {
                    samples = audioSamples.toFloatArray()
                    audioSamples.clear()
                }

                if (samples.isEmpty()) {
                    _state.value = SttState.Error("No audio recorded.")
                    return@launch
                }

                val stream = rec.createStream()
                // Sherpa API: acceptWaveform(samples, sampleRate)
                stream.acceptWaveform(samples, SAMPLE_RATE)
                rec.decode(stream)

                // Sherpa API: getResult(stream)
                val result = rec.getResult(stream)
                stream.release()

                val text = result.text.trim()
                if (text.isNotEmpty()) {
                    _state.value = SttState.Result(text)
                } else {
                    _state.value = SttState.Error("No speech detected. Please try again.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Decode error", e)
                _state.value = SttState.Error("Speech recognition error: ${e.message}")
            }
        }
    }
}
