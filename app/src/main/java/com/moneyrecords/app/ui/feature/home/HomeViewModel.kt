package com.moneyrecords.app.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.MonthSummary
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.BudgetRepository
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    accounts: AccountRepository,
    records: RecordRepository,
    budgets: BudgetRepository,
    clock: Clock,
) : ViewModel() {
    private val month = YearMonth.now(clock)

    val state: StateFlow<HomeState> = combine(
        accounts.observeAccounts(),
        records.observeMonthSummary(month),
        records.observeRecords(RecordFilter(limit = RECENT_COUNT)),
        budgets.observeOverview(month),
    ) { accountList, summary, recent, budgetOverview ->
        HomeState(
            loading = false,
            totalBalance = accountList.sumOf { it.balance },
            month = month,
            summary = summary,
            recent = recent,
            hasAccounts = accountList.isNotEmpty(),
            budget = budgetOverview.overall,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(month = month))

    companion object {
        const val RECENT_COUNT = 10
    }
}
