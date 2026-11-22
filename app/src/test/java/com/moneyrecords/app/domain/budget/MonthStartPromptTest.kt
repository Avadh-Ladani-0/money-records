package com.moneyrecords.app.domain.budget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

class MonthStartPromptTest {
    private val dec = YearMonth.of(2026, 12)
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 12, day, hour, 0)

    @Test fun `asks from 9 AM on the 1st`() {
        assertFalse(MonthStartPrompt.shouldPrompt(at(1, 8), lastPrompted = null, hadBudgetsLastMonth = true))
        assertTrue(MonthStartPrompt.shouldPrompt(at(1, 9), lastPrompted = null, hadBudgetsLastMonth = true))
    }

    @Test fun `catches up later in the first week if the 1st was missed`() {
        assertTrue(MonthStartPrompt.shouldPrompt(at(3, 7), lastPrompted = YearMonth.of(2026, 11), hadBudgetsLastMonth = true))
        assertFalse(MonthStartPrompt.shouldPrompt(at(8, 12), lastPrompted = YearMonth.of(2026, 11), hadBudgetsLastMonth = true))
    }

    @Test fun `only once per month and only when last month had budgets`() {
        assertFalse(MonthStartPrompt.shouldPrompt(at(1, 10), lastPrompted = dec, hadBudgetsLastMonth = true))
        assertFalse(MonthStartPrompt.shouldPrompt(at(1, 10), lastPrompted = null, hadBudgetsLastMonth = false))
    }
}
