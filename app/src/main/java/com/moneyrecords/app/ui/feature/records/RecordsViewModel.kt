package com.moneyrecords.app.ui.feature.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.TimeRange
import com.moneyrecords.app.domain.repository.AccountRepository
import com.moneyrecords.app.domain.repository.CategoryRepository
import com.moneyrecords.app.domain.repository.RecordRepository
import com.moneyrecords.app.domain.time.MonthRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** What the user has chosen to see. A custom [range] overrides [month]. */
data class RecordsQuery(
    val month: YearMonth,
    val range: ClosedRange<LocalDate>? = null,
    val accountIds: Set<Long> = emptySet(),
    val categoryIds: Set<Long> = emptySet(),
    val kind: RecordKind? = null,
    val search: String = "",
) {
    val filterCount: Int
        get() = (if (range != null) 1 else 0) + (if (accountIds.isNotEmpty()) 1 else 0) +
            (if (categoryIds.isNotEmpty()) 1 else 0) + (if (kind != null) 1 else 0)
}

data class DayGroup(
    val date: LocalDate,
    val records: List<RecordDetails>,
    val income: Long,
    val expense: Long,
)

data class RecordsState(
    val query: RecordsQuery,
    val loading: Boolean = true,
    val days: List<DayGroup> = emptyList(),
    /** Totals of everything currently shown (respects all filters). */
    val income: Long = 0,
    val expense: Long = 0,
    /** The month the shown records belong to (lags [query] until its results arrive, so the
     *  title never sits over another month's records). */
    val shownMonth: YearMonth = query.month,
    val accounts: List<Account> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
) {
    val net: Long get() = income - expense
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val records: RecordRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val query = MutableStateFlow(RecordsQuery(month = YearMonth.now(clock)))

    private val results = query
        .debounce { if (it.search.isEmpty()) 0L else 250L }
        .flatMapLatest { q -> records.observeRecords(q.toFilter()).map { q to it } }

    val state: StateFlow<RecordsState> = combine(
        query,
        results,
        accounts.observeAccounts(includeArchived = true),
        categories.observeTree(CategoryKind.EXPENSE, includeArchived = false),
        categories.observeTree(CategoryKind.INCOME, includeArchived = false),
    ) { q, (shown, list), accountList, expenseTree, incomeTree ->
        RecordsState(
            query = q,
            loading = false,
            shownMonth = shown.month,
            days = groupByDay(list),
            income = list.filter { it.record.kind == RecordKind.INCOME }.sumOf { it.record.amount },
            expense = list.filter { it.record.kind == RecordKind.EXPENSE }.sumOf { it.record.amount },
            accounts = accountList,
            expenseCategories = expenseTree,
            incomeCategories = incomeTree,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsState(query = query.value))

    fun previousMonth() = query.update { it.copy(month = it.month.minusMonths(1), range = null) }
    fun nextMonth() = query.update { it.copy(month = it.month.plusMonths(1), range = null) }
    fun setRange(range: ClosedRange<LocalDate>?) = query.update { it.copy(range = range) }
    fun setKind(kind: RecordKind?) = query.update { it.copy(kind = kind) }
    fun toggleAccount(id: Long) = query.update { it.copy(accountIds = it.accountIds.toggle(id)) }
    fun toggleCategory(id: Long) = query.update { it.copy(categoryIds = it.categoryIds.toggle(id)) }
    fun setSearch(text: String) = query.update { it.copy(search = text) }
    fun clearAccounts() = query.update { it.copy(accountIds = emptySet()) }
    fun clearCategories() = query.update { it.copy(categoryIds = emptySet()) }
    fun resetFilters() = query.update { RecordsQuery(month = it.month, search = it.search) }

    private fun RecordsQuery.toFilter(): RecordFilter {
        val zone = clock.zone
        val timeRange = range?.let { r ->
            TimeRange(
                r.start.atStartOfDay(zone).toInstant().toEpochMilli(),
                r.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        } ?: MonthRange.of(month, zone)
        return RecordFilter(
            accountIds = accountIds,
            categoryIds = categoryIds,
            kind = kind,
            range = timeRange,
            query = search.trim().ifEmpty { null },
            limit = 5_000,
        )
    }

    private fun groupByDay(list: List<RecordDetails>): List<DayGroup> =
        list.groupBy { it.record.occurredAt.atZone(clock.zone).toLocalDate() }
            .map { (date, items) ->
                DayGroup(
                    date = date,
                    records = items,
                    income = items.filter { it.record.kind == RecordKind.INCOME }.sumOf { it.record.amount },
                    expense = items.filter { it.record.kind == RecordKind.EXPENSE }.sumOf { it.record.amount },
                )
            }
            .sortedByDescending { it.date }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id
