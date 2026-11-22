package com.moneyrecords.app.domain.recurring

import com.moneyrecords.app.domain.model.Frequency
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.RecurringRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun `daily and weekly with intervals`() {
        assertEquals(d(2026, 11, 25), Recurrence.next(d(2026, 11, 22), Frequency.DAILY, 3, d(2026, 11, 22)))
        assertEquals(d(2026, 12, 6), Recurrence.next(d(2026, 11, 22), Frequency.WEEKLY, 2, d(2026, 11, 22)))
    }

    @Test fun `monthly keeps the start day and clamps to month end`() {
        val start = d(2027, 1, 31)
        val feb = Recurrence.next(start, Frequency.MONTHLY, 1, start)
        assertEquals(d(2027, 2, 28), feb)
        assertEquals(d(2027, 3, 31), Recurrence.next(feb, Frequency.MONTHLY, 1, start))   // back to the 31st
        assertEquals(d(2027, 4, 30), Recurrence.next(d(2027, 1, 31), Frequency.MONTHLY, 3, start))
    }

    @Test fun `yearly on 29 Feb falls on 28 Feb in non-leap years`() {
        val start = d(2028, 2, 29)
        assertEquals(d(2029, 2, 28), Recurrence.next(start, Frequency.YEARLY, 1, start))
        assertEquals(d(2032, 2, 29), Recurrence.next(d(2031, 2, 28), Frequency.YEARLY, 1, start))
    }

    private fun rule(next: LocalDate, end: LocalDate? = null, active: Boolean = true) = RecurringRule(
        id = 1, kind = RecordKind.EXPENSE, amount = 100, accountId = 1, categoryId = 1,
        frequency = Frequency.MONTHLY, interval = 1, startDate = d(2026, 9, 1), endDate = end,
        nextRunDate = next, active = active,
    )

    @Test fun `due dates catch up missed occurrences and respect end date and pause`() {
        assertEquals(listOf(d(2026, 10, 1), d(2026, 11, 1)), Recurrence.dueDates(rule(d(2026, 10, 1)), today = d(2026, 11, 22)))
        assertEquals(listOf(d(2026, 10, 1)), Recurrence.dueDates(rule(d(2026, 10, 1), end = d(2026, 10, 31)), today = d(2026, 11, 22)))
        assertTrue(Recurrence.dueDates(rule(d(2026, 12, 1)), today = d(2026, 11, 22)).isEmpty())
        assertTrue(Recurrence.dueDates(rule(d(2026, 10, 1), active = false), today = d(2026, 11, 22)).isEmpty())
    }

    @Test fun `describes rules in plain words`() {
        assertEquals("Monthly on the 1st", Recurrence.describe(Frequency.MONTHLY, 1, d(2026, 12, 1)))
        assertEquals("Every 2 weeks on Sunday", Recurrence.describe(Frequency.WEEKLY, 2, d(2026, 11, 22)))
        assertEquals("Yearly on 29 Feb", Recurrence.describe(Frequency.YEARLY, 1, d(2028, 2, 29)))
        assertEquals("Monthly on the 22nd", Recurrence.describe(Frequency.MONTHLY, 1, d(2026, 11, 22)))
    }
}
