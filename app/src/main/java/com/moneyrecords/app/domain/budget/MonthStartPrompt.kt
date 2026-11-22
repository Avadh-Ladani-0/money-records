package com.moneyrecords.app.domain.budget

import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Decides when to ask "Keep last month's budgets?" at the start of a month.
 *
 * Asks once per month, from 9 AM on the 1st. If the phone was off or the check didn't run on the 1st,
 * it still asks on the first check within the first week, so a month is never silently skipped.
 * Only asks when last month actually had budgets.
 */
object MonthStartPrompt {
    const val PROMPT_HOUR = 9
    const val LAST_DAY_TO_ASK = 7

    fun shouldPrompt(now: LocalDateTime, lastPrompted: YearMonth?, hadBudgetsLastMonth: Boolean): Boolean {
        if (!hadBudgetsLastMonth) return false
        if (lastPrompted == YearMonth.from(now)) return false
        val day = now.dayOfMonth
        return when {
            day == 1 -> now.hour >= PROMPT_HOUR
            else -> day <= LAST_DAY_TO_ASK
        }
    }
}
