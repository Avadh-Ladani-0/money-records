package com.moneyrecords.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneyrecords.app.data.db.MIGRATION_1_2
import com.moneyrecords.app.data.db.MIGRATION_2_3
import com.moneyrecords.app.data.db.MoneyRecordsDatabase
import com.moneyrecords.app.data.db.SeedCallback
import com.moneyrecords.app.data.repository.RoomAccountRepository
import com.moneyrecords.app.data.repository.RoomBudgetRepository
import com.moneyrecords.app.data.repository.RoomCategoryRepository
import com.moneyrecords.app.data.repository.RoomRecordRepository
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.Budget
import com.moneyrecords.app.domain.model.BudgetScope
import com.moneyrecords.app.domain.model.BudgetTarget
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class BudgetsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-22T06:00:00Z"), zone)
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private val inNov = Instant.parse("2026-11-10T06:00:00Z")

    @get:Rule
    val migrationHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MoneyRecordsDatabase::class.java)

    private lateinit var db: MoneyRecordsDatabase
    private lateinit var budgets: RoomBudgetRepository
    private lateinit var records: RoomRecordRepository
    private lateinit var categories: RoomCategoryRepository
    private var bank = 0L

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyRecordsDatabase::class.java)
            .addCallback(SeedCallback()).build()
        budgets = RoomBudgetRepository(db, clock)
        records = RoomRecordRepository(db, clock)
        categories = RoomCategoryRepository(db)
        bank = RoomAccountRepository(db, clock).save(Account(name = "Bank", type = AccountType.BANK, iconKey = "bank"))
    }

    @After fun tearDown() = db.close()

    private suspend fun account(name: String) =
        RoomAccountRepository(db, clock).save(Account(name = name, type = AccountType.CARD, iconKey = "card"))
    private suspend fun income(accountId: Long, paise: Long, at: Instant = inNov) = records.save(
        Record(kind = RecordKind.INCOME, amount = paise, accountId = accountId,
            categoryId = categories.observeTree(CategoryKind.INCOME).first().first { it.name == "Refunds" }.id, occurredAt = at),
    )
    private suspend fun transfer(from: Long, to: Long, paise: Long, at: Instant = inNov) =
        records.save(Record(kind = RecordKind.TRANSFER, amount = paise, accountId = from, toAccountId = to, occurredAt = at))

    private suspend fun cat(name: String) = categories.observeTree(CategoryKind.EXPENSE).first().first { it.name == name }.id
    private suspend fun spend(categoryId: Long, paise: Long) =
        records.save(Record(kind = RecordKind.EXPENSE, amount = paise, accountId = bank, categoryId = categoryId, occurredAt = inNov))

    @Test fun migrationFrom1To2KeepsOverallBudgets() {
        val name = "migration-test"
        migrationHelper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO budgets (id, categoryId, yearMonth, limitAmount) VALUES (1, NULL, 202611, 2000000)")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)
        migrated.query("SELECT scope FROM budgets WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("OVERALL", c.getString(0))
        }
    }

    @Test fun budgetsCarryForwardAndEditsApplyFromThatMonth() = runTest {
        val food = cat("Food & Dining")
        budgets.save(Budget(categoryId = food, month = oct, limit = 5_000_00))
        assertEquals(5_000_00L, budgets.observeOverview(nov).first().categories.single().budget.limit)

        budgets.save(Budget(categoryId = food, month = dec, limit = 6_000_00))
        assertEquals(5_000_00L, budgets.observeOverview(nov).first().categories.single().budget.limit) // Nov unchanged
        assertEquals(6_000_00L, budgets.observeOverview(dec).first().categories.single().budget.limit)
        assertTrue(budgets.observeOverview(YearMonth.of(2026, 9)).first().isEmpty)                     // not before Oct
    }

    @Test fun stoppingHidesFromThatMonthButKeepsEarlierMonths() = runTest {
        budgets.save(Budget(categoryId = null, month = oct, limit = 20_000_00))
        budgets.stop(BudgetTarget.Overall, dec)
        assertEquals(20_000_00L, budgets.observeOverview(nov).first().overall?.budget?.limit)
        assertNull(budgets.observeOverview(dec).first().overall)
        assertNull(budgets.observeOverview(YearMonth.of(2027, 3)).first().overall)

        // Stopping in the only month it was set removes it completely.
        val food = cat("Food & Dining")
        budgets.save(Budget(categoryId = food, month = nov, limit = 1_000_00))
        budgets.stop(BudgetTarget.category(food), nov)
        assertTrue(budgets.observeOverview(nov).first().categories.isEmpty())
    }

    @Test fun everythingElseCoversCategoriesWithoutTheirOwnBudget() = runTest {
        val food = cat("Food & Dining")
        val rent = cat("Rent")
        val fuel = cat("Fuel")
        val homeFood = categories.combine(rent, food, "Home-Food")
        spend(food, 400_00)
        spend(homeFood, 300_00)   // counts in Rent
        spend(fuel, 200_00)
        spend(cat("Groceries"), 100_00)

        budgets.save(Budget(categoryId = rent, month = nov, limit = 1_000_00))
        budgets.save(Budget(categoryId = null, month = nov, limit = 1_500_00, scope = BudgetScope.REST))

        val o = budgets.observeOverview(nov).first()
        assertEquals(300_00L, o.categories.single().spent)          // Rent includes Home-Food
        val rest = o.rest!!
        assertEquals(400_00L + 200_00 + 100_00, rest.spent)          // everything except Rent
        assertEquals(13, rest.coveredCount)                          // 14 main expense categories − Rent
        assertTrue(o.suggestions.isEmpty())                          // covered by "Everything else"

        // Giving Food its own budget moves it out of "Everything else".
        budgets.save(Budget(categoryId = food, month = nov, limit = 500_00))
        assertEquals(300_00L, budgets.observeOverview(nov).first().rest!!.spent)
    }

    @Test fun suggestionsListUnbudgetedSpendingWhenThereIsNoRestBudget() = runTest {
        val food = cat("Food & Dining")
        val fuel = cat("Fuel")
        spend(food, 400_00)
        spend(fuel, 900_00)
        budgets.save(Budget(categoryId = food, month = nov, limit = 1_000_00))
        val suggestions = budgets.observeOverview(nov).first().suggestions
        assertEquals(listOf("Fuel"), suggestions.map { it.name })
        assertEquals(900_00L, suggestions.single().spent)
        assertEquals(900_00L, budgets.spentIn(BudgetTarget.category(fuel), nov))
        assertEquals(1_300_00L, budgets.spentIn(BudgetTarget.Overall, nov))
    }

    @Test fun migrationFrom2To3KeepsBudgetsAndAddsOffsets() {
        val name = "migration-test-3"
        migrationHelper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO categories (id, name, kind, parentId, iconKey, archived, sortOrder) VALUES (1, 'Food', 'EXPENSE', NULL, 'food', 0, 0)")
            execSQL("INSERT INTO budgets (id, scope, categoryId, yearMonth, limitAmount) VALUES (1, 'CATEGORY', 1, 202611, 500000)")
            execSQL("INSERT INTO budgets (id, scope, categoryId, yearMonth, limitAmount) VALUES (2, 'REST', NULL, 202611, 150000)")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 3, true, MIGRATION_1_2, MIGRATION_2_3)
        migrated.query("SELECT id, scope, categoryId, accountId, limitAmount FROM budgets ORDER BY id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("CATEGORY", c.getString(1)); assertEquals(1L, c.getLong(2)); assertTrue(c.isNull(3)); assertEquals(500000L, c.getLong(4))
            assertTrue(c.moveToNext())
            assertEquals("REST", c.getString(1))
        }
        migrated.query("SELECT COUNT(*) FROM budget_offsets").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
    }

    @Test fun accountBudgetCountsExpensesAndTransfersOutNotTransfersIn() = runTest {
        val card = account("HDFC Card")
        records.save(Record(kind = RecordKind.EXPENSE, amount = 12_000_00, accountId = card, categoryId = cat("Shopping"), occurredAt = inNov))
        transfer(from = card, to = bank, paise = 2_000_00)       // out: counts
        transfer(from = bank, to = card, paise = 10_000_00)      // in: ignored until picked
        budgets.save(Budget(categoryId = null, month = nov, limit = 20_000_00, accountId = card))

        val cardBudget = budgets.observeOverview(nov).first().accounts.single()
        assertEquals("HDFC Card", cardBudget.accountName)
        assertEquals(14_000_00L, cardBudget.spent)
    }

    @Test fun pickedTransfersInAndIncomeReduceSpentOnlyInTheirMonth() = runTest {
        val card = account("HDFC Card")
        records.save(Record(kind = RecordKind.EXPENSE, amount = 12_000_00, accountId = card, categoryId = cat("Shopping"), occurredAt = inNov))
        val billPayment = transfer(from = bank, to = card, paise = 10_000_00)
        val cashback = income(card, 500_00)
        val elsewhere = income(bank, 300_00)                     // not into the card: not a candidate
        val octIncome = income(card, 900_00, at = Instant.parse("2026-10-10T06:00:00Z"))
        val target = BudgetTarget.account(card)
        budgets.save(Budget(categoryId = null, month = oct, limit = 20_000_00, accountId = card))

        val candidates = budgets.offsetCandidates(target, nov).map { it.record.id }.toSet()
        assertEquals(setOf(billPayment, cashback), candidates)
        assertTrue(elsewhere !in candidates && octIncome !in candidates)

        budgets.setOffsets(target, nov, setOf(billPayment, cashback))
        val p = budgets.observeOverview(nov).first().accounts.single()
        assertEquals(10_500_00L, p.offset)
        assertEquals(12_000_00L - 10_500_00L, p.spent)
        assertEquals(setOf(billPayment, cashback), budgets.selectedOffsets(target, nov))
        assertEquals(0L, budgets.observeOverview(oct).first().accounts.single().offset)   // other months unaffected

        // Income can be counted back on a category budget too; deleting the record drops the offset.
        val food = cat("Food & Dining")
        spend(food, 1_000_00)
        budgets.save(Budget(categoryId = food, month = nov, limit = 5_000_00))
        budgets.setOffsets(BudgetTarget.category(food), nov, setOf(elsewhere))
        assertEquals(700_00L, budgets.observeOverview(nov).first().categories.single().spent)
        records.delete(elsewhere)
        assertEquals(1_000_00L, budgets.observeOverview(nov).first().categories.single().spent)
    }

    @Test fun otherAccountsCoversOnlyAccountsWithoutTheirOwnBudget() = runTest {
        val card = account("HDFC Card")
        val wallet = account("Paytm")
        records.save(Record(kind = RecordKind.EXPENSE, amount = 1_000_00, accountId = card, categoryId = cat("Shopping"), occurredAt = inNov))
        records.save(Record(kind = RecordKind.EXPENSE, amount = 200_00, accountId = wallet, categoryId = cat("Fuel"), occurredAt = inNov))
        records.save(Record(kind = RecordKind.EXPENSE, amount = 300_00, accountId = bank, categoryId = cat("Fuel"), occurredAt = inNov))
        budgets.save(Budget(categoryId = null, month = nov, limit = 5_000_00, accountId = card))
        budgets.save(Budget(categoryId = null, month = nov, limit = 1_000_00, scope = BudgetScope.OTHER_ACCOUNTS))

        val other = budgets.observeOverview(nov).first().otherAccounts!!
        assertEquals(500_00L, other.spent)          // wallet + bank, not the card
        assertEquals(2, other.coveredCount)         // Bank and Paytm: every active account except the card
    }

    @Test fun accountWithABudgetIsArchivedNotDeleted() = runTest {
        val accounts = RoomAccountRepository(db, clock)
        val card = account("HDFC Card")
        budgets.save(Budget(categoryId = null, month = nov, limit = 5_000_00, accountId = card))
        assertEquals(com.moneyrecords.app.domain.model.DeleteOutcome.ARCHIVED, accounts.deleteOrArchive(card))
    }
}
