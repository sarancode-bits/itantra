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
 * IPC proxy that implements [SpeechToText] by forwarding all calls to the
 * [AiEngineService] running in the `:ai_engine` process via AIDL.
 *
 * This ensures that even if the ONNX native code crashes, only the
 * `:ai_engine` process is killed. The main UI process stays alive.
 */
@Singleton
class RemoteSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechToText {

    companion object {
        private const val TAG = "RemoteSpeechToText"
        private const val BIND_TIMEOUT_MS = 10_000L
    }

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    override val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _isMockMode = MutableStateFlow(false)
    override val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var engine: IAiEngine? = null
    private val bindDeferred = CompletableDeferred<IAiEngine>()

    private val callback = object : IAiEngineCallback.Stub() {
        override fun onSttStateChanged(stateCode: Int, text: String?) {
            _state.value = when (stateCode) {
                0 -> SttState.Idle
                1 -> SttState.Listening
                2 -> SttState.Processing
                3 -> SttState.Result(text ?: "")
                4 -> SttState.Error(text ?: "Unknown error")
                else -> SttState.Idle
            }
        }

        override fun onRmsLevelChanged(rmsLevel: Float) {
            _rmsLevel.value = rmsLevel
        }

        override fun onSpeakingStateChanged(stateCode: Int, text: String?) {
            // Handled by RemoteTextToSpeech — ignore here
        }

        override fun onInitialized(sttReady: Boolean, ttsReady: Boolean, isMockMode: Boolean) {
            _isReady.value = sttReady
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
            // The OS will auto-restart the service due to START_STICKY
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
            aiEngine.initialize()
            // The callback will set _isReady when initialization completes
            // Wait a reasonable amount for the callback
            withTimeoutOrNull(30_000L) {
                while (!_isReady.value) {
                    kotlinx.coroutines.delay(100)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize remote AI engine", e)
        }
    }

    override fun startListening() {
        try {
            engine?.startListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call remote startListening", e)
            _state.value = SttState.Error("AI engine connection lost")
        }
    }

    override fun stopListening() {
        try {
            engine?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call remote stopListening", e)
            _state.value = SttState.Error("AI engine connection lost")
        }
    }

    override fun startContinuousListening() {
        try {
            engine?.startContinuousListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call remote startContinuousListening", e)
            _state.value = SttState.Error("AI engine connection lost")
        }
    }

    override fun setLanguage(language: SupportedLanguage) {
        try {
            engine?.setLanguage(language.code)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set language on remote engine", e)
        }
    }
}
