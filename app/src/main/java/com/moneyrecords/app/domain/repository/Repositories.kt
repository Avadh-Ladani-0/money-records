package com.moneyrecords.app.domain.repository

import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.Budget
import com.moneyrecords.app.domain.model.BudgetOverview
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.BudgetTarget
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.DeleteOutcome
import com.moneyrecords.app.domain.model.MonthSummary
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.model.DueOccurrence
import com.moneyrecords.app.domain.model.RecurringRule
import com.moneyrecords.app.domain.model.RecurringRuleDetails
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
    /** Number of records dated on [date] (local time). */
    suspend fun countOn(date: LocalDate): Int
    /** Validates, then inserts when [Record.id] is 0 or updates. Returns the id. */
    suspend fun save(record: Record): Long
    suspend fun delete(id: Long)
}

interface RecurringRepository {
    /** All rules with names, active first, soonest first. */
    fun observeRules(): Flow<List<RecurringRuleDetails>>
    suspend fun get(id: Long): RecurringRule?
    suspend fun getDetails(id: Long): RecurringRuleDetails?
    /** Occurrences due on or before [today] (missed ones included), oldest first. */
    fun observeDue(today: LocalDate): Flow<List<DueOccurrence>>
    suspend fun save(rule: RecurringRule): Long
    /**
     * Records the rule's earliest due occurrence and moves it to the next date, in one transaction.
     * [override] replaces the planned record (e.g. a different amount from "Edit"). Returns the record id.
     */
    suspend fun confirm(ruleId: Long, override: Record? = null): Long
    /** Skips the earliest due occurrence without recording it. */
    suspend fun skip(ruleId: Long)
    suspend fun setActive(id: Long, active: Boolean)
    /** Deletes the rule; records it created stay (they just lose the link). */
    suspend fun delete(id: Long)
}

interface BudgetRepository {
    /** Budgets in effect for [month] (carried forward from earlier months) with spending against each. */
    fun observeOverview(month: YearMonth): Flow<BudgetOverview>
    /** Same as [observeOverview], flattened: overall, categories, then "Everything else". */
    fun observeProgress(month: YearMonth): Flow<List<BudgetProgress>>
    /** Sets the limit for [Budget.target] from [Budget.month] onwards (replacing one set in that same month). */
    suspend fun save(budget: Budget): Long
    /** Ends a budget from [month] onwards; earlier months keep it. */
    suspend fun stop(target: BudgetTarget, month: YearMonth)
    /** What [target] would have counted as spent in [month] (e.g. "last month you spent ₹X"). */
    suspend fun spentIn(target: BudgetTarget, month: YearMonth): Long

    /**
     * Records that can be counted back against [target] in [month]: income (into the account, for
     * account budgets) and, for account budgets, transfers into the account.
     */
    suspend fun offsetCandidates(target: BudgetTarget, month: YearMonth): List<RecordDetails>
    /** Ids of the records currently counted back against [target] in [month]. */
    suspend fun selectedOffsets(target: BudgetTarget, month: YearMonth): Set<Long>
    /** Replaces [target]'s counted-back records for [month] with [recordIds]. */
    suspend fun setOffsets(target: BudgetTarget, month: YearMonth, recordIds: Set<Long>)
}

/**
 * Bills paid for a group. Your share and each friend's part are real expenses; a friend's part
 * stays spending until their money comes back and is moved to the account that paid.
 */
interface SplitRepository {
    /** Saves [expense] (its amount = the whole bill) split by [spec]; returns the bill's id. */
    suspend fun saveSplit(expense: com.moneyrecords.app.domain.model.Record, spec: com.moneyrecords.app.domain.split.SplitSpec): Long
    /** Turns a split bill back into a plain expense of its whole amount (only before any repayment). */
    suspend fun removeSplit(expenseId: Long)
    /** The current split of a split bill, or null. */
    suspend fun splitOf(expenseId: Long): com.moneyrecords.app.domain.split.SplitSpec?
    /** True once anything has been paid back (the amount and split can no longer change). */
    suspend fun hasRepayments(expenseId: Long): Boolean
    /** Copies a bill's category, date and note to its friends' parts. */
    suspend fun updateBillDetails(expenseId: Long, categoryId: Long?, occurredAt: java.time.Instant, note: String)
    /** A friend paid back [amount] of one part into [accountId]. */
    suspend fun receive(legId: Long, amount: Long, accountId: Long)
    /** [person] paid back [amount] for several bills: spread oldest first. */
    suspend fun receiveFromPerson(person: String, amount: Long, accountId: Long)
    /** "Didn't actually arrive": removes a waiting repayment; the amount is owed again. */
    suspend fun unreceive(repaymentId: Long)
    fun observeOpen(): Flow<List<com.moneyrecords.app.domain.split.OpenPart>>
    fun observeAwaitingMove(): Flow<List<com.moneyrecords.app.domain.split.AwaitingMove>>
    /**
     * Moves [amount] of what's waiting in [heldInId] back to [payerId] (oldest first): the friends'
     * parts shrink by it and the repayments go. Returns a token for [undoMove].
     */
    suspend fun moveCollected(payerId: Long, heldInId: Long, amount: Long): Any
    suspend fun undoMove(token: Any)
    /** Why [recordId] can't be deleted right now (a repayment for it is waiting), or null. */
    suspend fun deleteBlockReason(recordId: Long): String?
    fun observePeople(): Flow<List<String>>
}
