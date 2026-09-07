package com.itantra.core.speech

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguagePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("itantra_lang_prefs", Context.MODE_PRIVATE)
    
    private val _selectedLanguage = MutableStateFlow(
        SupportedLanguage.fromCode(prefs.getString("selected_language", "en") ?: "en")
    )
    val selectedLanguage: StateFlow<SupportedLanguage> = _selectedLanguage.asStateFlow()
    
    fun setLanguage(language: SupportedLanguage) {
        prefs.edit {
            putString("selected_language", language.code)
        }
        _selectedLanguage.value = language
    }
}
