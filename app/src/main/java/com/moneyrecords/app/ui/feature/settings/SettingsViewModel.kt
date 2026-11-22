package com.moneyrecords.app.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val prefs: UserPrefsRepository) : ViewModel() {
    val calculatorEnabled: StateFlow<Boolean> = prefs.observeCalculator().map { it.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val theme: StateFlow<AppTheme> = prefs.observeTheme()
        .map { key -> AppTheme.entries.firstOrNull { it.name == key } ?: AppTheme.NOTHING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppTheme.NOTHING)

    fun setCalculatorEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setCalculatorEnabled(enabled) }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { prefs.setTheme(theme.name) }
    }
}
