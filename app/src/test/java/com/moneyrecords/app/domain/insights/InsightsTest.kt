package com.moneyrecords.app.domain.insights

import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.Frequency
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.RecurringRule
import com.moneyrecords.app.domain.model.RecurringRuleDetails
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.testing.TestData
import com.moneyrecords.app.ui.feature.accounts.AccountFlow
import com.moneyrecords.app.ui.feature.accounts.accountFlows
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class InsightsTest {
    private fun rec(kind: RecordKind, amount: Long, category: Long? = null, account: Long = 1, to: Long? = null) = RecordDetails(
        Record(kind = kind, amount = amount, accountId = account, toAccountId = to, categoryId = category, occurredAt = Instant.EPOCH),
        "Cash", null, null, null,
    )

    @Test fun `top categories group subcategories into their main category`() {
        val home = Category(id = 30, name = "Home", kind = CategoryKind.EXPENSE, iconKey = "rent",
            children = listOf(Category(id = 31, name = "Home-Food", kind = CategoryKind.EXPENSE, parentId = 30, iconKey = "combo:rent+food")))
        val tree = listOf(TestData.food, TestData.fuel, home)
        val top = Insights.topCategories(
            listOf(
                rec(RecordKind.EXPENSE, 100_00, 10),
                rec(RecordKind.EXPENSE, 50_00, 11), // Snacks → Food
                rec(RecordKind.EXPENSE, 200_00, 31), // Home-Food → Home, not Food
                rec(RecordKind.EXPENSE, 50_00, 12),
                rec(RecordKind.INCOME, 9_999_00, 20), // income ignored
            ),
            tree,
        )
        assertEquals(listOf("Home", "Food", "Fuel"), top.map { it.name })
        assertEquals(listOf(200_00L, 150_00L, 50_00L), top.map { it.spent })
        assertEquals(0.5f, top.first().share, 0.001f)
    }

    @Test fun `no spending means no categories`() {
        assertEquals(emptyList<CategoryShare>(), Insights.topCategories(emptyList(), listOf(TestData.food)))
    }

    private fun rule(id: Long, next: LocalDate, active: Boolean = true, freq: Frequency = Frequency.MONTHLY, amount: Long = 1_000_00, end: LocalDate? = null) =
        RecurringRuleDetails(
            RecurringRule(id = id, kind = RecordKind.EXPENSE, amount = amount, accountId = 1, categoryId = 10, note = "",
                frequency = freq, startDate = next, endDate = end, nextRunDate = next, active = active),
            "Cash", null, "Food", "food",
        )

    @Test fun `upcoming skips due, paused and ended items and sorts by date`() {
        val today = LocalDate.of(2026, 11, 23)
        val list = listOf(
            rule(1, today),                         // due today → "Due now", not upcoming
            rule(2, today.plusDays(5)),
            rule(3, today.plusDays(1)),
            rule(4, today.plusDays(2), active = false),
            rule(5, today.plusDays(9)),
            rule(6, today.plusDays(20)),
        )
        assertEquals(listOf(3L, 2L, 5L), Insights.upcoming(list, today).map { it.rule.id })
    }

    @Test fun `monthly commitment averages every frequency and skips once-only items`() {
        val d = LocalDate.of(2026, 11, 1)
        val rules = listOf(
            rule(1, d, freq = Frequency.MONTHLY, amount = 1_000_00),
            rule(2, d, freq = Frequency.YEARLY, amount = 12_000_00),   // ₹1,000 a month
            rule(3, d, freq = Frequency.WEEKLY, amount = 120_00),      // ₹520 a month
            rule(4, d, amount = 5_000_00, end = d),                    // once on a date
            rule(5, d, active = false),
        )
        assertEquals(2_520_00L, Insights.monthlyCommitment(rules))
    }

    @Test fun `account flows count transfers on both sides`() {
        val flows = accountFlows(
            listOf(
                rec(RecordKind.INCOME, 500_00, account = 1),
                rec(RecordKind.EXPENSE, 200_00, account = 1),
                rec(RecordKind.TRANSFER, 100_00, account = 1, to = 2),
            ),
        )
        assertEquals(AccountFlow(500_00, 300_00), flows[1])
        assertEquals(AccountFlow(100_00, 0), flows[2])
    }

    @Test fun `amounts are spoken in words a screen reader reads well`() {
        assertEquals("1,502 rupees", Money.spoken(1_502_00))
        assertEquals("minus 179 rupees", Money.spoken(-179_00))
        assertEquals("1 rupee 50 paise", Money.spoken(1_50))
        assertEquals("1,00,000 rupees", Money.spoken(1_00_000_00))
    }
}
