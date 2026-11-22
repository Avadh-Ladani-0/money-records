package com.moneyrecords.app.domain.recurring

import com.moneyrecords.app.domain.model.Frequency
import com.moneyrecords.app.domain.model.RecurringRule
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/**
 * Date maths for recurring records.
 * - Monthly rules keep the start date's day; in shorter months they fall on the last day
 *   (31 Jan → 28/29 Feb → 31 Mar).
 * - Yearly rules keep the start month and day; 29 Feb falls on 28 Feb in non-leap years.
 */
object Recurrence {

    /** The occurrence after [date] for a rule that started on [start]. */
    fun next(date: LocalDate, frequency: Frequency, interval: Int, start: LocalDate): LocalDate {
        require(interval >= 1) { "Interval must be at least 1" }
        return when (frequency) {
            Frequency.DAILY -> date.plusDays(interval.toLong())
            Frequency.WEEKLY -> date.plusWeeks(interval.toLong())
            Frequency.MONTHLY -> {
                val month = YearMonth.from(date).plusMonths(interval.toLong())
                month.atDay(min(start.dayOfMonth, month.lengthOfMonth()))
            }
            Frequency.YEARLY -> {
                val month = YearMonth.of(date.year + interval, start.monthValue)
                month.atDay(min(start.dayOfMonth, month.lengthOfMonth()))
            }
        }
    }

    fun next(rule: RecurringRule, date: LocalDate): LocalDate = next(date, rule.frequency, rule.interval, rule.startDate)

    /**
     * Occurrences of [rule] due on or before [today], oldest first, starting at its next run date
     * (missed days are caught up). Empty for paused rules or past the end date. Capped at [max].
     */
    fun dueDates(rule: RecurringRule, today: LocalDate, max: Int = 60): List<LocalDate> {
        if (!rule.active) return emptyList()
        val out = mutableListOf<LocalDate>()
        var date = rule.nextRunDate
        while (!date.isAfter(today) && (rule.endDate == null || !date.isAfter(rule.endDate)) && out.size < max) {
            out += date
            date = next(rule, date)
        }
        return out
    }

    /**
     * A "once on a date" item is stored as a rule that starts and ends on the same date, so it
     * comes back one time and then ends. No separate type is needed.
     */
    fun isOnce(rule: RecurringRule): Boolean = rule.endDate != null && rule.endDate == rule.startDate

    /** Description for a stored rule ("Monthly on the 1st", "Once on 15 Mar"). */
    fun describe(rule: RecurringRule): String =
        if (isOnce(rule)) "Once on ${shortDate(rule.startDate)}" else describe(rule.frequency, rule.interval, rule.startDate)

    fun shortDate(date: LocalDate): String =
        "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}" +
            if (date.year != LocalDate.now().year) " ${date.year}" else ""

    /** True when [date] is still within the rule's end date (or it has none). */
    fun withinEnd(rule: RecurringRule, date: LocalDate): Boolean = rule.endDate == null || !date.isAfter(rule.endDate)

    /** Human description, e.g. "Monthly on the 1st", "Every 2 weeks", "Yearly on 29 Feb". */
    fun describe(frequency: Frequency, interval: Int, start: LocalDate): String {
        val unit = when (frequency) {
            Frequency.DAILY -> "day"
            Frequency.WEEKLY -> "week"
            Frequency.MONTHLY -> "month"
            Frequency.YEARLY -> "year"
        }
        val base = if (interval == 1) {
            when (frequency) {
                Frequency.DAILY -> "Daily"
                Frequency.WEEKLY -> "Weekly"
                Frequency.MONTHLY -> "Monthly"
                Frequency.YEARLY -> "Yearly"
            }
        } else "Every $interval ${unit}s"
        val on = when (frequency) {
            Frequency.DAILY -> ""
            Frequency.WEEKLY -> " on ${start.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}"
            Frequency.MONTHLY -> " on the ${ordinal(start.dayOfMonth)}"
            Frequency.YEARLY -> " on ${start.dayOfMonth} ${start.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
        }
        return base + on
    }

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }
}
