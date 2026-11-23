package com.moneyrecords.app.ui.feature.record

import androidx.lifecycle.SavedStateHandle
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.repository.RecordDefaults
import com.moneyrecords.app.testing.FakeAccounts
import com.moneyrecords.app.testing.FakeCategories
import com.moneyrecords.app.testing.FakeAlerts
import com.moneyrecords.app.testing.FakePrefs
import com.moneyrecords.app.testing.FakeRecurring
import com.moneyrecords.app.testing.FakeRecords
import com.moneyrecords.app.testing.TestData
import com.moneyrecords.app.ui.components.KeypadKey
import com.moneyrecords.app.ui.navigation.RouteArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RecordEditorViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-22T06:00:00Z"), zone) // 11:30 IST
    private lateinit var records: FakeRecords
    private lateinit var prefs: FakePrefs
    private lateinit var recurring: FakeRecurring
    private lateinit var alerts: FakeAlerts

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        records = FakeRecords()
        prefs = FakePrefs()
        recurring = FakeRecurring(records)
        alerts = FakeAlerts()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private val splits = com.moneyrecords.app.testing.FakeSplits()

    private fun vm(vararg args: Pair<String, Long>) = RecordEditorViewModel(
        SavedStateHandle(args.toMap()),
        records,
        FakeAccounts(listOf(TestData.cash, TestData.bank)),
        FakeCategories(TestData.trees),
        prefs,
        recurring,
        alerts,
        splits,
        clock,
    )

    private fun RecordEditorViewModel.type(keys: String) = keys.forEach { c ->
        onKey(
            when (c) {
                '.' -> KeypadKey.Dot
                '+', '−', '×', '÷' -> KeypadKey.Operator(c)
                else -> KeypadKey.Digit(c)
            },
        )
    }

    @Test fun `new record pre-selects remembered account and category`() {
        prefs.defaults[RecordKind.EXPENSE] = RecordDefaults(accountId = TestData.bank.id, categoryId = TestData.fuel.id)
        val s = vm().state.value
        assertEquals(RecordKind.EXPENSE, s.kind)
        assertEquals(TestData.bank.id, s.accountId)
        assertEquals(TestData.fuel.id, s.categoryId)
        assertEquals(LocalDate.of(2026, 11, 22), s.date)
    }

    @Test fun `without memory it picks the first account and no category`() {
        val s = vm().state.value
        assertEquals(TestData.cash.id, s.accountId)
        assertNull(s.categoryId)
        assertFalse(s.canSave)
    }

    @Test fun `keypad builds a calculation and saves the result`() {
        val vm = vm()
        vm.type("1250+300")
        assertEquals("1250+300", vm.state.value.amountText)
        assertEquals("= ₹1,550", vm.state.value.preview)
        vm.onCategory(TestData.food.id)
        vm.onNote("Dinner")
        vm.save()

        val saved = records.records.value.single()
        assertEquals(1_550_00L, saved.amount)
        assertEquals(TestData.food.id, saved.categoryId)
        assertEquals("Dinner", saved.note)
        // Remembered for next time.
        assertEquals(TestData.food.id, prefs.defaults[RecordKind.EXPENSE]?.categoryId)
    }

    @Test fun `keypad keeps input well formed`() {
        val vm = vm()
        vm.type("0")
        vm.type("5")          // leading zero replaced
        vm.type("..")         // only one dot
        vm.type("999")        // max two decimals
        vm.type("+×")         // operator replaced
        assertEquals("5.99×", vm.state.value.amountText)
        vm.onKey(KeypadKey.Backspace)
        assertEquals("5.99", vm.state.value.amountText)
        vm.onKey(KeypadKey.Clear)
        assertEquals("", vm.state.value.amountText)
    }

    @Test fun `missing category is reported instead of saving`() {
        val vm = vm()
        vm.type("100")
        vm.save()
        assertEquals("Choose a category", vm.state.value.error)
        assertTrue(records.records.value.isEmpty())
    }

    @Test fun `transfer switches to two accounts and needs no category`() {
        val vm = vm()
        vm.onKind(RecordKind.TRANSFER)
        val s = vm.state.value
        assertEquals(TestData.cash.id, s.accountId)
        assertEquals(TestData.bank.id, s.toAccountId)
        // Choosing the destination as source swaps them.
        vm.onAccount(TestData.bank.id)
        assertEquals(TestData.bank.id, vm.state.value.accountId)
        assertEquals(TestData.cash.id, vm.state.value.toAccountId)
        vm.type("500")
        vm.save()
        val saved = records.records.value.single()
        assertEquals(RecordKind.TRANSFER, saved.kind)
        assertNull(saved.categoryId)
    }

    @Test fun `duplicate copies fields but uses now and a new id`() {
        records.records.value = listOf(
            Record(id = 7, kind = RecordKind.EXPENSE, amount = 249_50, accountId = TestData.bank.id, categoryId = TestData.fuel.id,
                occurredAt = Instant.parse("2026-11-01T03:00:00Z"), note = "Petrol"),
        )
        val vm = vm(RouteArgs.DUPLICATE_OF to 7L)
        val s = vm.state.value
        assertEquals(EditorMode.DUPLICATE, s.mode)
        assertEquals("249.5", s.amountText)
        assertEquals(LocalDate.of(2026, 11, 22), s.date)
        vm.save()
        assertEquals(2, records.records.value.size)
    }

    @Test fun `edit keeps the original date and updates in place`() {
        records.records.value = listOf(
            Record(id = 7, kind = RecordKind.INCOME, amount = 50_000_00, accountId = TestData.bank.id, categoryId = TestData.salary.id,
                occurredAt = Instant.parse("2026-11-01T03:00:00Z")),
        )
        val vm = vm(RouteArgs.RECORD_ID to 7L)
        assertEquals(LocalDate.of(2026, 11, 1), vm.state.value.date)
        vm.onKey(KeypadKey.Backspace)
        vm.save()
        val saved = records.records.value.single()
        assertEquals(7L, saved.id)
        assertEquals(5_000_00L, saved.amount)
    }

    @Test fun `recent categories come from the latest records of the same kind`() {
        records.records.value = listOf(
            Record(id = 1, kind = RecordKind.EXPENSE, amount = 100, accountId = 1, categoryId = TestData.fuel.id, occurredAt = Instant.parse("2026-11-20T03:00:00Z")),
            Record(id = 2, kind = RecordKind.EXPENSE, amount = 100, accountId = 1, categoryId = 11, occurredAt = Instant.parse("2026-11-21T03:00:00Z")),
            Record(id = 3, kind = RecordKind.EXPENSE, amount = 100, accountId = 1, categoryId = TestData.fuel.id, occurredAt = Instant.parse("2026-11-19T03:00:00Z")),
            Record(id = 4, kind = RecordKind.INCOME, amount = 100, accountId = 1, categoryId = TestData.salary.id, occurredAt = Instant.parse("2026-11-22T03:00:00Z")),
        )
        val vm = vm()
        assertEquals(listOf(11L, TestData.fuel.id), vm.state.value.recentCategoryIds)
        vm.onCategory(11)
        assertEquals("Snacks", vm.state.value.selectedCategory?.name)
        vm.onKind(RecordKind.INCOME)
        assertEquals(listOf(TestData.salary.id), vm.state.value.recentCategoryIds)
    }

    @Test fun `creating a category from the picker selects it`() {
        val vm = vm()
        vm.createCategory("Pets", "other")
        val s = vm.state.value
        assertEquals("Pets", s.selectedCategory?.name)
        vm.type("120")
        vm.save()
        assertEquals(s.categoryId, records.records.value.single().categoryId)
    }

    @Test fun `combining two categories creates one inside the first and selects it`() {
        val vm = vm()
        vm.combineCategories(TestData.fuel.id, TestData.food.id, "Fuel-Food")
        val s = vm.state.value
        val combined = s.selectedCategory!!
        assertEquals("Fuel-Food", combined.name)
        assertEquals(TestData.fuel.id, combined.parentId)
        assertEquals("combo:fuel+food", combined.iconKey)
    }

    @Test fun `repeat creates a rule linked to the first record and checks budgets`() {
        val vm = vm()
        vm.type("15000")
        vm.onCategory(TestData.fuel.id)
        vm.onRepeat(RepeatSpec(com.moneyrecords.app.domain.model.Frequency.MONTHLY))
        vm.save()
        val rule = recurring.rules.value.single()
        assertEquals(15_000_00L, rule.amount)
        assertEquals(LocalDate.of(2026, 11, 22), rule.startDate)
        assertEquals(LocalDate.of(2026, 12, 22), rule.nextRunDate)
        assertEquals(rule.id, records.records.value.single().recurringRuleId)
        assertEquals(listOf(java.time.YearMonth.of(2026, 11)), alerts.checked)
    }

    @Test fun `editing a due recurring item confirms it with the edited amount`() {
        val start = LocalDate.of(2026, 11, 1)
        recurring.rules.value = listOf(
            com.moneyrecords.app.domain.model.RecurringRule(
                id = 5, kind = RecordKind.EXPENSE, amount = 2_000_00, accountId = TestData.cash.id, categoryId = TestData.fuel.id,
                frequency = com.moneyrecords.app.domain.model.Frequency.MONTHLY, startDate = start, nextRunDate = start,
            ),
        )
        val vm = vm(RouteArgs.FROM_RULE_ID to 5L)
        assertEquals(EditorMode.RECURRING_DUE, vm.state.value.mode)
        assertEquals(start, vm.state.value.date)
        vm.onKey(KeypadKey.Clear); vm.type("2350")
        vm.save()
        val saved = records.records.value.single()
        assertEquals(2_350_00L, saved.amount)
        assertEquals(5L, saved.recurringRuleId)
        assertEquals(LocalDate.of(2026, 12, 1), recurring.rules.value.single().nextRunDate)
    }

    @Test fun `once on a date creates a one-time rule`() {
        val vm = vm()
        vm.type("4000")
        vm.onCategory(TestData.fuel.id)
        vm.onRepeat(RepeatSpec(com.moneyrecords.app.domain.model.Frequency.MONTHLY, nextDate = LocalDate.of(2027, 3, 15), once = true))
        vm.save()
        val rule = recurring.rules.value.single()
        assertEquals(LocalDate.of(2027, 3, 15), rule.nextRunDate)
        assertEquals(rule.startDate, rule.endDate)
        assertTrue(com.moneyrecords.app.domain.recurring.Recurrence.isOnce(rule))
    }

    @Test fun `editing a record from a repeat shows that repeat`() {
        val start = LocalDate.of(2026, 11, 22)
        recurring.rules.value = listOf(
            com.moneyrecords.app.domain.model.RecurringRule(
                id = 3, kind = RecordKind.EXPENSE, amount = 4_000_00, accountId = TestData.cash.id, categoryId = TestData.fuel.id,
                frequency = com.moneyrecords.app.domain.model.Frequency.MONTHLY, startDate = start, nextRunDate = start.plusMonths(1),
            ),
        )
        records.records.value = listOf(
            Record(id = 9, kind = RecordKind.EXPENSE, amount = 4_000_00, accountId = TestData.cash.id, categoryId = TestData.fuel.id,
                occurredAt = Instant.parse("2026-11-22T06:00:00Z"), recurringRuleId = 3),
        )
        val vm = vm(RouteArgs.RECORD_ID to 9L)
        assertEquals(3L, vm.state.value.linkedRule?.rule?.id)
        // Changing the repeat elsewhere refreshes what the editor shows.
        recurring.rules.value = recurring.rules.value.map { it.copy(active = false) }
        assertEquals(false, vm.state.value.linkedRule?.rule?.active)
        // A plain record has no linked repeat.
        records.records.value = records.records.value + Record(id = 10, kind = RecordKind.EXPENSE, amount = 100, accountId = TestData.cash.id,
            categoryId = TestData.fuel.id, occurredAt = Instant.parse("2026-11-22T06:00:00Z"))
        assertEquals(null, vm(RouteArgs.RECORD_ID to 10L).state.value.linkedRule)
    }
}
