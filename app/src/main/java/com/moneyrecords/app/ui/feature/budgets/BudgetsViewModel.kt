package com.moneyrecords.app.ui.feature.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.Budget
import com.moneyrecords.app.domain.model.BudgetOverview
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.BudgetTarget
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.BudgetRepository
import com.moneyrecords.app.domain.repository.CategoryRepository
import com.moneyrecords.app.domain.repository.ComboIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class BudgetsState(
    val month: YearMonth,
    val today: LocalDate,
    val loading: Boolean = true,
    val overview: BudgetOverview? = null,
    /** Main expense categories that can get a budget. */
    val mainCategories: List<Category> = emptyList(),
    /** Active accounts that can get a budget. */
    val accounts: List<Account> = emptyList(),
) {
    val isCurrentMonth: Boolean get() = month == YearMonth.from(today)

    /** Targets that already have a budget this month. */
    val budgetedTargets: Set<BudgetTarget> get() = overview?.all.orEmpty().map { it.budget.target }.toSet()

    /** "₹X a day" for [progress] in the viewed month (see [dailyAllowance]). */
    fun perDay(progress: BudgetProgress): Long? = dailyAllowance(progress, month, today)
}

/**
 * How much can still be spent per day:
 * - current month: remaining ÷ days left including today (0 when nothing is left);
 * - future month: limit ÷ days in the month;
 * - past month: null (no longer meaningful).
 */
fun dailyAllowance(progress: BudgetProgress, month: YearMonth, today: LocalDate): Long? {
    val current = YearMonth.from(today)
    return when {
        month < current -> null
        month > current -> progress.budget.limit / month.lengthOfMonth()
        else -> {
            val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
            (progress.remaining.coerceAtLeast(0)) / daysLeft
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgets: BudgetRepository,
    categories: CategoryRepository,
    accounts: AccountRepository,
    private val clock: Clock,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now(clock))

    val state: StateFlow<BudgetsState> = combine(
        month.flatMapLatest { m -> budgets.observeOverview(m) },
        categories.observeTree(CategoryKind.EXPENSE),
        accounts.observeAccounts(includeArchived = false),
        month,
    ) { overview, tree, accountList, m ->
        BudgetsState(
            month = m,
            today = LocalDate.now(clock),
            loading = overview.month != m,
            // Keep showing the previous month until the new one arrives, so the screen never blanks.
            overview = overview,
            mainCategories = tree.filterNot { ComboIcon.isCombo(it.iconKey) },
            accounts = accountList,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BudgetsState(month = month.value, today = LocalDate.now(clock)),
    )

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }

    /**
     * Sets [target]'s limit from the viewed month onwards. When [offsets] is given (editing), it
     * replaces the records counted back this month. [onDone] gets an error message or null.
     */
    fun save(target: BudgetTarget, limit: Long?, offsets: Set<Long>? = null, onDone: (String?) -> Unit) {
        if (limit == null || limit <= 0) return onDone("Enter an amount above ₹0")
        viewModelScope.launch {
            val error = runCatching {
                budgets.save(
                    Budget(categoryId = target.categoryId, month = month.value, limit = limit, accountId = target.accountId, scope = target.scope),
                )
                if (offsets != null) budgets.setOffsets(target, month.value, offsets)
            }.exceptionOrNull()?.let { it.message ?: "Couldn't save budget" }
            onDone(error)
        }
    }

    /** Income (and, for accounts, transfers in) this month that can be counted back against [target]. */
    suspend fun offsetCandidates(target: BudgetTarget) = budgets.offsetCandidates(target, month.value)
    suspend fun selectedOffsets(target: BudgetTarget) = budgets.selectedOffsets(target, month.value)

    /** Ends [target]'s budget from the viewed month onwards; earlier months keep it. */
    fun stop(target: BudgetTarget) {
        viewModelScope.launch { budgets.stop(target, month.value) }
    }

    /** Spending for [target] in the month before the viewed one (editor hint). */
    suspend fun lastMonthSpent(target: BudgetTarget): Long = budgets.spentIn(target, month.value.minusMonths(1))
}
