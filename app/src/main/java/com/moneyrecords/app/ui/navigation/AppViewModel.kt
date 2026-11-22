package com.moneyrecords.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.repository.CalculatorPrefs
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App-wide actions that outlive a single screen: undoing a delete, the floating calculator. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val records: RecordRepository,
    private val prefs: UserPrefsRepository,
) : ViewModel() {

    val calculator: StateFlow<CalculatorPrefs?> = prefs.observeCalculator()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Re-creates a deleted record (it gets a new id). */
    fun restore(record: Record) {
        viewModelScope.launch { records.save(record.copy(id = 0)) }
    }

    fun moveCalculator(x: Float, y: Float) {
        viewModelScope.launch { prefs.setCalculatorPosition(x, y) }
    }
}
