package com.moneyrecords.app.domain.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Evaluates simple calculator input: numbers, + − × ÷ and postfix %.
 * - Usual precedence (× ÷ before + −), left to right.
 * - `a + b%` / `a − b%` add or subtract b percent *of a* (calculator style); elsewhere `b%` is b/100.
 * - A trailing operator is ignored, so "12+" previews as 12.
 * Returns null for malformed input or division by zero. Never throws.
 */
object Expression {
    private val MC = MathContext(34, RoundingMode.HALF_UP)

    fun evaluate(input: String): BigDecimal? = try {
        val tokens = tokenize(input) ?: return null
        val trimmed = tokens.dropLastWhile { it is Token.Op }
        if (trimmed.isEmpty()) null else Parser(trimmed).parse()
    } catch (_: ArithmeticException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    /** Evaluates and rounds to paise. Null when invalid, negative or too large. */
    fun evaluateToPaise(input: String): Long? {
        val value = evaluate(input) ?: return null
        if (value.signum() < 0) return null
        val paise = value.setScale(2, RoundingMode.HALF_UP).movePointRight(2)
        return if (paise > BigDecimal(MAX_PAISE)) null else paise.toLong()
    }

    /** True when [input] contains an operator, i.e. there is a result worth previewing. */
    fun hasOperator(input: String): Boolean = input.any { it in "+−-×*÷/%" }

    /** Plain number text for copying: no grouping, at most 2 decimals, no trailing zeros. */
    fun toPlainText(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

    private const val MAX_PAISE = 999_999_999_999_99L

    private sealed interface Token {
        data class Num(val value: BigDecimal) : Token
        data class Op(val symbol: Char) : Token
        data object Percent : Token
    }

    private fun tokenize(input: String): List<Token>? {
        val out = mutableListOf<Token>()
        var i = 0
        val s = input.replace(",", "").replace(" ", "")
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    val text = s.substring(start, i)
                    if (text.count { it == '.' } > 1 || text == ".") return null
                    out += Token.Num(BigDecimal(text))
                    continue
                }
                c == '+' -> out += Token.Op('+')
                c == '-' || c == '−' -> out += Token.Op('-')
                c == '*' || c == '×' -> out += Token.Op('*')
                c == '/' || c == '÷' -> out += Token.Op('/')
                c == '%' -> out += Token.Percent
                else -> return null
            }
            i++
        }
        return out
    }

    /** expr := term (('+'|'-') term)* ; term := factor (('*'|'/') factor)* ; factor := '-'? number '%'? */
    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun parse(): BigDecimal {
            val value = expr()
            check(pos == tokens.size) { "Unexpected token" }
            return value
        }

        private fun expr(): BigDecimal {
            var left = term().value
            while (peekOp('+') || peekOp('-')) {
                val op = (tokens[pos++] as Token.Op).symbol
                val right = term()
                val amount = if (right.isPercentOnly) left.multiply(right.value, MC) else right.value
                left = if (op == '+') left.add(amount, MC) else left.subtract(amount, MC)
            }
            return left
        }

        private class Term(val value: BigDecimal, val isPercentOnly: Boolean)

        private fun term(): Term {
            val first = factor()
            var value = first.value
            var single = true
            while (peekOp('*') || peekOp('/')) {
                single = false
                val op = (tokens[pos++] as Token.Op).symbol
                val right = factor().value
                value = if (op == '*') value.multiply(right, MC) else value.divide(right, MC)
            }
            return Term(value, single && first.isPercent)
        }

        private class Factor(val value: BigDecimal, val isPercent: Boolean)

        private fun factor(): Factor {
            var negative = false
            while (peekOp('-')) { pos++; negative = !negative }
            val num = tokens.getOrNull(pos) as? Token.Num ?: error("Expected a number")
            pos++
            var value = num.value
            var percent = false
            if (tokens.getOrNull(pos) == Token.Percent) {
                pos++
                percent = true
                value = value.divide(BigDecimal(100), MC)
            }
            return Factor(if (negative) value.negate() else value, percent)
        }

        private fun peekOp(symbol: Char) = (tokens.getOrNull(pos) as? Token.Op)?.symbol == symbol
    }
}
