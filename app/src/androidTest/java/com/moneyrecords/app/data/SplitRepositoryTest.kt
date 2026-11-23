package com.moneyrecords.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneyrecords.app.data.db.MIGRATION_1_2
import com.moneyrecords.app.data.db.MIGRATION_2_3
import com.moneyrecords.app.data.db.MIGRATION_3_4
import com.moneyrecords.app.data.db.MoneyRecordsDatabase
import com.moneyrecords.app.data.db.SeedCallback
import com.moneyrecords.app.data.repository.RoomAccountRepository
import com.moneyrecords.app.data.repository.RoomCategoryRepository
import com.moneyrecords.app.data.repository.RoomRecordRepository
import com.moneyrecords.app.data.repository.RoomSplitRepository
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.split.Split
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Split bills end to end on an in-memory database (never the app's real data). */
@RunWith(AndroidJUnit4::class)
class SplitRepositoryTest {
    @get:Rule val migrationHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MoneyRecordsDatabase::class.java)

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-22T06:00:00Z"), zone)
    private val nov = YearMonth.of(2026, 11)
    private lateinit var db: MoneyRecordsDatabase
    private lateinit var accounts: RoomAccountRepository
    private lateinit var records: RoomRecordRepository
    private lateinit var splits: RoomSplitRepository
    private var sbi = 0L
    private var lite = 0L
    private var food = 0L

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyRecordsDatabase::class.java)
            .addCallback(SeedCallback()).build()
        accounts = RoomAccountRepository(db, clock)
        records = RoomRecordRepository(db, clock)
        splits = RoomSplitRepository(db, clock)
        sbi = accounts.save(Account(name = "SBI", type = AccountType.BANK, iconKey = "bank", openingBalance = 10_000_00))
        lite = accounts.save(Account(name = "UPI Lite", type = AccountType.WALLET, iconKey = "wallet", openingBalance = 2_000_00))
        food = RoomCategoryRepository(db).observeTree(CategoryKind.EXPENSE).first().first { it.name == "Food & Dining" }.id
    }

    @After fun tearDown() = db.close()

    private suspend fun balance(id: Long) = accounts.observeAccounts().first().first { it.id == id }.balance

    @Test fun friendsPartIsSpendingUntilMovedBack() = runTest {
        val bill = Record(kind = RecordKind.EXPENSE, amount = 300_00, accountId = lite, categoryId = food, occurredAt = clock.instant(), note = "Dinner")
        val id = splits.saveSplit(bill, Split.equal(300_00, 2, listOf("Rahul")))

        // Two real expenses: your share and Rahul's part; UPI Lite paid the whole bill.
        assertEquals(300_00L, records.observeMonthSummary(nov).first().expense)
        assertEquals(2, records.observeRecords().first().size)
        assertEquals(1_700_00L, balance(lite))
        assertEquals(listOf("SBI", "UPI Lite"), accounts.observeAccounts().first().map { it.name }.sorted())

        // Rahul pays ₹150 into SBI: not income; deleting the bill is now blocked.
        val leg = splits.observeOpen().first().single()
        splits.receive(leg.legId, 150_00, sbi)
        assertEquals(10_150_00L, balance(sbi))
        assertEquals(0L, records.observeMonthSummary(nov).first().income)
        assertTrue(splits.deleteBlockReason(id)!!.contains("SBI"))
        assertTrue(splits.observeOpen().first().isEmpty())

        // Move ₹100 of it: the part shrinks to ₹50, ₹50 keeps waiting in SBI.
        val token = splits.moveCollected(lite, sbi, 100_00)
        assertEquals(10_050_00L, balance(sbi))
        assertEquals(1_800_00L, balance(lite))
        assertEquals(50_00L, splits.observeAwaitingMove().first().single().total)
        assertEquals(200_00L, records.observeMonthSummary(nov).first().expense)

        // Undo puts it back exactly; then move everything.
        splits.undoMove(token)
        assertEquals(10_150_00L, balance(sbi))
        assertEquals(300_00L, records.observeMonthSummary(nov).first().expense)
        splits.moveCollected(lite, sbi, 150_00)

        // As if Rahul had paid straight back to UPI Lite: only your share is left.
        assertEquals(10_000_00L, balance(sbi))
        assertEquals(1_850_00L, balance(lite))
        assertEquals(150_00L, records.observeMonthSummary(nov).first().expense)
        assertEquals(1, records.observeRecords().first().size)
        assertEquals(null, splits.deleteBlockReason(id))

        records.delete(id)
        assertTrue(records.observeRecords().first().isEmpty())
        assertEquals(2_000_00L, balance(lite))
    }

    @Test fun onePaymentCoversSeveralBillsAndZeroShareShowsOnlyThePart() = runTest {
        fun bill(amount: Long, note: String) = Record(kind = RecordKind.EXPENSE, amount = amount, accountId = lite, categoryId = food, occurredAt = clock.instant(), note = note)
        splits.saveSplit(bill(300_00, "Lunch"), Split.equal(300_00, 2, listOf("Rahul")))
        // Paid entirely for Rahul: your share ₹0 — only his part is a visible record.
        splits.saveSplit(bill(200_00, "Movie"), com.moneyrecords.app.domain.split.SplitSpec(200_00, 0, listOf(com.moneyrecords.app.domain.split.SplitPart("Rahul", 200_00))))
        assertEquals(3, records.observeRecords().first().size)
        assertEquals(500_00L, records.observeMonthSummary(nov).first().expense)

        // Rahul sends ₹250 straight to UPI Lite for both: oldest first, settled at once.
        splits.receiveFromPerson("rahul", 250_00, lite)
        val left = splits.observeOpen().first()
        assertEquals(1, left.size)
        assertEquals(100_00L, left.single().outstanding)
        assertTrue(splits.observeAwaitingMove().first().isEmpty())
        assertEquals(1_500_00L + 250_00L, balance(lite))
    }

    @Test fun migrationFrom3To4KeepsRecords() {
        val name = "migration-test-4"
        migrationHelper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO accounts (id, name, type, openingBalance, iconKey, archived, sortOrder, createdAt) VALUES (1, 'SBI', 'BANK', 0, 'bank', 0, 0, 0)")
            execSQL("INSERT INTO records (id, kind, amount, accountId, toAccountId, categoryId, occurredAt, note, recurringRuleId, createdAt, updatedAt) VALUES (1, 'EXPENSE', 17900, 1, NULL, NULL, 0, 'Other', NULL, 0, 0)")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        migrated.query("SELECT amount, splitOf, awaitingMove, splitTotal FROM records").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(17900L, c.getLong(0)); assertTrue(c.isNull(1)); assertEquals(0, c.getInt(2)); assertTrue(c.isNull(3))
        }
        migrated.query("SELECT system FROM accounts").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
    }
}
