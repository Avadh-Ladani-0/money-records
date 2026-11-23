package com.moneyrecords.app.domain.detect

/** Money leaving (a payment) or arriving (a credit). */
enum class Direction { OUT, IN }

/**
 * What a bank SMS or payment-app notification says happened. [accountDigits] are the last digits of
 * the bank account/card ("1234"); [counterparty] is who was paid / who paid (as written).
 */
data class ParsedTransaction(
    val direction: Direction,
    val amount: Long,
    val accountDigits: String? = null,
    val ref: String? = null,
    val counterparty: String? = null,
    /** Mentions UPI Lite (a top-up from the bank, or a payment from UPI Lite). */
    val upiLite: Boolean = false,
    /** A reversal / refund of an earlier payment. */
    val reversal: Boolean = false,
)

/**
 * Reads transaction alerts. Deliberately conservative: anything that isn't clearly a completed
 * debit or credit (OTPs, offers, due reminders, requests, failures) returns null, so the user is
 * only ever asked about real money movements.
 */
object TransactionParser {
    private val ignore = listOf(
        "otp", "one time password", "one-time password", "verification code", " is due", "due on", "due date",
        "will be debited", "will be deducted", "requested money", "request of", "has requested", "collect request",
        "offer", "cashback upto", "cashback up to", "pre-approved", "preapproved", "eligible for", "apply now",
        "failed", "declined", "unsuccessful", "could not be", "was not successful", "insufficient",
        "minimum amount due", "total amount due", "statement", "bill generated", "autopay", "is scheduled",
        "mandate", "e-mandate", "will be auto", "avail bal in a/c", "free transactions",
    )
    /** Clear signals: when one of these is present, it decides the direction (earliest wins). */
    private val strongOut = listOf("debited", "withdrawn", "top-up", "top up", "topup")
    private val strongIn = listOf("credited", "has credit for", "credit for upi", "deposited", "reversed", "reversal")
    /** Weaker wording, used only when no clear signal is there. */
    private val outWords = listOf(
        "debit of", "spent", "paid to", "you paid", "payment of", "sent to", "you sent",
        "withdrawal", "transferred to", "trf to", "purchase", "deducted", "payment successful", "paid via",
    )
    private val inWords = listOf(
        "credit of", "received", "paid you", "sent you", "added to your", "refund",
    )

    private val future = listOf("will be refunded", "refunded if", "will be reversed", "reversed if", "will be credited")
    private val reversalWords = listOf("reversed", "reversal", "refunded to", "refund of", "refund credited", "refund for")
    /** "Paid ₹150 to Raju" / "Sent ₹50 to Amit" — but not "Rahul paid you". */
    private val paidOrSent = Regex("""\b(?:paid|sent)\b(?! you)""")

    private val currencyAmount = Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    /** "debited by 150.0" — some banks (e.g. SBI UPI) write the number without a currency. */
    private val verbAmount = Regex("""(?:debited|credited)\s+(?:by|with|for)\s+(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val digits = listOf(
        Regex("""(?:a/c|ac|acct|account|card)(?:\s*no\.?)?\s*[:\-]?\s*(?:no\.?\s*)?(?:x+|\*+|\.{2,})\s*(\d{3,6})"""),
        Regex("""(?:a/c|ac|acct|account|card)\s*(?:ending|ending with|ending in)\s*(\d{3,6})"""),
        Regex("""\bx{2,}(\d{3,6})\b"""),
    )
    private val ref = Regex("""(?:upi\s*ref(?:\s*no)?|ref(?:erence)?\s*(?:no\.?|number|id)?|refno|utr(?:\s*no)?|rrn|txn\s*id)\s*[:.\-#]?\s*(\d{6,})""")
    private val payee = Regex("""(?:trf to|transferred to|paid to|sent to|to vpa|to)\s+([a-z0-9][a-z0-9 .@&_'-]{1,40}?)(?=\s+(?:refno|ref|on|upi|via|from|using|a/c|is|has|thru|through)\b|[.,(]|$)""")
    private val payer = Regex("""(?:from|by)\s+([a-z0-9][a-z0-9 .@&_'-]{1,40}?)(?=\s+(?:ref|on|upi|via|to|in|into|a/c|thru|through|is|has)\b|[.,(]|$)""")
    private val paidYou = Regex("""([a-z][a-z .'-]{1,40}?)\s+(?:paid|sent)\s+you""")

    fun parse(text: String): ParsedTransaction? {
        val t = text.lowercase().replace('\n', ' ').replace(Regex("""\s+"""), " ")
        // A refund that only "will" happen isn't money yet.
        if (future.any { it in t }) return null
        val reversal = reversalWords.any { it in t } || "upi/drc" in t
        if (ignore.any { it in t } && !reversal) return null
        fun first(words: List<String>) = words.mapNotNull { w -> t.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val strongOutAt = first(strongOut)
        val strongInAt = first(strongIn)
        val hasStrong = strongOutAt != null || strongInAt != null
        val outAt = if (hasStrong) strongOutAt else (outWords.mapNotNull { w -> t.indexOf(w).takeIf { it >= 0 } } +
            listOfNotNull(paidOrSent.find(t)?.range?.first)).minOrNull()
        val inAt = if (hasStrong) strongInAt else first(inWords)
        val direction = when {
            reversal -> Direction.IN
            outAt != null && (inAt == null || outAt < inAt) -> Direction.OUT
            inAt != null -> Direction.IN
            else -> return null
        }
        val amountText = verbAmount.find(t)?.groupValues?.get(1) ?: currencyAmount.find(t)?.groupValues?.get(1) ?: return null
        val amount = toPaise(amountText) ?: return null
        if (amount <= 0) return null
        val accountDigits = digits.firstNotNullOfOrNull { it.find(t)?.groupValues?.get(1) }?.takeLast(4)
        val who = when {
            "atm" in t && "withdrawn" in t -> "ATM withdrawal"
            parsedTopUp(t) -> null
            direction == Direction.OUT -> firstName(payee, t) ?: firstName(info, t)
            else -> paidYou.find(t)?.groupValues?.get(1)?.let(::cleanName) ?: firstName(payer, t)
        }
        return ParsedTransaction(
            direction = direction,
            amount = amount,
            accountDigits = accountDigits,
            ref = ref.find(t)?.groupValues?.get(1),
            counterparty = who,
            upiLite = "upi lite" in t || "upilite" in t,
            reversal = reversal,
        )
    }

    /** "INFO: SWIGGY" — Kalupur Bank puts the other party here. */
    private val info = Regex("""info\s*:\s*([a-z][a-z0-9 .&'-]{1,40}?)(?=\s*(?:-|$|\.|,))""")

    private fun parsedTopUp(t: String) = ("upi lite" in t || "upilite" in t) && ("top-up" in t || "top up" in t || "topup" in t)

    /** The first match that looks like a name (not "Rs.150", not a date or number). */
    private fun firstName(regex: Regex, t: String): String? =
        regex.findAll(t).map { it.groupValues[1] }
            .filterNot { c -> c.startsWith("rs") || c.startsWith("inr") || c.startsWith("₹") || c.count(Char::isDigit) > c.count(Char::isLetter) }
            .firstNotNullOfOrNull(::cleanName)

    private fun toPaise(text: String): Long? {
        val clean = text.replace(",", "").trimEnd('.')
        val parts = clean.split('.')
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0
        return rupees * 100 + paise
    }

    /** "rahul kumar" → "Rahul Kumar"; VPAs and account-ish words are kept as they are. */
    private fun cleanName(raw: String): String? {
        val s = raw.trim().trimEnd('.', '-', ',').trim()
        if (s.length < 2 || s.all { it.isDigit() } || s in setOf("you", "your", "a/c", "your a/c", "account")) return null
        if ('@' in s) return s
        return s.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
