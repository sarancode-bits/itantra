package com.itantra.ui.talk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.speech.SpeakingState
import com.itantra.core.speech.SttState
import com.itantra.core.transport.ConnectionState
import com.itantra.data.repository.SessionRepository
import com.itantra.data.repository.TranscriptEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TalkViewModel @Inject constructor(
    val repository: SessionRepository
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

    fun startRecording() {
        repository.sttEngine.startListening()
    }

    fun stopRecording() {
        repository.sttEngine.stopListening()
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
