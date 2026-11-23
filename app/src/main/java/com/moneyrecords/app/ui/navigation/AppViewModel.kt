package com.moneyrecords.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.repository.CalculatorPrefs
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.domain.repository.SplitRepository
import com.moneyrecords.app.domain.split.SplitSpec
import com.moneyrecords.app.security.AppLockManager
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
    private val splits: SplitRepository,
    val appLock: AppLockManager,
) : ViewModel() {

    val calculator: StateFlow<CalculatorPrefs?> = prefs.observeCalculator()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Re-creates a deleted record (it gets a new id); a split bill comes back with its split. */
    fun restore(record: Record, split: SplitSpec? = null) {
        viewModelScope.launch {
            if (split != null) splits.saveSplit(record.copy(id = 0, amount = split.total), split)
            else records.save(record.copy(id = 0, splitTotal = null))
        }
    }

    fun moveCalculator(x: Float, y: Float) {
        viewModelScope.launch { prefs.setCalculatorPosition(x, y) }
    }
}
