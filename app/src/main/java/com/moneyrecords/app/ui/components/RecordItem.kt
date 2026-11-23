package com.moneyrecords.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moneyrecords.app.domain.model.RecordDetails
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.ui.theme.MrTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/**
 * One record in a list. Expense "−₹", income "+₹", transfer "From → To"; colours come from the
 * theme (Nothing keeps them white, Graphite colours them). Read by TalkBack as one sentence.
 * [showTime] adds the time to the subtitle (useful in day-grouped lists).
 */
@Composable
fun RecordItem(
    details: RecordDetails,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTime: Boolean = true,
    zone: ZoneId = ZoneId.systemDefault(),
    /** Shown first in the subtitle, e.g. "Yesterday" in lists that aren't grouped by day. */
    subtitlePrefix: String? = null,
) {
    val record = details.record
    val isTransfer = record.kind == RecordKind.TRANSFER
    val repayment = record.isRepayment
    val title = when {
        repayment -> "Repaid" + (record.person?.let { " · $it" } ?: "")
        isTransfer -> "Transfer"
        else -> details.categoryName ?: "Uncategorised"
    }
    val where = when {
        // Waiting in SBI until it's moved back to the account that paid.
        repayment -> "In ${details.toAccountName ?: "?"} · to move back"
        isTransfer -> "${details.accountName} → ${details.toAccountName ?: "?"}"
        else -> details.accountName
    }
    val splitLine = when {
        record.isSplit -> "Your share of ${Money.format(record.splitTotal!!)}"
        // A friend's part: spending until it's paid back and moved.
        record.isToCollect -> "To collect · ${record.person ?: "others"}" +
            if (details.splitWaiting > 0) " · ${Money.format(details.splitWaiting)} received" else ""
        else -> null
    }
    val parts = buildList {
        if (subtitlePrefix != null) add(subtitlePrefix)
        if (showTime) add(record.occurredAt.atZone(zone).format(timeFormat))
        add(where)
        splitLine?.let(::add)
        if (record.note.isNotBlank()) add(record.note)
        if (record.recurringRuleId != null) add("↻")
    }
    val spoken = buildList {
        add(
            when (record.kind) {
                RecordKind.EXPENSE -> "Expense, $title"
                RecordKind.INCOME -> "Income, $title"
                RecordKind.TRANSFER -> if (repayment) "Repaid${record.person?.let { " by $it" } ?: ""}, waiting in ${details.toAccountName}"
                else "Transfer from ${details.accountName} to ${details.toAccountName ?: "unknown account"}"
            },
        )
        add(Money.spoken(record.amount))
        record.splitTotal?.let { total -> add("your share of a ${Money.spoken(total)} bill") }
        if (record.isToCollect) {
            add("to collect from ${record.person ?: "others"}")
            if (details.splitWaiting > 0) add("${Money.spoken(details.splitWaiting)} received")
        }
        if (subtitlePrefix != null) add(subtitlePrefix)
        if (showTime) add(record.occurredAt.atZone(zone).format(timeFormat))
        if (!isTransfer) add(details.accountName)
        if (record.note.isNotBlank()) add(record.note)
        if (record.recurringRuleId != null) add("repeats")
    }.joinToString(", ")
    ListRow(
        title = title,
        subtitle = parts.joinToString(" · "),
        subtitleLines = if (splitLine != null) 2 else 1,
        onLongClickLabel = if (record.isToCollect || record.isRepayment) null else "Duplicate",
        icon = if (isTransfer) Icons.AutoMirrored.Outlined.CompareArrows else null,
        iconKey = if (isTransfer) null else details.categoryIconKey ?: "other",
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        spokenText = spoken,
        trailing = {
            when (record.kind) {
                RecordKind.EXPENSE -> AmountText(-record.amount, sign = Money.Sign.ALWAYS, color = MrTheme.colors.expense)
                RecordKind.INCOME -> AmountText(record.amount, sign = Money.Sign.ALWAYS, color = MrTheme.colors.positive)
                RecordKind.TRANSFER -> if (repayment) AmountText(record.amount, sign = Money.Sign.ALWAYS, color = MrTheme.colors.positive)
                else AmountText(record.amount, color = MrTheme.colors.transfer)
            }
        },
    )
}
