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
    /** Split payments: see RecordEntity. [splitTotal] set = this expense was split ([amount] = your share). */
    val splitOf: Long? = null,
    val person: String? = null,
    val awaitingMove: Boolean = false,
    val splitTotal: Long? = null,
) {
    val isSplit: Boolean get() = splitTotal != null
    /** A friend's part of a split bill — spending until it's paid back and moved. */
    val isToCollect: Boolean get() = kind == RecordKind.EXPENSE && splitOf != null
    /** A repayment from a friend waiting to be moved (shown in lists as "Repaid"). */
    val isRepayment: Boolean get() = kind == RecordKind.TRANSFER && splitOf != null && awaitingMove
}

/** A record with the names the UI shows next to it. */
data class RecordDetails(
    val record: Record,
    val accountName: String,
    val toAccountName: String?,
    val categoryName: String?,
    val categoryIconKey: String?,
    /** For a split expense: what friends still owe on it. */
    val splitOutstanding: Long = 0,
    /** For a friend's part: repaid into another account, waiting to be moved back. */
    val splitWaiting: Long = 0,
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
    /** One account: expenses paid from it + transfers out of it. */
    ACCOUNT,
    /** "Other accounts": every account that has no budget of its own. */
    OTHER_ACCOUNTS,
}

/** Identifies a budget line independent of month. */
data class BudgetTarget(val scope: BudgetScope, val categoryId: Long? = null, val accountId: Long? = null) {
    init {
        require((scope == BudgetScope.CATEGORY) == (categoryId != null)) { "Only category budgets have a category" }
        require((scope == BudgetScope.ACCOUNT) == (accountId != null)) { "Only account budgets have an account" }
    }
    val isAccountBased: Boolean get() = scope == BudgetScope.ACCOUNT || scope == BudgetScope.OTHER_ACCOUNTS

    companion object {
        val Overall = BudgetTarget(BudgetScope.OVERALL)
        val Rest = BudgetTarget(BudgetScope.REST)
        val OtherAccounts = BudgetTarget(BudgetScope.OTHER_ACCOUNTS)
        fun category(id: Long) = BudgetTarget(BudgetScope.CATEGORY, categoryId = id)
        fun account(id: Long) = BudgetTarget(BudgetScope.ACCOUNT, accountId = id)
    }
}

/**
 * A monthly limit that applies from [month] onwards until changed or stopped.
 */
/** A recurring rule with the names the UI shows. */
data class RecurringRuleDetails(
    val rule: RecurringRule,
    val accountName: String,
    val toAccountName: String?,
    val categoryName: String?,
    val categoryIconKey: String?,
) {
    /** The note when there is one ("Coffee", "Netflix"), otherwise the category or transfer target. */
    val title: String
        get() = rule.note.takeIf { it.isNotBlank() }
            ?: if (rule.kind == RecordKind.TRANSFER) "Transfer to ${toAccountName ?: "?"}" else categoryName ?: "Recurring"
}

/** One due occurrence of a recurring rule, waiting for Add / Skip. */
data class DueOccurrence(val details: RecurringRuleDetails, val date: LocalDate)

data class Budget(
    val id: Long = 0,
    /** Null for OVERALL and REST. */
    val categoryId: Long?,
    val month: YearMonth,
    /** Paise. */
    val limit: Long,
    /** Only for ACCOUNT budgets. */
    val accountId: Long? = null,
    val scope: BudgetScope = when {
        accountId != null -> BudgetScope.ACCOUNT
        categoryId != null -> BudgetScope.CATEGORY
        else -> BudgetScope.OVERALL
    },
) {
    val target: BudgetTarget get() = BudgetTarget(scope, categoryId, accountId)
}

data class BudgetProgress(
    val budget: Budget,
    val categoryName: String?,
    val categoryIconKey: String?,
    /** Net spent: the gross amount minus [offset] (never below zero). */
    val spent: Long,
    /** REST: main expense categories covered; OTHER_ACCOUNTS: accounts covered. */
    val coveredCount: Int = 0,
    val accountName: String? = null,
    val accountIconKey: String? = null,
    /** Total of hand-picked records counted back this month. */
    val offset: Long = 0,
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
    /** Account budgets, most used first. */
    val accounts: List<BudgetProgress> = emptyList(),
    val otherAccounts: BudgetProgress? = null,
) {
    /** Overall, categories, "Everything else", accounts, then "Other accounts". */
    val all: List<BudgetProgress>
        get() = listOfNotNull(overall) + categories + listOfNotNull(rest) + accounts + listOfNotNull(otherAccounts)
    val isEmpty: Boolean get() = all.isEmpty()
}
