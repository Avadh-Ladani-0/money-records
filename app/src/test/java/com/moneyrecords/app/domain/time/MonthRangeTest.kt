package com.moneyrecords.app.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class MonthRangeTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    @Test fun `month range starts at local midnight and is half-open`() {
        val range = MonthRange.of(YearMonth.of(2026, 11), ist)
        assertEquals(Instant.parse("2026-10-31T18:30:00Z").toEpochMilli(), range.start)
        assertEquals(Instant.parse("2026-11-30T18:30:00Z").toEpochMilli(), range.endExclusive)
    }

    @Test fun `december rolls into next year`() {
        val range = MonthRange.of(YearMonth.of(2026, 12), ist)
        assertEquals(Instant.parse("2026-12-31T18:30:00Z").toEpochMilli(), range.endExclusive)
    }

    @Test fun `keys round-trip`() {
        assertEquals(202611, MonthRange.key(YearMonth.of(2026, 11)))
        assertEquals(YearMonth.of(2027, 1), MonthRange.fromKey(202701))
    }
}
