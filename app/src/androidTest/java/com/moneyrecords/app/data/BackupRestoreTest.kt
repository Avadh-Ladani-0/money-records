package com.moneyrecords.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneyrecords.app.backup.BackupCodec
import com.moneyrecords.app.backup.BackupData
import com.moneyrecords.app.backup.BackupFile
import com.moneyrecords.app.backup.parentsFirst
import com.moneyrecords.app.backup.toEntity
import com.moneyrecords.app.backup.toRow
import com.moneyrecords.app.data.db.MoneyRecordsDatabase
import com.moneyrecords.app.data.db.SeedCallback
import com.moneyrecords.app.data.repository.RoomAccountRepository
import com.moneyrecords.app.data.repository.RoomCategoryRepository
import com.moneyrecords.app.data.repository.RoomRecordRepository
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

/** Export → wipe → restore through the backup file keeps every row, id and link. In-memory only. */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private lateinit var db: MoneyRecordsDatabase
    private val clock = Clock.systemUTC()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyRecordsDatabase::class.java)
            .addCallback(SeedCallback()).build()
    }

    @After fun tearDown() = db.close()

    private suspend fun export(): BackupData = db.withTransaction {
        val d = db.backupDao()
        BackupData(
            d.accounts().map { it.toRow() }, d.categories().map { it.toRow() }, d.recurringRules().map { it.toRow() },
            d.records().map { it.toRow() }, d.budgets().map { it.toRow() }, d.budgetOffsets().map { it.toRow() },
        )
    }

    @Test fun roundTripThroughAFileRestoresEverything() = runTest {
        val categories = RoomCategoryRepository(db)
        val bank = RoomAccountRepository(db, clock).save(Account(name = "SBI", type = AccountType.BANK, iconKey = "bank"))
        val tree = categories.observeTree(CategoryKind.EXPENSE).first()
        val food = tree.first { it.name == "Food & Dining" }.id
        val rent = tree.first { it.name == "Rent" }.id
        categories.combine(rent, food, "Home-Food")
        RoomRecordRepository(db, clock).save(
            Record(kind = RecordKind.EXPENSE, amount = 1_323_00, accountId = bank, categoryId = food, occurredAt = Instant.now(), note = "Lunch"),
        )
        val before = export()

        val bytes = BackupCodec.encode(BackupFile(appVersion = "test", dbVersion = 3, createdAt = 0, data = before), "pw-123456")
        val restored = BackupCodec.decode(bytes, "pw-123456").data

        val dao = db.backupDao()
        db.withTransaction {
            dao.clearBudgetOffsets(); dao.clearBudgets(); dao.clearRecords(); dao.clearRecurringRules(); dao.clearCategories(); dao.clearAccounts()
        }
        assertTrue(export().records.isEmpty())
        db.withTransaction {
            dao.insertAccounts(restored.accounts.map { it.toEntity() })
            dao.insertCategories(restored.categories.parentsFirst().map { it.toEntity() })
            dao.insertRecurringRules(restored.recurringRules.map { it.toEntity() })
            dao.insertRecords(restored.records.map { it.toEntity() })
            dao.insertBudgets(restored.budgets.map { it.toEntity() })
            dao.insertBudgetOffsets(restored.budgetOffsets.map { it.toEntity() })
        }
        assertEquals(before, export())
    }
}
