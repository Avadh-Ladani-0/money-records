package com.moneyrecords.app.domain.split

import com.moneyrecords.app.ui.feature.collect.groupByPerson
import com.moneyrecords.app.ui.feature.record.SplitDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitTest {
    @Test fun `equal shares give any leftover paise to you`() {
        assertEquals(listOf(150_00L, 150_00L), Split.equalShares(300_00, 2))
        assertEquals(listOf(100_01L, 100_00L, 100_00L), Split.equalShares(300_01, 3))
        assertEquals(listOf(34L, 33L, 33L), Split.equalShares(100, 3))
    }

    @Test fun `a split must add up to the bill`() {
        assertNull(Split.equal(300_00, 2).problem)
        assertEquals("Shares must add up to the bill", SplitSpec(300_00, 100_00, listOf(SplitPart(null, 150_00))).problem)
        assertEquals("Add at least one other person", SplitSpec(300_00, 300_00, emptyList()).problem)
        // Paying entirely for someone else is allowed: your share is ₹0.
        assertNull(SplitSpec(300_00, 0, listOf(SplitPart("Rahul", 300_00))).problem)
    }

    @Test fun `draft follows the bill until a share is typed`() {
        val equal = SplitDraft(names = listOf("Rahul", null))
        assertEquals(SplitSpec(300_00, 100_00, listOf(SplitPart("Rahul", 100_00), SplitPart(null, 100_00))), equal.toSpec(300_00))
        assertEquals(150_00L, equal.toSpec(450_00).myShare)
        val custom = SplitDraft(names = listOf("Rahul"), custom = listOf(100_00, 200_00))
        assertEquals(200_00L, custom.toSpec(300_00).toCollect)
    }

    @Test fun `a re-opened equal split stays equal, a custom one stays custom`() {
        assertNull(SplitDraft.from(Split.equal(300_00, 3)).custom)
        assertEquals(listOf(100_00L, 200_00L), SplitDraft.from(SplitSpec(300_00, 100_00, listOf(SplitPart(null, 200_00)))).custom)
    }

    @Test fun `to collect groups by person with unnamed parts last`() {
        fun part(id: Long, person: String?, owed: Long) = OpenPart(id, person, 1, "UPI Lite", id, "Dinner", "food", 0, 300_00, owed)
        val groups = groupByPerson(listOf(part(1, null, 150_00), part(2, "Rahul", 100_00), part(3, "Rahul", 50_00), part(4, "Amit", 300_00)))
        assertEquals(listOf("Amit", "Rahul", "Others"), groups.map { it.name })
        assertEquals(150_00L, groups[1].total)
    }

    @Test fun `one payment covers several bills oldest first and never more than owed`() {
        val owed = listOf(Owed(1, 150_00), Owed(2, 100_00), Owed(3, 50_00))
        assertEquals(listOf(Owed(1, 150_00), Owed(2, 50_00)), SplitMath.allocate(200_00, owed))
        assertEquals(owed, SplitMath.allocate(999_00, owed))
        assertEquals(emptyList<Owed>(), SplitMath.allocate(0, owed))
    }

    @Test fun `this month's bills apart, earlier months grouped newest first`() {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        fun at(date: String) = java.time.LocalDate.parse(date).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        fun part(id: Long, date: String) = OpenPart(id, null, 1, "UPI Lite", id, "Dinner", "food", at(date), 300_00, 150_00)
        val parts = listOf(part(1, "2026-11-30"), part(2, "2026-12-01"), part(3, "2026-10-05"), part(4, "2026-11-02"))
        val (current, earlier) = SplitMath.byMonth(parts, java.time.YearMonth.of(2026, 12), zone)
        assertEquals(listOf(2L), current.map { it.legId })               // 30 Nov is already "earlier" on 1 Dec
        assertEquals(listOf(java.time.YearMonth.of(2026, 11), java.time.YearMonth.of(2026, 10)), earlier.map { it.first })
        assertEquals(listOf(1L, 4L), earlier.first().second.map { it.legId })
    }
}
