package com.moneyrecords.app.domain.split

/** One friend's (or "others'") part of a bill you paid. [person] is optional. */
data class SplitPart(val person: String?, val amount: Long)

/**
 * A bill of [total] paise you paid for a group: [myShare] is your spending, [parts] are what others owe.
 * Your share may be 0 when you paid entirely for someone else.
 */
data class SplitSpec(val total: Long, val myShare: Long, val parts: List<SplitPart>) {
    val toCollect: Long get() = parts.sumOf { it.amount }

    /** Why this split can't be saved, or null when it's fine. */
    val problem: String?
        get() = when {
            total <= 0 -> "Enter the bill amount first"
            parts.isEmpty() -> "Add at least one other person"
            myShare < 0 || parts.any { it.amount <= 0 } -> "Every share must be more than ₹0"
            myShare + toCollect != total -> "Shares must add up to the bill"
            else -> null
        }
}

object Split {
    /**
     * [total] shared equally by [people] (you included). Any paise left over go to you, so others'
     * shares are round and the parts always add up exactly. First entry is yours.
     */
    fun equalShares(total: Long, people: Int): List<Long> {
        require(people >= 2) { "A split needs at least 2 people" }
        val each = total / people
        val mine = total - each * (people - 1)
        return listOf(mine) + List(people - 1) { each }
    }

    /** An equal split; [names] are optional names for the others (missing = unnamed). */
    fun equal(total: Long, people: Int, names: List<String?> = emptyList()): SplitSpec {
        val shares = equalShares(total, people)
        return SplitSpec(
            total = total,
            myShare = shares.first(),
            parts = shares.drop(1).mapIndexed { i, amount -> SplitPart(names.getOrNull(i)?.trim()?.ifBlank { null }, amount) },
        )
    }
}

/**
 * A friend's part of a bill that hasn't been fully paid back. [outstanding] is still owed;
 * [waiting] has been paid into another account and is waiting to be moved back.
 */
data class OpenPart(
    val legId: Long,
    val person: String?,
    val payerAccountId: Long,
    val payerName: String,
    val expenseId: Long,
    val title: String,
    val iconKey: String?,
    val occurredAtMillis: Long,
    val billTotal: Long?,
    val outstanding: Long,
    val waiting: Long = 0,
)

/** Repayments sitting in [heldInName] (e.g. SBI) that belong back in [payerName] (e.g. UPI Lite). */
data class AwaitingMove(
    val payerAccountId: Long,
    val payerName: String,
    val heldInAccountId: Long,
    val heldInName: String,
    val total: Long,
    val count: Int = 1,
)

/** Something owed (an [id] and how much); used when one payment covers several. */
data class Owed(val id: Long, val amount: Long)

object SplitMath {
    /**
     * Spreads [amount] over [owed] in order (oldest first), never more than each one owes.
     * Returns how much goes to each; anything beyond the total owed is left out.
     */
    fun allocate(amount: Long, owed: List<Owed>): List<Owed> {
        var left = amount
        val out = mutableListOf<Owed>()
        for (o in owed) {
            if (left <= 0) break
            val take = minOf(left, o.amount)
            if (take > 0) { out += Owed(o.id, take); left -= take }
        }
        return out
    }

    /** Bills of [month] are "this month"; older ones are grouped by month, newest month first. */
    fun byMonth(parts: List<OpenPart>, month: java.time.YearMonth, zone: java.time.ZoneId): Pair<List<OpenPart>, List<Pair<java.time.YearMonth, List<OpenPart>>>> {
        fun monthOf(p: OpenPart) = java.time.YearMonth.from(java.time.Instant.ofEpochMilli(p.occurredAtMillis).atZone(zone))
        val (current, older) = parts.partition { !monthOf(it).isBefore(month) }
        val groups = older.groupBy(::monthOf).toSortedMap(compareByDescending { it }).map { it.key to it.value }
        return current to groups
    }
}
