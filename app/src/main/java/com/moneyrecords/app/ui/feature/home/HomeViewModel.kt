package com.moneyrecords.app.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.DueOccurrence
import com.moneyrecords.app.domain.model.MonthSummary
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.BudgetRepository
import com.moneyrecords.app.domain.repository.RecurringRepository
import com.moneyrecords.app.notify.BudgetAlerts
import com.moneyrecords.app.notify.RecurringNotifier
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.moneyrecords.app.domain.repository.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val totalBalance: Long = 0,
    val month: YearMonth = YearMonth.now(),
    val summary: MonthSummary = MonthSummary(0, 0),
    val recent: List<RecordDetails> = emptyList(),
    val hasAccounts: Boolean = true,
    /** Overall budget in effect this month, if any. */
    val budget: BudgetProgress? = null,
    /** Recurring records waiting for Add / Skip. */
    val due: List<DueOccurrence> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    accounts: AccountRepository,
    records: RecordRepository,
    budgets: BudgetRepository,
    private val recurring: RecurringRepository,
    private val alerts: BudgetAlerts,
    private val recurringNotifier: RecurringNotifier,
    clock: Clock,
) : ViewModel() {
    private val month = YearMonth.now(clock)
    private val today = LocalDate.now(clock)

    val state: StateFlow<HomeState> = combine(
        combine(
            accounts.observeAccounts(),
            records.observeMonthSummary(month),
            records.observeRecords(RecordFilter(limit = RECENT_COUNT)),
            budgets.observeOverview(month),
        ) { a, s, r, b -> listOf(a, s, r, b) },
        recurring.observeDue(today),
    ) { parts, due ->
        @Suppress("UNCHECKED_CAST") val accountList = parts[0] as List<com.moneyrecords.app.domain.model.Account>
        val summary = parts[1] as MonthSummary
        @Suppress("UNCHECKED_CAST") val recent = parts[2] as List<RecordDetails>
        val budgetOverview = parts[3] as com.moneyrecords.app.domain.model.BudgetOverview
        HomeState(
            loading = false,
            totalBalance = accountList.sumOf { it.balance },
            month = month,
            summary = summary,
            recent = recent,
            hasAccounts = accountList.isNotEmpty(),
            budget = budgetOverview.overall,
            // One row per rule (its earliest due date); the rest follow after Add / Skip.
            due = due.groupBy { it.details.rule.id }.map { it.value.first() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(month = month))

    fun addDue(occurrence: DueOccurrence) {
        viewModelScope.launch {
            runCatching { recurring.confirm(occurrence.details.rule.id) }
            recurringNotifier.cancelDue(occurrence.details.rule.id)
            alerts.checkAfterRecord(YearMonth.from(occurrence.date))
        }
    }

    fun skipDue(occurrence: DueOccurrence) {
        viewModelScope.launch {
            runCatching { recurring.skip(occurrence.details.rule.id) }
            recurringNotifier.cancelDue(occurrence.details.rule.id)
        }
    }

    companion object {
        const val RECENT_COUNT = 10
    }
}
