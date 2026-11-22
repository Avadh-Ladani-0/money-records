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
