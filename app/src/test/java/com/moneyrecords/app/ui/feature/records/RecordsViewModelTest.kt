package com.moneyrecords.app.ui.feature.records

import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.time.MonthRange
import com.moneyrecords.app.testing.FakeAccounts
import com.moneyrecords.app.testing.FakeCategories
import com.moneyrecords.app.testing.FakeRecords
import com.moneyrecords.app.testing.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-22T06:00:00Z"), zone)
    private lateinit var records: FakeRecords
    private lateinit var vm: RecordsViewModel

    private fun rec(id: Long, kind: RecordKind, amount: Long, at: String) = Record(
        id = id, kind = kind, amount = amount, accountId = TestData.cash.id,
        categoryId = if (kind == RecordKind.TRANSFER) null else TestData.food.id,
        toAccountId = if (kind == RecordKind.TRANSFER) TestData.bank.id else null,
        occurredAt = Instant.parse(at),
    )

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        records = FakeRecords(
            listOf(
                rec(1, RecordKind.EXPENSE, 300_00, "2026-11-22T05:00:00Z"),
                rec(2, RecordKind.INCOME, 1_000_00, "2026-11-22T04:00:00Z"),
                rec(3, RecordKind.EXPENSE, 50_00, "2026-11-20T10:00:00Z"),
                rec(4, RecordKind.TRANSFER, 5_000_00, "2026-11-20T09:00:00Z"),
                rec(5, RecordKind.EXPENSE, 999_00, "2026-10-05T09:00:00Z"),
            ),
        )
        vm = RecordsViewModel(records, FakeAccounts(listOf(TestData.cash, TestData.bank)), FakeCategories(TestData.trees), com.moneyrecords.app.testing.FakeSplits(), clock)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `groups the current month by day with totals excluding transfers`() = runTest {
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(YearMonth.of(2026, 11), s.query.month)
        assertEquals(listOf(LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 20)), s.days.map { it.date })
        assertEquals(1_000_00L, s.income)
        assertEquals(350_00L, s.expense)
        assertEquals(650_00L, s.net)
        assertEquals(300_00L, s.days.first().expense)
    }

    @Test fun `month switch and filters are passed to the repository`() = runTest {
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        vm.previousMonth()
        advanceUntilIdle()
        assertEquals(MonthRange.of(YearMonth.of(2026, 10), zone), records.lastFilter?.range)
        assertEquals(listOf(999_00L), vm.state.value.days.flatMap { it.records }.map { it.record.amount })

        vm.toggleAccount(TestData.bank.id)
        advanceUntilIdle()
        vm.toggleCategory(TestData.food.id)
        advanceUntilIdle()
        vm.toggleCategory(TestData.fuel.id)
        advanceUntilIdle()
        vm.toggleCategory(TestData.food.id) // toggles off
        advanceUntilIdle()
        vm.setKind(RecordKind.EXPENSE)
        advanceUntilIdle()
        val f = records.lastFilter!!
        assertEquals(setOf(TestData.bank.id), f.accountIds)
        assertEquals(setOf(TestData.fuel.id), f.categoryIds)
        assertEquals(RecordKind.EXPENSE, f.kind)
        assertEquals(3, vm.state.value.query.filterCount)

        vm.resetFilters()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.query.filterCount)
    }

    @Test fun `custom range overrides the month and covers whole days`() = runTest {
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        vm.setRange(LocalDate.of(2026, 11, 20)..LocalDate.of(2026, 11, 21))
        advanceUntilIdle()
        val range = records.lastFilter!!.range!!
        assertEquals(LocalDate.of(2026, 11, 20).atStartOfDay(zone).toInstant().toEpochMilli(), range.start)
        assertEquals(LocalDate.of(2026, 11, 22).atStartOfDay(zone).toInstant().toEpochMilli(), range.endExclusive)
        assertEquals(listOf(LocalDate.of(2026, 11, 20)), vm.state.value.days.map { it.date })
    }
}
