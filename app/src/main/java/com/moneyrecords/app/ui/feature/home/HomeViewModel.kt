package com.moneyrecords.app.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.insights.CategoryShare
import com.moneyrecords.app.domain.insights.Insights
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.BudgetOverview
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.BudgetScope
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.DueOccurrence
import com.moneyrecords.app.domain.model.MonthSummary
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.RecurringRuleDetails
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.BudgetRepository
import com.moneyrecords.app.domain.repository.CategoryRepository
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.repository.RecurringRepository
import com.moneyrecords.app.domain.time.MonthRange
import com.moneyrecords.app.notify.BudgetAlerts
import com.moneyrecords.app.notify.RecurringNotifier
import com.moneyrecords.app.ui.feature.budgets.dailyAllowance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val totalBalance: Long = 0,
    val month: YearMonth = YearMonth.now(),
    val summary: MonthSummary = MonthSummary(0, 0),
    val hasAccounts: Boolean = true,
    /** Overall budget in effect this month, if any. */
    val budget: BudgetProgress? = null,
    /** "₹X a day" left on the overall budget. */
    val budgetPerDay: Long? = null,
    /** The other budget closest to its limit (only when it's at 50% or more). */
    val tightest: BudgetProgress? = null,
    val hasAnyBudget: Boolean = false,
    /** Recurring records waiting for Add / Skip. */
    val due: List<DueOccurrence> = emptyList(),
    /** The next recurring items that aren't due yet. */
    val upcoming: List<RecurringRuleDetails> = emptyList(),
    /** This month's biggest spending categories. */
    val topCategories: List<CategoryShare> = emptyList(),
)

private data class MoneyParts(val accounts: List<Account>, val summary: MonthSummary, val overview: BudgetOverview)

@HiltViewModel
class HomeViewModel @Inject constructor(
    accounts: AccountRepository,
    records: RecordRepository,
    budgets: BudgetRepository,
    categories: CategoryRepository,
    private val recurring: RecurringRepository,
    private val alerts: BudgetAlerts,
    private val recurringNotifier: RecurringNotifier,
    clock: Clock,
) : ViewModel() {
    private val month = YearMonth.now(clock)
    private val today = LocalDate.now(clock)

    private val money = combine(
        accounts.observeAccounts(),
        records.observeMonthSummary(month),
        budgets.observeOverview(month),
    ) { a, s, o -> MoneyParts(a, s, o) }

    private val spending = combine(
        records.observeRecords(RecordFilter(kind = RecordKind.EXPENSE, range = MonthRange.of(month, clock.zone))),
        categories.observeTree(CategoryKind.EXPENSE, includeArchived = true),
    ) { monthRecords, tree -> Insights.topCategories(monthRecords, tree) }

    val state: StateFlow<HomeState> = combine(
        money,
        spending,
        recurring.observeDue(today),
        recurring.observeRules(),
    ) { m, top, due, rules ->
        val overall = m.overview.overall
        val others = m.overview.all.filter { it.budget.scope != BudgetScope.OVERALL }
        HomeState(
            loading = false,
            totalBalance = m.accounts.sumOf { it.balance },
            month = month,
            summary = m.summary,
            hasAccounts = m.accounts.isNotEmpty(),
            budget = overall,
            budgetPerDay = overall?.let { dailyAllowance(it, month, today) },
            tightest = others.maxByOrNull { it.fraction }?.takeIf { it.fraction >= 0.5f },
            hasAnyBudget = m.overview.all.isNotEmpty(),
            // One row per rule (its earliest due date); the rest follow after Add / Skip.
            due = due.groupBy { it.details.rule.id }.map { it.value.first() },
            upcoming = Insights.upcoming(rules, today),
            topCategories = top,
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
}
