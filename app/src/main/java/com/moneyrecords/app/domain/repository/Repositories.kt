package com.moneyrecords.app.domain.repository

import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.Budget
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.DeleteOutcome
import com.moneyrecords.app.domain.model.MonthSummary
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.model.RecurringRule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

interface AccountRepository {
    /** Accounts with live balances, in display order. */
    fun observeAccounts(includeArchived: Boolean = false): Flow<List<Account>>
    /** Sum of balances of active accounts, in paise. */
    fun observeTotalBalance(): Flow<Long>
    suspend fun get(id: Long): Account?
    /** Inserts when [Account.id] is 0, otherwise updates. Returns the id. */
    suspend fun save(account: Account): Long
    suspend fun setArchived(id: Long, archived: Boolean)
    /** Deletes an unused account; archives one that records or rules still reference. */
    suspend fun deleteOrArchive(id: Long): DeleteOutcome
}

interface CategoryRepository {
    /** Top-level categories of [kind] with their subcategories in [Category.children]. */
    fun observeTree(kind: CategoryKind, includeArchived: Boolean = false): Flow<List<Category>>
    suspend fun get(id: Long): Category?
    suspend fun save(category: Category): Long
    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun deleteOrArchive(id: Long): DeleteOutcome

    /**
     * Creates a combined category from two top-level categories of the same type. It is stored as a
     * subcategory of [firstId], so its records also count toward the first category (not the second).
     * Returns the new category's id.
     */
    suspend fun combine(firstId: Long, secondId: Long, name: String): Long
}

/** Icon keys for combined categories: "combo:<first>+<second>". */
object ComboIcon {
    private const val PREFIX = "combo:"
    fun key(first: String, second: String) = "$PREFIX$first+$second"
    fun parts(key: String?): Pair<String, String>? =
        key?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.split('+')?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
    fun isCombo(key: String?) = parts(key) != null
}

interface RecordRepository {
    fun observeRecords(filter: RecordFilter = RecordFilter()): Flow<List<RecordDetails>>
    fun observeMonthSummary(month: YearMonth): Flow<MonthSummary>
    suspend fun get(id: Long): Record?
    /** Validates, then inserts when [Record.id] is 0 or updates. Returns the id. */
    suspend fun save(record: Record): Long
    suspend fun delete(id: Long)
}

interface RecurringRepository {
    fun observeRules(): Flow<List<RecurringRule>>
    suspend fun get(id: Long): RecurringRule?
    suspend fun dueOn(date: LocalDate): List<RecurringRule>
    suspend fun save(rule: RecurringRule): Long
    suspend fun delete(id: Long)
}

interface BudgetRepository {
    /** Budgets for [month] with what has been spent against each. Overall budget first. */
    fun observeProgress(month: YearMonth): Flow<List<BudgetProgress>>
    /** One budget per (category, month): saving again replaces the limit. */
    suspend fun save(budget: Budget): Long
    suspend fun delete(id: Long)
}
