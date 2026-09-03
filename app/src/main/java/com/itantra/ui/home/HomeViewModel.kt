package com.itantra.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.transport.ConnectionState
import com.itantra.core.transport.PeerInfo
import com.itantra.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SessionRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Idle)

    val discoveredPeers: StateFlow<List<PeerInfo>> = repository.discoveredPeers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startHosting(deviceName: String = "Emergency Node") {
        viewModelScope.launch {
            repository.transport.startHosting(deviceName)
        }
    }

    fun startScanning() {
        viewModelScope.launch {
            repository.transport.startScanning()
        }
    }

    fun connectTo(peer: PeerInfo) {
        viewModelScope.launch {
            repository.transport.connectTo(peer)
        }
    }

    fun cancelConnection() {
        viewModelScope.launch {
            repository.transport.stop()
        }
    }
}
