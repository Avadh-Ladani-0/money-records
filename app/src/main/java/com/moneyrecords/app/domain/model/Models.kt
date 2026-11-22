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

/** What a budget covers. */
enum class BudgetScope {
    /** All expenses. */
    OVERALL,
    /** One main expense category (its subcategories and combined categories included). */
    CATEGORY,
    /** "Everything else": every expense category that has no budget of its own. */
    REST,
}

/** Identifies a budget line independent of month. */
data class BudgetTarget(val scope: BudgetScope, val categoryId: Long? = null) {
    init { require((scope == BudgetScope.CATEGORY) == (categoryId != null)) { "Only category budgets have a category" } }
    companion object {
        val Overall = BudgetTarget(BudgetScope.OVERALL)
        val Rest = BudgetTarget(BudgetScope.REST)
        fun category(id: Long) = BudgetTarget(BudgetScope.CATEGORY, id)
    }
}

/**
 * A monthly limit that applies from [month] onwards until changed or stopped.
 */
data class Budget(
    val id: Long = 0,
    /** Null for OVERALL and REST. */
    val categoryId: Long?,
    val month: YearMonth,
    /** Paise. */
    val limit: Long,
    val scope: BudgetScope = if (categoryId == null) BudgetScope.OVERALL else BudgetScope.CATEGORY,
) {
    val target: BudgetTarget get() = BudgetTarget(scope, categoryId)
}

data class BudgetProgress(
    val budget: Budget,
    val categoryName: String?,
    val categoryIconKey: String?,
    val spent: Long,
    /** For REST: how many main expense categories it currently covers. */
    val coveredCount: Int = 0,
) {
    val fraction: Float get() = if (budget.limit <= 0) 0f else spent.toFloat() / budget.limit
    val remaining: Long get() = budget.limit - spent
    val isOver: Boolean get() = spent > budget.limit
    /** 80% or more used, but not over. */
    val isNearLimit: Boolean get() = !isOver && fraction >= 0.8f
}

/** A main expense category with spending this month but no budget of its own. */
data class BudgetSuggestion(val categoryId: Long, val name: String, val iconKey: String, val spent: Long)

data class BudgetOverview(
    val month: YearMonth,
    val overall: BudgetProgress?,
    /** Category budgets, most used first. */
    val categories: List<BudgetProgress>,
    val rest: BudgetProgress?,
    /** Empty while a REST budget exists (those categories are already covered). */
    val suggestions: List<BudgetSuggestion>,
) {
    /** Overall first, then categories, then "Everything else". */
    val all: List<BudgetProgress> get() = listOfNotNull(overall) + categories + listOfNotNull(rest)
    val isEmpty: Boolean get() = all.isEmpty()
}
