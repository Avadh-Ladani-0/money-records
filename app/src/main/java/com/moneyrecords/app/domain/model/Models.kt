package com.moneyrecords.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

enum class AccountType { CASH, BANK, CARD, WALLET, OTHER }

enum class CategoryKind { INCOME, EXPENSE }

enum class RecordKind { INCOME, EXPENSE, TRANSFER }

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** What happened when the user asked to delete something that records may still use. */
enum class DeleteOutcome { DELETED, ARCHIVED }

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    /** Paise. */
    val openingBalance: Long = 0,
    val iconKey: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    /** Paise; computed from opening balance and records, never stored. */
    val balance: Long = openingBalance,
)

data class Category(
    val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val parentId: Long? = null,
    val iconKey: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    /** Filled only in tree results. */
    val children: List<Category> = emptyList(),
)

data class Record(
    val id: Long = 0,
    val kind: RecordKind,
    /** Paise, always > 0; [kind] decides the direction. */
    val amount: Long,
    val accountId: Long,
    /** Destination account — transfers only. */
    val toAccountId: Long? = null,
    /** Null for transfers. */
    val categoryId: Long? = null,
    val occurredAt: Instant,
    val note: String = "",
    val recurringRuleId: Long? = null,
)

/** A record with the names the UI shows next to it. */
data class RecordDetails(
    val record: Record,
    val accountName: String,
    val toAccountName: String?,
    val categoryName: String?,
    val categoryIconKey: String?,
)

data class RecordFilter(
    /** Empty = all accounts. Matches records from or to any of these accounts. */
    val accountIds: Set<Long> = emptySet(),
    /** Empty = all categories. Matches these categories and their subcategories. */
    val categoryIds: Set<Long> = emptySet(),
    val kind: RecordKind? = null,
    val range: TimeRange? = null,
    val query: String? = null,
    val limit: Int = 500,
)

/** Half-open epoch-millis range [start, endExclusive). */
data class TimeRange(val start: Long, val endExclusive: Long) {
    init { require(endExclusive >= start) { "Range ends before it starts" } }
}

data class MonthSummary(val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

data class RecurringRule(
    val id: Long = 0,
    val kind: RecordKind,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val frequency: Frequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val nextRunDate: LocalDate = startDate,
    val active: Boolean = true,
)

data class Budget(
    val id: Long = 0,
    /** Null = overall budget for all expenses. */
    val categoryId: Long?,
    val month: YearMonth,
    /** Paise. */
    val limit: Long,
)

data class BudgetProgress(
    val budget: Budget,
    val categoryName: String?,
    val categoryIconKey: String?,
    val spent: Long,
) {
    val fraction: Float get() = if (budget.limit <= 0) 0f else spent.toFloat() / budget.limit
    val remaining: Long get() = budget.limit - spent
    val isOver: Boolean get() = spent > budget.limit
}
