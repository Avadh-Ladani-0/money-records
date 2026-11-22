package com.moneyrecords.app.domain.money

import kotlin.math.absoluteValue

/**
 * Money is always held as a [Long] number of paise (1/100 of a rupee) — never as floating point.
 * Formatting uses Indian digit grouping: 1,00,000 rather than 100,000.
 */
object Money {
    const val DEFAULT_SYMBOL = "₹"

    /** U+2212 MINUS SIGN — typographically matches "+" width, unlike a hyphen. */
    const val MINUS = "−"

    enum class Sign { NONE, NEGATIVE_ONLY, ALWAYS }

    data class Parts(
        val sign: String,
        val symbol: String,
        val rupees: String,
        /** Two-digit paise, or empty when hidden. */
        val paise: String,
    )

    /** Groups a string of digits Indian-style: "1234567" -> "12,34,567". */
    fun groupIndian(digits: String): String {
        require(digits.all { it.isDigit() }) { "Expected only digits: $digits" }
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,$lastThree"
    }

    fun parts(
        paise: Long,
        symbol: String = DEFAULT_SYMBOL,
        sign: Sign = Sign.NEGATIVE_ONLY,
        alwaysShowPaise: Boolean = false,
    ): Parts {
        require(paise != Long.MIN_VALUE) { "Amount out of range" }
        val abs = paise.absoluteValue
        val rupees = groupIndian((abs / 100).toString())
        val fraction = abs % 100
        val paiseText = if (alwaysShowPaise || fraction != 0L) fraction.toString().padStart(2, '0') else ""
        val signText = when {
            sign == Sign.NONE -> ""
            paise < 0 -> MINUS
            paise > 0 && sign == Sign.ALWAYS -> "+"
            else -> ""
        }
        return Parts(signText, symbol, rupees, paiseText)
    }

    /**
     * Parses user input such as "1,234.5", "₹ 1,00,000" or ".75" into paise.
     * Returns null for empty, malformed, negative or more-than-2-decimal input, or overflow.
     */
    fun parse(input: String): Long? {
        val cleaned = input.trim().removePrefix(DEFAULT_SYMBOL).replace(",", "").replace(" ", "")
        if (cleaned.isEmpty() || cleaned == ".") return null
        if (!cleaned.matches(Regex("""\d*(\.\d{0,2})?"""))) return null
        val rupeesText = cleaned.substringBefore('.').ifEmpty { "0" }
        val paiseText = cleaned.substringAfter('.', "").padEnd(2, '0')
        if (rupeesText.length > 15) return null
        return rupeesText.toLong() * 100 + paiseText.toLong()
    }

    fun format(
        paise: Long,
        symbol: String = DEFAULT_SYMBOL,
        sign: Sign = Sign.NEGATIVE_ONLY,
        alwaysShowPaise: Boolean = false,
    ): String {
        val p = parts(paise, symbol, sign, alwaysShowPaise)
        val fraction = if (p.paise.isEmpty()) "" else ".${p.paise}"
        return "${p.sign}${p.symbol}${p.rupees}$fraction"
    }

    /**
     * How a screen reader should say an amount: "1,502 rupees", "minus 179 rupees",
     * "12 rupees 50 paise". Symbols like "−₹" are read badly by TalkBack, so lists use this.
     */
    fun spoken(paise: Long): String {
        val abs = kotlin.math.abs(paise)
        val rupees = groupIndian((abs / 100).toString())
        val p = abs % 100
        val main = if (abs / 100 == 1L) "1 rupee" else "$rupees rupees"
        val text = if (p == 0L) main else "$main $p paise"
        return if (paise < 0) "minus $text" else text
    }
}
