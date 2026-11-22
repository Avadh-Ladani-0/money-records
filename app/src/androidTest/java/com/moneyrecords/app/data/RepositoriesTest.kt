package com.moneyrecords.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneyrecords.app.data.db.DefaultCategories
import com.moneyrecords.app.data.db.MoneyRecordsDatabase
import com.moneyrecords.app.data.db.SeedCallback
import com.moneyrecords.app.data.repository.RoomAccountRepository
import com.moneyrecords.app.data.repository.RoomBudgetRepository
import com.moneyrecords.app.data.repository.RoomCategoryRepository
import com.moneyrecords.app.data.repository.RoomRecordRepository
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.Budget
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.DeleteOutcome
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordFilter
import com.moneyrecords.app.domain.model.RecordKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RepositoriesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-22T06:00:00Z"), zone)
    private val november = YearMonth.of(2026, 11)
    private val inNov = Instant.parse("2026-11-10T06:00:00Z")
    private val inOct = Instant.parse("2026-10-10T06:00:00Z")

    private lateinit var db: MoneyRecordsDatabase
    private lateinit var accounts: RoomAccountRepository
    private lateinit var categories: RoomCategoryRepository
    private lateinit var records: RoomRecordRepository
    private lateinit var budgets: RoomBudgetRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyRecordsDatabase::class.java)
            .addCallback(SeedCallback())
            .build()
        accounts = RoomAccountRepository(db, clock)
        categories = RoomCategoryRepository(db)
        records = RoomRecordRepository(db, clock)
        budgets = RoomBudgetRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun account(name: String, opening: Long = 0) =
        accounts.save(Account(name = name, type = AccountType.BANK, openingBalance = opening, iconKey = "bank"))

    private suspend fun categoryId(name: String, kind: CategoryKind) =
        categories.observeTree(kind).first().first { it.name == name }.id

    @Test fun seedInsertsDefaultCategoriesAndNoAccounts() = runTest {
        val expense = categories.observeTree(CategoryKind.EXPENSE).first()
        val income = categories.observeTree(CategoryKind.INCOME).first()
        assertEquals(DefaultCategories.all.count { it.kind == CategoryKind.EXPENSE }, expense.size)
        assertEquals(DefaultCategories.all.count { it.kind == CategoryKind.INCOME }, income.size)
        assertEquals("Food & Dining", expense.first().name)
        assertTrue(accounts.observeAccounts().first().isEmpty())
    }

    @Test fun balancesIncludeIncomeExpenseAndTransfers() = runTest {
        val bank = account("Bank", opening = 10_000_00)
        val cash = account("Cash", opening = 500_00)
        val salary = categoryId("Salary", CategoryKind.INCOME)
        val food = categoryId("Food & Dining", CategoryKind.EXPENSE)

        records.save(Record(kind = RecordKind.INCOME, amount = 50_000_00, accountId = bank, categoryId = salary, occurredAt = inNov))
        records.save(Record(kind = RecordKind.EXPENSE, amount = 1_250_50, accountId = bank, categoryId = food, occurredAt = inNov))
        records.save(Record(kind = RecordKind.TRANSFER, amount = 2_000_00, accountId = bank, toAccountId = cash, occurredAt = inNov))

        val byName = accounts.observeAccounts().first().associateBy { it.name }
        assertEquals(10_000_00 + 50_000_00 - 1_250_50 - 2_000_00, byName.getValue("Bank").balance)
        assertEquals(500_00 + 2_000_00, byName.getValue("Cash").balance)
        // Transfers move money between accounts; the total only changes by income − expense.
        assertEquals(10_500_00 + 50_000_00 - 1_250_50, accounts.observeTotalBalance().first())
    }

    @Test fun monthSummaryExcludesTransfersAndOtherMonths() = runTest {
        val bank = account("Bank")
        val cash = account("Cash")
        val food = categoryId("Food & Dining", CategoryKind.EXPENSE)
        records.save(Record(kind = RecordKind.EXPENSE, amount = 300_00, accountId = bank, categoryId = food, occurredAt = inNov))
        records.save(Record(kind = RecordKind.EXPENSE, amount = 999_00, accountId = bank, categoryId = food, occurredAt = inOct))
        records.save(Record(kind = RecordKind.TRANSFER, amount = 5_000_00, accountId = bank, toAccountId = cash, occurredAt = inNov))

        val summary = records.observeMonthSummary(november).first()
        assertEquals(0L, summary.income)
        assertEquals(300_00L, summary.expense)
    }

    @Test fun usedAccountIsArchivedUnusedIsDeleted() = runTest {
        val used = account("Used")
        val unused = account("Unused")
        val food = categoryId("Food & Dining", CategoryKind.EXPENSE)
        records.save(Record(kind = RecordKind.EXPENSE, amount = 100, accountId = used, categoryId = food, occurredAt = inNov))

        assertEquals(DeleteOutcome.ARCHIVED, accounts.deleteOrArchive(used))
        assertEquals(DeleteOutcome.DELETED, accounts.deleteOrArchive(unused))
        assertTrue(accounts.observeAccounts().first().isEmpty())
        assertTrue(accounts.get(used)!!.archived)
        assertNull(accounts.get(unused))
        // History is kept: the record still shows under the archived account.
        assertEquals("Used", records.observeRecords().first().single().accountName)
    }

    @Test fun usedCategoryIsArchivedWithItsSubcategories() = runTest {
        val food = categoryId("Food & Dining", CategoryKind.EXPENSE)
        val snacks = categories.save(Category(name = "Snacks", kind = CategoryKind.EXPENSE, parentId = food, iconKey = "food"))
        val unusedId = categories.save(Category(name = "Temp", kind = CategoryKind.EXPENSE, iconKey = "other"))

        assertEquals(DeleteOutcome.ARCHIVED, categories.deleteOrArchive(food)) // has a child
        assertTrue(categories.get(snacks)!!.archived)
        assertEquals(DeleteOutcome.DELETED, categories.deleteOrArchive(unusedId))
        assertFalse(categories.observeTree(CategoryKind.EXPENSE).first().any { it.id == food })
    }

    @Test fun invalidRecordsAreRejected() = runTest {
        val bank = account("Bank")
        val salary = categoryId("Salary", CategoryKind.INCOME)
        suspend fun rejected(record: Record) = runCatching { records.save(record) }.exceptionOrNull() is IllegalArgumentException
        assertTrue(rejected(Record(kind = RecordKind.EXPENSE, amount = 0, accountId = bank, occurredAt = inNov)))
        assertTrue(rejected(Record(kind = RecordKind.TRANSFER, amount = 100, accountId = bank, toAccountId = bank, occurredAt = inNov)))
        assertTrue(rejected(Record(kind = RecordKind.EXPENSE, amount = 100, accountId = bank, categoryId = salary, occurredAt = inNov)))
        assertTrue(records.observeRecords().first().isEmpty())
    }

    @Test fun searchMatchesNoteCategoryAndEscapesWildcards() = runTest {
        val bank = account("Bank")
        val food = categoryId("Food & Dining", CategoryKind.EXPENSE)
        val fuel = categoryId("Fuel", CategoryKind.EXPENSE)
        records.save(Record(kind = RecordKind.EXPENSE, amount = 100, accountId = bank, categoryId = food, occurredAt = inNov, note = "Dinner 50% off"))
        records.save(Record(kind = RecordKind.EXPENSE, amount = 200, accountId = bank, categoryId = fuel, occurredAt = inNov, note = "Petrol"))

        assertEquals(1, records.observeRecords(RecordFilter(query = "dinner")).first().size)
        assertEquals(1, records.observeRecords(RecordFilter(query = "fuel")).first().size)
        assertEquals(1, records.observeRecords(RecordFilter(query = "50%")).first().size)
        // "%" is a literal character, not "match anything".
        assertEquals(1, records.observeRecords(RecordFilter(query = "%")).first().size)
        assertEquals(2, records.observeRecords(RecordFilter(accountId = bank)).first().size)
    }

    @Test fun budgetProgressCountsSubcategorySpendAndReplacesLimit() = runTest {
        val bank = account("Bank")
        val food = categoryId("Food & Dining", CategoryKind.EXPENSE)
        val snacks = categories.save(Category(name = "Snacks", kind = CategoryKind.EXPENSE, parentId = food, iconKey = "food"))
        records.save(Record(kind = RecordKind.EXPENSE, amount = 400_00, accountId = bank, categoryId = food, occurredAt = inNov))
        records.save(Record(kind = RecordKind.EXPENSE, amount = 200_00, accountId = bank, categoryId = snacks, occurredAt = inNov))

        budgets.save(Budget(categoryId = food, month = november, limit = 1_000_00))
        budgets.save(Budget(categoryId = food, month = november, limit = 500_00)) // replaces
        budgets.save(Budget(categoryId = null, month = november, limit = 20_000_00))

        val progress = budgets.observeProgress(november).first()
        assertEquals(2, progress.size)
        val overall = progress.first()
        assertNull(overall.budget.categoryId)
        assertEquals(600_00L, overall.spent)
        val foodBudget = progress[1]
        assertEquals(500_00L, foodBudget.budget.limit)
        assertEquals(600_00L, foodBudget.spent)
        assertTrue(foodBudget.isOver)
    }
}
