package com.moneyrecords.app.domain.time

import com.moneyrecords.app.domain.model.TimeRange
import java.time.YearMonth
import java.time.ZoneId

/** Converts calendar months to query ranges and compact storage keys. */
object MonthRange {

    /** [start of month, start of next month) in [zone], as epoch millis. */
    fun of(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): TimeRange {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return TimeRange(start, end)
    }

    /** 2026-11 -> 202611. */
    fun key(month: YearMonth): Int = month.year * 100 + month.monthValue

    fun fromKey(key: Int): YearMonth = YearMonth.of(key / 100, key % 100)
}
