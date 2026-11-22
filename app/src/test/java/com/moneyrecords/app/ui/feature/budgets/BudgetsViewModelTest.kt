package com.moneyrecords.app.ui.feature.budgets

import com.moneyrecords.app.domain.model.Budget
import com.moneyrecords.app.domain.model.BudgetOverview
import com.moneyrecords.app.domain.model.BudgetProgress
import com.moneyrecords.app.domain.model.BudgetScope
import com.moneyrecords.app.domain.model.BudgetTarget
import com.moneyrecords.app.testing.FakeAccounts
import com.moneyrecords.app.testing.FakeBudgets
import com.moneyrecords.app.testing.FakeCategories
import com.moneyrecords.app.testing.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-22T06:00:00Z"), zone) // 22 Nov
    private val nov = YearMonth.of(2026, 11)
    private lateinit var budgets: FakeBudgets

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        budgets = FakeBudgets()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun progress(limit: Long, spent: Long, scope: BudgetScope = BudgetScope.OVERALL, categoryId: Long? = null, month: YearMonth = nov) =
        BudgetProgress(Budget(categoryId = categoryId, month = month, limit = limit, scope = scope), null, null, spent)

    @Test fun `daily allowance depends on the month`() {
        val today = LocalDate.of(2026, 11, 22)
        // Current month: ₹9,000 left over 9 days (22nd..30th) = ₹1,000/day.
        assertEquals(1_000_00L, dailyAllowance(progress(30_000_00, 21_000_00), nov, today))
        // Over budget: nothing left per day.
        assertEquals(0L, dailyAllowance(progress(10_000_00, 12_000_00), nov, today))
        // Future month: limit spread over the whole month (31 days in December).
        assertEquals(1_000_00L, dailyAllowance(progress(31_000_00, 0), YearMonth.of(2026, 12), today))
        // Past month: not meaningful.
        assertNull(dailyAllowance(progress(30_000_00, 1), YearMonth.of(2026, 10), today))
    }

    @Test fun `80 percent and over flags`() {
        assertTrue(progress(1_000_00, 800_00).isNearLimit)
        assertFalse(progress(1_000_00, 799_00).isNearLimit)
        val over = progress(1_000_00, 1_000_01)
        assertTrue(over.isOver)
        assertFalse(over.isNearLimit)
    }

    @Test fun `state follows the viewed month and saves apply from it`() = runTest {
        val overall = progress(20_000_00, 5_000_00)
        budgets.overviews.value = mapOf(nov to BudgetOverview(nov, overall, emptyList(), null, emptyList()))
        val vm = BudgetsViewModel(budgets, FakeCategories(TestData.trees), FakeAccounts(listOf(TestData.cash, TestData.bank)), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertEquals(nov, vm.state.value.month)
        assertTrue(vm.state.value.isCurrentMonth)
        assertEquals(setOf(BudgetTarget.Overall), vm.state.value.budgetedTargets)
        assertEquals(listOf("Food", "Fuel"), vm.state.value.mainCategories.map { it.name })

        vm.nextMonth()
        assertEquals(YearMonth.of(2026, 12), vm.state.value.month)
        assertNull(vm.state.value.overview?.overall)

        var error: String? = "unset"
        vm.save(BudgetTarget.Rest, 1_500_00) { error = it }
        assertNull(error)
        val saved = budgets.saved.single()
        assertEquals(BudgetScope.REST, saved.scope)
        assertEquals(YearMonth.of(2026, 12), saved.month)

        vm.save(BudgetTarget.Overall, null) { error = it }
        assertEquals("Enter an amount above ₹0", error)

        vm.stop(BudgetTarget.category(TestData.food.id))
        assertEquals(BudgetTarget.category(TestData.food.id) to YearMonth.of(2026, 12), budgets.stopped.single())

        // Account budgets save with their account, and editing stores the counted-back records.
        assertEquals(listOf("Cash", "Bank"), vm.state.value.accounts.map { it.name })
        vm.save(BudgetTarget.account(TestData.bank.id), 20_000_00, offsets = setOf(7L, 9L)) { error = it }
        assertNull(error)
        val accountBudget = budgets.saved.last()
        assertEquals(BudgetScope.ACCOUNT, accountBudget.scope)
        assertEquals(TestData.bank.id, accountBudget.accountId)
        assertEquals(setOf(7L, 9L), budgets.offsets[BudgetTarget.account(TestData.bank.id) to YearMonth.of(2026, 12)])
    }
}
