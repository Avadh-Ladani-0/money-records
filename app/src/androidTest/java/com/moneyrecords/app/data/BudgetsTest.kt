package com.moneyrecords.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneyrecords.app.data.db.MIGRATION_1_2
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
}
