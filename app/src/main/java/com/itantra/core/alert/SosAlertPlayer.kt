package com.itantra.core.alert

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface SosAlertPlayer {
    val isActive: StateFlow<Boolean>
    fun start()
    fun stop()
}

@Singleton
class RealSosAlertPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) : SosAlertPlayer {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var flashJob: Job? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun start() {
        if (_isActive.value) return
        _isActive.value = true

        // 1. Play loud alarm sound on STREAM_ALARM
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setVolume(1.0f, 1.0f)
                // Use prepareAsync to avoid ANR when alarm URI is slow to resolve
                setOnPreparedListener { mp -> mp.start() }
                setOnErrorListener { _, _, _ -> true } // Suppress error propagation
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Strong emergency vibration pattern
        try {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Strobe flashlight (max 2Hz for photosensitivity safety)
        flashJob = scope.launch {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                if (cameraManager != null && cameraId != null) {
                    var state = false
                    while (_isActive.value) {
                        state = !state
                        cameraManager.setTorchMode(cameraId, state)
                        delay(300) // Safe strobe rate (~1.6 Hz)
                    }
                    cameraManager.setTorchMode(cameraId, false)
                }
            } catch (e: Exception) {
                // Silently skip if flash not available
            }
        }
    }

    override fun stop() {
        _isActive.value = false
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            vibrator.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        flashJob?.cancel()
        flashJob = null

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraManager != null && cameraId != null) {
                cameraManager.setTorchMode(cameraId, false)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
