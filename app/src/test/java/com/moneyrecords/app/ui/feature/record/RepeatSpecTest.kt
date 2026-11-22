package com.moneyrecords.app.ui.feature.record

import com.moneyrecords.app.domain.model.Frequency
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.RecurringRule
import com.moneyrecords.app.domain.recurring.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RepeatSpecTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private val template = RecurringRule(
        kind = RecordKind.EXPENSE, amount = 4_000_00, accountId = 1, categoryId = 1,
        frequency = Frequency.MONTHLY, startDate = d(2026, 11, 22),
    )

    @Test fun `natural next dates`() {
        assertEquals(d(2026, 12, 22), RepeatSpec(Frequency.MONTHLY).nextFrom(d(2026, 11, 22)))
        assertEquals(d(2026, 11, 29), RepeatSpec(Frequency.WEEKLY).nextFrom(d(2026, 11, 22)))
        assertEquals(d(2026, 12, 22), RepeatSpec(Frequency.MONTHLY, once = true).nextFrom(d(2026, 11, 22)))
    }

    @Test fun `once on a date comes back one time then ends`() {
        val rule = RepeatSpec(Frequency.MONTHLY, nextDate = d(2027, 3, 15), once = true).toRule(template, d(2026, 11, 22))
        assertEquals(d(2027, 3, 15), rule.nextRunDate)
        assertEquals(d(2027, 3, 15), rule.startDate)
        assertEquals(d(2027, 3, 15), rule.endDate)
        assertTrue(Recurrence.isOnce(rule))
        assertEquals(listOf(d(2027, 3, 15)), Recurrence.dueDates(rule, today = d(2027, 6, 1)))   // only once
        assertFalse(Recurrence.withinEnd(rule, Recurrence.next(rule, rule.nextRunDate)))
    }

    @Test fun `chosen next date becomes the anchor for later dates`() {
        val rule = RepeatSpec(Frequency.MONTHLY, nextDate = d(2026, 12, 5)).toRule(template, d(2026, 11, 22))
        assertEquals(d(2026, 12, 5), rule.nextRunDate)
        assertEquals(d(2027, 1, 5), Recurrence.next(rule, rule.nextRunDate))      // the 5th each month
        assertFalse(Recurrence.isOnce(rule))
        assertEquals("Monthly on the 5th · next 5 Dec", RepeatSpec(Frequency.MONTHLY, nextDate = d(2026, 12, 5)).describe(d(2026, 11, 22)))
    }

    @Test fun `without a chosen date the record date stays the anchor (month-end safe)`() {
        val rule = RepeatSpec(Frequency.MONTHLY).toRule(template, d(2027, 1, 31))
        assertEquals(d(2027, 2, 28), rule.nextRunDate)
        assertEquals(d(2027, 1, 31), rule.startDate)
        assertEquals(d(2027, 3, 31), Recurrence.next(rule, rule.nextRunDate))
    }
}
