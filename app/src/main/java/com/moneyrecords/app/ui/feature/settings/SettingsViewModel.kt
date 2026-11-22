package com.moneyrecords.app.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.repository.UserPrefsRepository
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

    fun setCalculatorEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setCalculatorEnabled(enabled) }
    }
}
