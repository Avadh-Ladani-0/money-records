package com.moneyrecords.app.ui.feature.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.calc.Expression
import com.moneyrecords.app.domain.model.RecurringRuleDetails
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.recurring.Recurrence
import com.moneyrecords.app.domain.repository.RecurringRepository
import com.moneyrecords.app.ui.feature.record.RepeatSpec
import com.moneyrecords.app.ui.navigation.RouteArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RecurringListViewModel @Inject constructor(repository: RecurringRepository) : ViewModel() {
    val rules: StateFlow<List<RecurringRuleDetails>?> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class RecurringEditorState(
    val loading: Boolean = true,
    val details: RecurringRuleDetails? = null,
    val amountText: String = "",
    val note: String = "",
    val repeat: RepeatSpec? = null,
    val nextDate: LocalDate = LocalDate.MIN,
    val active: Boolean = true,
    val error: String? = null,
) {
    val amount: Long? get() = Money.parse(amountText)
    val canSave: Boolean get() = details != null && (amount ?: 0) > 0 && repeat != null
}

/** Edit a recurring item's amount, note, schedule and next date; pause, resume or delete it. */
@HiltViewModel
class RecurringEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: RecurringRepository,
) : ViewModel() {
    private val ruleId: Long = handle.get<Long>(RouteArgs.RULE_ID) ?: 0L
    private val _state = MutableStateFlow(RecurringEditorState())
    val state: StateFlow<RecurringEditorState> = _state.asStateFlow()
    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val d = repository.getDetails(ruleId)
            _state.value = if (d == null) RecurringEditorState(loading = false, error = "Recurring item not found") else {
                val r = d.rule
                RecurringEditorState(
                    loading = false,
                    details = d,
                    amountText = Expression.toPlainText(BigDecimal(r.amount).movePointLeft(2)),
                    note = r.note,
                    repeat = RepeatSpec(
                        r.frequency, r.interval,
                        endDate = if (Recurrence.isOnce(r)) null else r.endDate,
                        nextDate = r.nextRunDate,
                        once = Recurrence.isOnce(r),
                    ),
                    nextDate = r.nextRunDate,
                    active = r.active,
                )
            }
        }
    }

    fun onAmount(v: String) = _state.update { it.copy(amountText = v, error = null) }
    fun onNote(v: String) = _state.update { it.copy(note = v.take(200)) }
    fun onRepeat(v: RepeatSpec?) = _state.update {
        if (v == null) it else it.copy(repeat = v, nextDate = v.nextFrom(LocalDate.now()))
    }
    fun onNextDate(v: LocalDate) = _state.update { it.copy(nextDate = v) }

    fun save() {
        val s = _state.value
        val rule = s.details?.rule ?: return
        val amount = s.amount ?: return _state.update { it.copy(error = "Enter a valid amount") }
        val repeat = s.repeat ?: return
        viewModelScope.launch {
            runCatching {
                repository.save(
                    rule.copy(
                        amount = amount, note = s.note, frequency = repeat.frequency, interval = repeat.interval,
                        endDate = if (repeat.once) s.nextDate else repeat.endDate,
                        nextRunDate = s.nextDate,
                        // A once-item, or a changed schedule, re-anchors on the new next date.
                        startDate = if (repeat.once || repeat.frequency != rule.frequency || s.nextDate != rule.nextRunDate) s.nextDate else rule.startDate,
                        active = rule.active || repeat.once,
                    ),
                )
            }.onSuccess { _done.send(Unit) }.onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't save") } }
        }
    }

    fun togglePaused() {
        val active = !_state.value.active
        viewModelScope.launch {
            repository.setActive(ruleId, active)
            _state.update { it.copy(active = active) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(ruleId)
            _done.send(Unit)
        }
    }
}
