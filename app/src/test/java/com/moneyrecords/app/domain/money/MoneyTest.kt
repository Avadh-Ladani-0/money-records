package com.moneyrecords.app.domain.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test fun `groups digits the Indian way`() {
        assertEquals("0", Money.groupIndian("0"))
        assertEquals("999", Money.groupIndian("999"))
        assertEquals("1,000", Money.groupIndian("1000"))
        assertEquals("12,345", Money.groupIndian("12345"))
        assertEquals("1,00,000", Money.groupIndian("100000"))
        assertEquals("12,34,567", Money.groupIndian("1234567"))
        assertEquals("1,23,45,67,890", Money.groupIndian("1234567890"))
    }

    @Test fun `hides zero paise by default`() {
        assertEquals("₹1,24,500", Money.format(1_24_500_00L))
        assertEquals("₹0", Money.format(0L))
    }

    @Test fun `shows paise when present or forced`() {
        assertEquals("₹2,349.50", Money.format(2_349_50L))
        assertEquals("₹0.05", Money.format(5L))
        assertEquals("₹10.00", Money.format(10_00L, alwaysShowPaise = true))
    }

    @Test fun `applies sign modes`() {
        assertEquals("−₹450.75", Money.format(-450_75L))
        assertEquals("₹450.75", Money.format(-450_75L, sign = Money.Sign.NONE))
        assertEquals("+₹85,000", Money.format(85_000_00L, sign = Money.Sign.ALWAYS))
        assertEquals("₹0", Money.format(0L, sign = Money.Sign.ALWAYS))
    }

    @Test fun `splits into parts for the hero display`() {
        val p = Money.parts(-12_45_678_50L, alwaysShowPaise = true)
        assertEquals(Money.Parts("−", "₹", "12,45,678", "50"), p)
    }
}
