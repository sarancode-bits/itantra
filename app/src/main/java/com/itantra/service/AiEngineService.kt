package com.itantra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.RemoteCallbackList
import android.util.Log
import androidx.core.app.NotificationCompat
import com.itantra.IAiEngine
import com.itantra.IAiEngineCallback
import com.itantra.core.speech.MockSpeechToText
import com.itantra.core.speech.MockTextToSpeech
import com.itantra.core.speech.SherpaSttEngine
import com.itantra.core.speech.SherpaTtsEngine
import com.itantra.core.speech.SpeakingState
import com.itantra.core.speech.SttState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Service running in the `:ai_engine` process.
 *
 * This process has NO HWUI renderer, so ONNX Runtime can safely create
 * its native C++ thread pools without corrupting shared pthread mutexes.
 *
 * Even if the ONNX native code crashes (SIGABRT, SIGSEGV, OOM),
 * only this process is killed. The main app process (UI, messaging, SOS)
 * remains alive and responsive, and can silently restart this service.
 *
 * This is the same architecture Google uses for Google Assistant.
 */
class AiEngineService : Service() {

    companion object {
        private const val TAG = "AiEngineService"
        private const val CHANNEL_ID = "ai_engine_channel"
        private const val NOTIFICATION_ID = 1002
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val callbacks = RemoteCallbackList<IAiEngineCallback>()

    // Direct instantiation — no Hilt in the :ai_engine process
    // (Hilt's SingletonComponent is per-process, and we don't need DI here)
    private var sttEngine: SherpaSttEngine? = null
    private var ttsEngine: SherpaTtsEngine? = null
    private var initialized = false

    private val binder = object : IAiEngine.Stub() {

        override fun registerCallback(callback: IAiEngineCallback?) {
            callback?.let { callbacks.register(it) }
        }

        override fun unregisterCallback(callback: IAiEngineCallback?) {
            callback?.let { callbacks.unregister(it) }
        }

        override fun initialize() {
            scope.launch {
                initializeEngines()
            }
        }

        override fun startListening() {
            sttEngine?.startListening()
        }

        override fun stopListening() {
            sttEngine?.stopListening()
        }

        override fun startContinuousListening() {
            sttEngine?.startContinuousListening()
        }

        override fun setLanguage(languageCode: String?) {
            languageCode?.let { code ->
                val lang = com.itantra.core.speech.SupportedLanguage.fromCode(code)
                sttEngine?.setLanguage(lang)
                ttsEngine?.setLanguage(lang)
            }
        }

        override fun speak(text: String?) {
            text?.let { ttsEngine?.speak(it) }
        }

        override fun stopSpeaking() {
            ttsEngine?.stop()
        }

        override fun checkReady(): Boolean = initialized
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "AiEngineService created in :ai_engine process")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("AI Engine Active")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    private suspend fun initializeEngines() {
        if (initialized) return

        try {
            Log.i(TAG, "Initializing AI engines in :ai_engine process...")

            // Create engines directly (no Hilt in this process)
            val mockStt = MockSpeechToText()
            val mockTts = MockTextToSpeech()
            val stt = SherpaSttEngine(applicationContext, mockStt)
            val tts = SherpaTtsEngine(applicationContext, mockTts)

            // Safe to initialize here — no HWUI in :ai_engine process
            stt.initialize()
            tts.initialize()

            sttEngine = stt
            ttsEngine = tts
            initialized = true

            // Start observing state changes to relay to the UI process
            observeSttState()
            observeTtsState()

            // Notify callbacks
            broadcastInitialized(
                sttReady = stt.isReady.value,
                ttsReady = tts.isReady.value,
                isMockMode = stt.isMockMode.value || tts.isMockMode.value
            )

            Log.i(TAG, "AI engines initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AI engines", e)
            broadcastInitialized(sttReady = false, ttsReady = false, isMockMode = true)
        }
    }

    private fun observeSttState() {
        scope.launch {
            sttEngine?.state?.collect { state ->
                val (code, text) = when (state) {
                    is SttState.Idle -> 0 to ""
                    is SttState.Listening -> 1 to ""
                    is SttState.Processing -> 2 to ""
                    is SttState.Result -> 3 to state.text
                    is SttState.Error -> 4 to state.message
                    is SttState.RmsLevel -> return@collect // handled separately
                }
                broadcastSttState(code, text)
            }
        }
        scope.launch {
            sttEngine?.rmsLevel?.collect { level ->
                broadcastRmsLevel(level)
            }
        }
    }

    private fun observeTtsState() {
        scope.launch {
            ttsEngine?.speakingState?.collect { state ->
                val (code, text) = when (state) {
                    is SpeakingState.Idle -> 0 to ""
                    is SpeakingState.Speaking -> 1 to state.text
                    is SpeakingState.Error -> 2 to state.message
                }
                broadcastSpeakingState(code, text)
            }
        }
    }

    // --- Broadcast helpers ---

    private fun broadcastSttState(stateCode: Int, text: String) {
        val count = callbacks.beginBroadcast()
        try {
            for (i in 0 until count) {
                try {
                    callbacks.getBroadcastItem(i).onSttStateChanged(stateCode, text)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to broadcast STT state", e)
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    private fun broadcastRmsLevel(level: Float) {
        val count = callbacks.beginBroadcast()
        try {
            for (i in 0 until count) {
                try {
                    callbacks.getBroadcastItem(i).onRmsLevelChanged(level)
                } catch (e: Exception) {
                    // Suppress — RMS updates are high frequency, don't spam logs
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    private fun broadcastSpeakingState(stateCode: Int, text: String) {
        val count = callbacks.beginBroadcast()
        try {
            for (i in 0 until count) {
                try {
                    callbacks.getBroadcastItem(i).onSpeakingStateChanged(stateCode, text)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to broadcast TTS state", e)
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    private fun broadcastInitialized(sttReady: Boolean, ttsReady: Boolean, isMockMode: Boolean) {
        val count = callbacks.beginBroadcast()
        try {
            for (i in 0 until count) {
                try {
                    callbacks.getBroadcastItem(i).onInitialized(sttReady, ttsReady, isMockMode)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to broadcast init state", e)
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    // --- Notification ---

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("iTantra AI Engine")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "iTantra AI Engine",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background AI processing for speech recognition and synthesis"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "AiEngineService destroyed")
        scope.cancel()
        callbacks.kill()
        super.onDestroy()
    }
}
