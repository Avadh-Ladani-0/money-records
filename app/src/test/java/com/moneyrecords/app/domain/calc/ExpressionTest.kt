package com.moneyrecords.app.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionTest {
    private fun eval(s: String) = Expression.evaluate(s)?.let(Expression::toPlainText)

    @Test fun `basic arithmetic with precedence`() {
        assertEquals("14", eval("2+3×4"))
        assertEquals("10", eval("100÷10"))
        assertEquals("7.5", eval("10−2.5"))
        assertEquals("6", eval("2*3"))
        assertEquals("3", eval("9/3"))
        assertEquals("-5", eval("-5"))
        assertEquals("1234", eval("1,234"))
    }

    @Test fun `percent behaves like a calculator`() {
        assertEquals("110", eval("100+10%"))
        assertEquals("90", eval("100−10%"))
        assertEquals("20", eval("200×10%"))
        assertEquals("0.5", eval("50%"))
    }

    @Test fun `trailing operator is ignored for preview`() {
        assertEquals("12", eval("12+"))
        assertEquals("12", eval("12×"))
    }

    @Test fun `bad input and division by zero give null`() {
        assertNull(eval(""))
        assertNull(eval("+"))
        assertNull(eval("5÷0"))
        assertNull(eval("1..2"))
        assertNull(eval("2++×3"))
        assertNull(eval("abc"))
    }

    @Test fun `rounds to paise and rejects negatives`() {
        assertEquals(33_33L, Expression.evaluateToPaise("100÷3"))
        assertEquals(1_250_50L, Expression.evaluateToPaise("1000+250.5"))
        assertNull(Expression.evaluateToPaise("5−10"))
        assertNull(Expression.evaluateToPaise("1÷0"))
    }

    @Test fun `detects operators`() {
        assertTrue(Expression.hasOperator("12+3"))
        assertFalse(Expression.hasOperator("123.5"))
    }
}
