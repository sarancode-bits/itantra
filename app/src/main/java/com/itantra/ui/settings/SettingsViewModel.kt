package com.itantra.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.radio.RadioStateMonitor
import com.itantra.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SessionRepository,
    private val radioStateMonitor: RadioStateMonitor
) : ViewModel() {

    val radioStatus = radioStateMonitor.radioStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), radioStateMonitor.checkRadioState())

    fun clearMessageHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun refreshRadioState() {
        radioStateMonitor.checkRadioState()
    }
}
