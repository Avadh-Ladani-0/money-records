package com.moneyrecords.app.domain.insights

import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.Frequency
import com.moneyrecords.app.domain.model.RecurringRule
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.RecurringRuleDetails
import java.time.LocalDate

/** One main category's share of a month's spending. [share] is 0..1 of all expenses. */
data class CategoryShare(
    val categoryId: Long,
    val name: String,
    val iconKey: String,
    val spent: Long,
    val share: Float,
)

object Insights {

    /**
     * Expenses grouped by main category, largest first. Subcategories and combined categories count
     * toward the main category they live in (Home-Food counts toward Home), matching budgets.
     * Records whose category is unknown are grouped under "Uncategorised".
     */
    fun topCategories(records: List<RecordDetails>, tree: List<Category>, limit: Int = 4): List<CategoryShare> {
        val parentOf = buildMap {
            tree.forEach { parent ->
                put(parent.id, parent)
                parent.children.forEach { put(it.id, parent) }
            }
        }
        val expenses = records.filter { it.record.kind == RecordKind.EXPENSE }
        val total = expenses.sumOf { it.record.amount }
        if (total <= 0) return emptyList()
        return expenses
            .groupBy { r -> r.record.categoryId?.let(parentOf::get) }
            .map { (parent, rs) ->
                val spent = rs.sumOf { it.record.amount }
                CategoryShare(
                    categoryId = parent?.id ?: 0,
                    name = parent?.name ?: "Uncategorised",
                    iconKey = parent?.iconKey ?: "other",
                    spent = spent,
                    share = spent.toFloat() / total,
                )
            }
            .sortedByDescending { it.spent }
            .take(limit)
    }

    /** Active recurring items not yet due, soonest first ([today] excluded — those are "due"). */
    fun upcoming(rules: List<RecurringRuleDetails>, today: LocalDate, limit: Int = 3): List<RecurringRuleDetails> =
        rules
            .filter { it.rule.active && it.rule.nextRunDate.isAfter(today) }
            .filter { r -> r.rule.endDate == null || !r.rule.nextRunDate.isAfter(r.rule.endDate) }
            .sortedBy { it.rule.nextRunDate }
            .take(limit)

    /** What a rule costs per month on average (a yearly ₹1,200 is ₹100 a month). */
    fun monthlyEquivalent(rule: RecurringRule): Long {
        val perYear = when (rule.frequency) {
            Frequency.DAILY -> rule.amount * 365 / rule.interval
            Frequency.WEEKLY -> rule.amount * 52 / rule.interval
            Frequency.MONTHLY -> rule.amount * 12 / rule.interval
            Frequency.YEARLY -> rule.amount / rule.interval
        }
        return perYear / 12
    }

    /** Monthly cost of all active recurring expenses ("once" items excluded — they don't recur). */
    fun monthlyCommitment(rules: List<RecurringRuleDetails>): Long =
        rules.map { it.rule }
            .filter { it.active && it.kind == RecordKind.EXPENSE && !(it.endDate != null && it.endDate == it.startDate) }
            .sumOf(::monthlyEquivalent)
}
