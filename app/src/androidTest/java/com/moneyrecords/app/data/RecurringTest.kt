package com.moneyrecords.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneyrecords.app.data.db.MoneyRecordsDatabase
import com.moneyrecords.app.data.db.SeedCallback
import com.moneyrecords.app.data.repository.RoomAccountRepository
import com.moneyrecords.app.data.repository.RoomCategoryRepository
import com.moneyrecords.app.data.repository.RoomRecordRepository
import com.moneyrecords.app.data.repository.RoomRecurringRepository
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.Frequency
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.RecurringRule
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
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RecurringTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-11-22T06:00:00Z"), zone)
    private val today = LocalDate.of(2026, 11, 22)

    private lateinit var db: MoneyRecordsDatabase
    private lateinit var records: RoomRecordRepository
    private lateinit var recurring: RoomRecurringRepository
    private var bank = 0L
    private var rent = 0L

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyRecordsDatabase::class.java)
            .addCallback(SeedCallback()).build()
        records = RoomRecordRepository(db, clock)
        recurring = RoomRecurringRepository(db, records, clock)
        bank = RoomAccountRepository(db, clock).save(Account(name = "Bank", type = AccountType.BANK, iconKey = "bank"))
        rent = RoomCategoryRepository(db).observeTree(CategoryKind.EXPENSE).first().first { it.name == "Rent" }.id
    }

    @After fun tearDown() = db.close()

    private suspend fun monthlyRent(next: LocalDate, end: LocalDate? = null) = recurring.save(
        RecurringRule(
            kind = RecordKind.EXPENSE, amount = 15_000_00, accountId = bank, categoryId = rent, note = "Rent",
            frequency = Frequency.MONTHLY, startDate = LocalDate.of(2026, 10, 31), endDate = end, nextRunDate = next,
        ),
    )

    @Test fun missedOccurrencesAreDueAndConfirmRecordsAndAdvances() = runTest {
        val id = monthlyRent(next = LocalDate.of(2026, 10, 31))
        val due = recurring.observeDue(today).first()
        assertEquals(listOf(LocalDate.of(2026, 10, 31)), due.map { it.date })   // 30 Nov not reached yet
        assertEquals("Rent", due.single().details.categoryName)

        val recordId = recurring.confirm(id)
        val record = records.get(recordId)!!
        assertEquals(15_000_00L, record.amount)
        assertEquals(id, record.recurringRuleId)
        assertEquals(LocalDate.of(2026, 10, 31), record.occurredAt.atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 30), recurring.get(id)!!.nextRunDate)   // month-end kept
        assertTrue(recurring.observeDue(today).first().isEmpty())
    }

    @Test fun skipAdvancesWithoutRecording() = runTest {
        // The rule's anchor day is the 31st, so skipping 31 Oct lands on 30 Nov (month-end).
        val id = monthlyRent(next = LocalDate.of(2026, 10, 31))
        recurring.skip(id)
        assertEquals(LocalDate.of(2026, 11, 30), recurring.get(id)!!.nextRunDate)
        assertTrue(records.observeRecords().first().isEmpty())
    }

    @Test fun pausedRulesAreNotDueAndEndDateStopsThem() = runTest {
        val paused = monthlyRent(next = LocalDate.of(2026, 11, 1))
        recurring.setActive(paused, false)
        assertTrue(recurring.observeDue(today).first().isEmpty())

        val ending = monthlyRent(next = LocalDate.of(2026, 11, 1), end = LocalDate.of(2026, 11, 15))
        recurring.confirm(ending)
        assertFalse(recurring.get(ending)!!.active)                                    // next date is past the end
    }

    @Test fun confirmWithOverrideKeepsEditsAndDeletingRuleKeepsRecords() = runTest {
        val id = monthlyRent(next = LocalDate.of(2026, 11, 1))
        val planned = records.observeRecords().first()
        assertTrue(planned.isEmpty())
        val recordId = recurring.confirm(
            id,
            com.moneyrecords.app.domain.model.Record(
                kind = RecordKind.EXPENSE, amount = 15_500_00, accountId = bank, categoryId = rent,
                occurredAt = Instant.parse("2026-11-02T05:00:00Z"), note = "Rent + maintenance",
            ),
        )
        assertEquals(15_500_00L, records.get(recordId)!!.amount)
        recurring.delete(id)
        assertNull(recurring.get(id))
        val kept = records.get(recordId)!!
        assertNull(kept.recurringRuleId)                                                // link cleared, record kept
        assertEquals(0, records.countOn(today))
        assertEquals(1, records.countOn(LocalDate.of(2026, 11, 2)))
    }
}
