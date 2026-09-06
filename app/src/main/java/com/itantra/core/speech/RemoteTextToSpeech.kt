package com.itantra.core.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.itantra.IAiEngine
import com.itantra.IAiEngineCallback
import com.itantra.service.AiEngineService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IPC proxy that implements [TextToSpeechEngine] by forwarding all calls to the
 * [AiEngineService] running in the `:ai_engine` process via AIDL.
 *
 * This ensures that even if the ONNX native code crashes, only the
 * `:ai_engine` process is killed. The main UI process stays alive.
 */
@Singleton
class RemoteTextToSpeech @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeechEngine {

    companion object {
        private const val TAG = "RemoteTextToSpeech"
        private const val BIND_TIMEOUT_MS = 10_000L
    }

    private val _speakingState = MutableStateFlow<SpeakingState>(SpeakingState.Idle)
    override val speakingState: StateFlow<SpeakingState> = _speakingState.asStateFlow()

    private val _isMockMode = MutableStateFlow(false)
    override val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var engine: IAiEngine? = null
    private val bindDeferred = CompletableDeferred<IAiEngine>()

    private val callback = object : IAiEngineCallback.Stub() {
        override fun onSttStateChanged(stateCode: Int, text: String?) {
            // Handled by RemoteSpeechToText — ignore here
        }

        override fun onRmsLevelChanged(rmsLevel: Float) {
            // Handled by RemoteSpeechToText — ignore here
        }

        override fun onSpeakingStateChanged(stateCode: Int, text: String?) {
            _speakingState.value = when (stateCode) {
                0 -> SpeakingState.Idle
                1 -> SpeakingState.Speaking(text ?: "")
                2 -> SpeakingState.Error(text ?: "Unknown error")
                else -> SpeakingState.Idle
            }
        }

        override fun onInitialized(sttReady: Boolean, ttsReady: Boolean, isMockMode: Boolean) {
            _isReady.value = ttsReady
            _isMockMode.value = isMockMode
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val aiEngine = IAiEngine.Stub.asInterface(service)
            engine = aiEngine
            try {
                aiEngine.registerCallback(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register callback", e)
            }
            bindDeferred.complete(aiEngine)
            Log.i(TAG, "Bound to AiEngineService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "AiEngineService disconnected (process crashed?)")
            engine = null
            _isReady.value = false
        }
    }

    private fun ensureBound() {
        if (engine != null) return
        val intent = Intent(context, AiEngineService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to AiEngineService", e)
        }
    }

    override suspend fun initialize() {
        if (_isReady.value) return

        ensureBound()

        // Wait for the service to bind
        val aiEngine = withTimeoutOrNull(BIND_TIMEOUT_MS) {
            bindDeferred.await()
        }

        if (aiEngine == null) {
            Log.e(TAG, "Timed out waiting for AiEngineService bind")
            return
        }

        try {
            // The AI engine might already be initialized by RemoteSpeechToText
            if (aiEngine.checkReady()) {
                _isReady.value = true
                return
            }
            aiEngine.initialize()
            // Wait for the callback to signal readiness
            withTimeoutOrNull(30_000L) {
                while (!_isReady.value) {
                    kotlinx.coroutines.delay(100)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize remote TTS engine", e)
        }
    }

    override fun speak(text: String) {
        try {
            engine?.speak(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call remote speak", e)
            _speakingState.value = SpeakingState.Error("AI engine connection lost")
        }
    }

    override fun stop() {
        try {
            engine?.stopSpeaking()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call remote stopSpeaking", e)
        }
        _speakingState.value = SpeakingState.Idle
    }
}
