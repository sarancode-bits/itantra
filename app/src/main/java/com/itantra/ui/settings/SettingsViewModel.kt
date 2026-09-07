package com.itantra.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.radio.RadioStateMonitor
import com.itantra.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SessionRepository,
    private val radioStateMonitor: RadioStateMonitor,
    private val languagePreferences: com.itantra.core.speech.LanguagePreferences
) : ViewModel() {

    private val prefs = context.getSharedPreferences("itantra_settings", Context.MODE_PRIVATE)

    val radioStatus = radioStateMonitor.radioStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), radioStateMonitor.checkRadioState())

    val selectedLanguage = languagePreferences.selectedLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.itantra.core.speech.SupportedLanguage.ENGLISH)

    private val _isMetricsOverlayEnabled = MutableStateFlow(prefs.getBoolean("metrics_overlay", false))
    val isMetricsOverlayEnabled: StateFlow<Boolean> = _isMetricsOverlayEnabled.asStateFlow()

    fun setLanguage(language: com.itantra.core.speech.SupportedLanguage) {
        languagePreferences.setLanguage(language)
        repository.setLanguage(language)
    }

    fun toggleMetricsOverlay(enabled: Boolean) {
        prefs.edit().putBoolean("metrics_overlay", enabled).apply()
        _isMetricsOverlayEnabled.value = enabled
    }

    fun clearMessageHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun refreshRadioState() {
        radioStateMonitor.checkRadioState()
    }
}
