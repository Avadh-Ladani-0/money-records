package com.moneyrecords.app.domain.detect

/** Where an alert came from. */
enum class DetectSource { SMS, NOTIFICATION, PAY_WIDGET }

enum class DetectStatus { NEW, LATER, ADDED, IGNORED }

/** A caught payment or credit waiting for the user (see DetectedEntity). */
data class Detected(
    val id: Long = 0,
    val source: DetectSource,
    /** SMS sender or app package. */
    val origin: String,
    val direction: Direction,
    /** Paise; 0 = not known yet (a pay-widget payment before its alert arrives). */
    val amount: Long,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val accountDigits: String? = null,
    val counterparty: String? = null,
    val ref: String? = null,
    val upiLite: Boolean = false,
    val occurredAt: Long,
    val text: String = "",
    val status: DetectStatus = DetectStatus.NEW,
    val recordId: Long? = null,
) {
    val isTransfer: Boolean get() = toAccountId != null
}

object DetectionRules {
    /** Two alerts closer than this, same amount and direction, are one payment. */
    const val SAME_WINDOW_MS = 10 * 60_000L
    /** A pay-widget tap waits this long for the payment's alert. */
    const val WIDGET_WINDOW_MS = 20 * 60_000L

    /** True when [incoming] is the same money movement as [existing] (seen from another source). */
    fun sameMovement(existing: Detected, incoming: Detected): Boolean {
        if (existing.ref != null && existing.ref == incoming.ref) return true
        if (existing.direction != incoming.direction) return false
        val gap = incoming.occurredAt - existing.occurredAt
        // A pay-widget tap: the alert comes after it, with the amount.
        if (existing.source == DetectSource.PAY_WIDGET && existing.amount == 0L) {
            return incoming.direction == Direction.OUT && gap in 0..WIDGET_WINDOW_MS
        }
        if (existing.amount != incoming.amount) return false
        if (kotlin.math.abs(gap) > SAME_WINDOW_MS) return false
        // Two SMS from the same bank about the same amount minutes apart can be two real payments;
        // only merge them when nothing tells them apart (no refs).
        if (existing.source == incoming.source && existing.origin == incoming.origin) return existing.ref == null && incoming.ref == null && gap == 0L
        // Different accounts can't be the same payment (that's a transfer pair, see below).
        return existing.accountId == null || incoming.accountId == null || existing.accountId == incoming.accountId
    }

    /** Money out of one of your accounts and the same amount into another, minutes apart: a transfer. */
    fun transferPair(a: Detected, b: Detected): Boolean =
        a.direction != b.direction && a.amount == b.amount && a.amount > 0 &&
            a.accountId != null && b.accountId != null && a.accountId != b.accountId &&
            kotlin.math.abs(a.occurredAt - b.occurredAt) <= SAME_WINDOW_MS

    /** Fills what [existing] doesn't know from [incoming] (amount, account, payee, ref). */
    fun merge(existing: Detected, incoming: Detected): Detected = existing.copy(
        amount = if (existing.amount == 0L) incoming.amount else existing.amount,
        accountId = existing.accountId ?: incoming.accountId,
        accountDigits = existing.accountDigits ?: incoming.accountDigits,
        counterparty = existing.counterparty ?: incoming.counterparty,
        ref = existing.ref ?: incoming.ref,
        upiLite = existing.upiLite || incoming.upiLite,
        text = if (existing.text.isBlank()) incoming.text else existing.text,
    )

    /** Which account an alert is about: matching digits first, then what's known for its sender, else unknown. */
    fun resolveAccount(digits: String?, origin: String, accountDigits: Map<Long, String>, known: Map<String, Long>): Long? {
        if (digits != null) accountDigits.entries.firstOrNull { it.value.endsWith(digits) || digits.endsWith(it.value) }?.let { return it.key }
        return known[origin]
    }

    /** How long an app's payment alert waits for the bank SMS that names the account. */
    const val SMS_WAIT_MS = 90_000L

    /**
     * Which account an app payment came from when no bank SMS arrived (bank payments always send one):
     * - money out, ≤ ₹1,000, and the app has UPI Lite → UPI Lite (Lite payments never send an SMS);
     * - otherwise the app's usual account, else any account linked in it (never UPI Lite for money in).
     * [amount] 0 = not known yet (a pay-widget tap) — then Lite is assumed when the app has it.
     */
    fun accountForApp(
        pkg: String,
        direction: Direction,
        amount: Long,
        liteAccountId: Long?,
        liteApp: String?,
        linked: Set<Long>,
        usual: Long?,
    ): Long? {
        val appHasLite = liteAccountId != null && liteApp == pkg
        if (direction == Direction.OUT && appHasLite && amount <= com.moneyrecords.app.domain.model.UpiLite.MAX_PAYMENT) return liteAccountId
        val banks = linked - setOfNotNull(liteAccountId)
        return usual?.takeIf { it != liteAccountId } ?: banks.minOrNull()
    }
}

/** Caught payments waiting to be recorded. */
interface DetectionRepository {
    suspend fun get(id: Long): Detected?
    fun observeOpen(): kotlinx.coroutines.flow.Flow<List<Detected>>
    suspend fun setStatus(id: Long, status: DetectStatus, recordId: Long? = null)
    /** The category last used for this payee, if any. */
    suspend fun categoryFor(counterparty: String?, direction: Direction): Long?
    /** Accounts to offer first for an item from [origin]: the app's UPI Lite, its usual, its linked accounts. */
    suspend fun accountsFor(origin: String): List<Long>
    /** The account by the app's rules when no bank SMS named it. */
    suspend fun guessAccount(d: Detected): Long?
    /** Re-applies rules that may have changed since it was caught (e.g. UPI Lite set up later). */
    suspend fun refresh(id: Long): Detected?
}

/** The prompts (notifications) for caught payments. */
interface DetectedPrompts {
    fun cancel(id: Long)
    suspend fun refreshSummary(alert: Boolean = false)
}

object DetectionText {
    /** "Paid ₹150 to Raju · SBI", "₹500 received in SBI from Rahul", "₹2,000 moved SBI → UPI Lite". */
    fun title(d: Detected, accountNames: Map<Long, String>): String {
        val account = d.accountId?.let(accountNames::get)
        val amount = if (d.amount > 0) com.moneyrecords.app.domain.money.Money.format(d.amount) else "A payment"
        return when {
            d.isTransfer -> "$amount moved" + (account?.let { " $it" } ?: "") + " → " + (accountNames[d.toAccountId] ?: "another account")
            d.direction == Direction.OUT -> "Paid $amount" + (d.counterparty?.let { " to $it" } ?: "") + (account?.let { " · $it" } ?: "")
            else -> "$amount received" + (account?.let { " in $it" } ?: "") + (d.counterparty?.let { " from $it" } ?: "")
        }
    }
}
