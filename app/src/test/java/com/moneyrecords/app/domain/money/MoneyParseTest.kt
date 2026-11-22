package com.moneyrecords.app.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParseTest {

    @Test fun `parses whole rupees and grouped input`() {
        assertEquals(100_00L, Money.parse("100"))
        assertEquals(1_234_00L, Money.parse("1,234"))
        assertEquals(1_00_000_00L, Money.parse("₹ 1,00,000"))
    }

    @Test fun `parses paise with one or two decimals`() {
        assertEquals(1_234_50L, Money.parse("1234.5"))
        assertEquals(1_234_05L, Money.parse("1234.05"))
        assertEquals(75L, Money.parse(".75"))
        assertEquals(12_00L, Money.parse("12."))
    }

    @Test fun `rejects bad input`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("."))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("12a"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("1234567890123456"))
    }

    @Test fun `format and parse round-trip`() {
        listOf(0L, 5L, 1_00L, 2_349_50L, 12_45_678_50L).forEach { paise ->
            assertEquals(paise, Money.parse(Money.format(paise)))
        }
    }
}
