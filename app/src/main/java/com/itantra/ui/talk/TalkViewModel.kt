package com.itantra.ui.talk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.speech.SpeakingState
import com.itantra.core.speech.SttState
import com.itantra.core.transport.ConnectionState
import com.itantra.data.repository.SessionRepository
import com.itantra.data.repository.TranscriptEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class TalkViewModel @Inject constructor(
    val repository: SessionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Idle)

    val transcript: StateFlow<List<TranscriptEntry>> = repository.transcript
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sttState: StateFlow<SttState> = repository.sttEngine.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SttState.Idle)

    val rmsLevel: StateFlow<Float> = repository.sttEngine.rmsLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val speakingState: StateFlow<SpeakingState> = repository.ttsEngine.speakingState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpeakingState.Idle)

    val peerBatteryPct: StateFlow<Int?> = repository.peerBatteryPct
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isMockMode: StateFlow<Boolean> = repository.sttEngine.isMockMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val latencyMetrics = repository.latencyTracker.metrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val prefs = context.getSharedPreferences("itantra_settings", Context.MODE_PRIVATE)
    val isMetricsOverlayEnabled = MutableStateFlow(prefs.getBoolean("metrics_overlay", false)).asStateFlow()

    private val _isHandsFreeMode = MutableStateFlow(false)
    val isHandsFreeMode: StateFlow<Boolean> = _isHandsFreeMode.asStateFlow()

    private var recordingStartMs: Long = 0

    fun startRecording() {
        recordingStartMs = System.currentTimeMillis()
        repository.sttEngine.startListening()
    }

    fun stopRecording() {
        val durationMs = System.currentTimeMillis() - recordingStartMs
        repository.latencyTracker.recordPttReleased(durationMs)
        repository.sttEngine.stopListening()
    }

    fun toggleHandsFreeMode() {
        _isHandsFreeMode.value = !_isHandsFreeMode.value
        if (_isHandsFreeMode.value) {
            recordingStartMs = System.currentTimeMillis()
            repository.sttEngine.startContinuousListening()
        } else {
            repository.sttEngine.stopListening()
        }
    }

    fun retrySendMessage(entry: TranscriptEntry) {
        repository.retrySendMessage(entry)
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.transport.disconnect()
        }
    }
}
