package com.moneyrecords.app.notify

import com.moneyrecords.app.domain.model.Budget
import com.moneyrecords.app.domain.model.BudgetOverview
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.BudgetScope
import com.moneyrecords.app.testing.FakeBudgets
import com.moneyrecords.app.testing.FakePrefs
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class BudgetAlertCheckerTest {
    private val nov = YearMonth.of(2026, 11)
    private val budgets = FakeBudgets()
    private val prefs = FakePrefs()
    private val posted = mutableListOf<Pair<String, Int>>()
    private val checker = BudgetAlertChecker(budgets, prefs, object : BudgetAlertPoster {
        override fun post(progress: BudgetProgress, threshold: Int) { posted += (progress.categoryName ?: progress.budget.scope.name) to threshold }
    })

    private fun food(spent: Long) = BudgetProgress(Budget(categoryId = 1, month = nov, limit = 1_000_00), "Food", "food", spent)
    private fun card(spent: Long) = BudgetProgress(Budget(categoryId = null, month = nov, limit = 1_000_00, accountId = 9, scope = BudgetScope.ACCOUNT), null, null, spent)
    private fun set(vararg categories: BudgetProgress, accounts: List<BudgetProgress> = emptyList()) =
        budgets.overviews.update { mapOf(nov to BudgetOverview(nov, null, categories.toList(), null, emptyList(), accounts)) }

    @Test fun `80 percent alerts once, then over alerts once`() = runTest {
        set(food(700_00)); checker.checkAfterRecord(nov)
        assertTrue(posted.isEmpty())
        set(food(850_00)); checker.checkAfterRecord(nov); checker.checkAfterRecord(nov)
        assertEquals(listOf("Food" to 80), posted)
        set(food(1_200_00)); checker.checkAfterRecord(nov); checker.checkAfterRecord(nov)
        assertEquals(listOf("Food" to 80, "Food" to 100), posted)
    }

    @Test fun `jumping straight past 100 sends only the over alert and covers account budgets`() = runTest {
        set(accounts = listOf(card(1_500_00)))
        checker.checkAfterRecord(nov)
        set(accounts = listOf(card(1_600_00)))
        checker.checkAfterRecord(nov)
        assertEquals(listOf("ACCOUNT" to 100), posted)
    }

    @Test fun `nothing is sent when budget alerts are off`() = runTest {
        prefs.notificationPrefs.update { it.copy(budgetAlerts = false) }
        set(food(2_000_00)); checker.checkAfterRecord(nov)
        assertTrue(posted.isEmpty())
    }
}
