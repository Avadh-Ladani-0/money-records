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
    val title = when {
        isTransfer -> "Transfer"
        else -> details.categoryName ?: "Uncategorised"
    }
    val where = if (isTransfer) "${details.accountName} → ${details.toAccountName ?: "?"}" else details.accountName
    val parts = buildList {
        if (subtitlePrefix != null) add(subtitlePrefix)
        if (showTime) add(record.occurredAt.atZone(zone).format(timeFormat))
        add(where)
        if (record.note.isNotBlank()) add(record.note)
        if (record.recurringRuleId != null) add("↻")
    }
    val spoken = buildList {
        add(
            when (record.kind) {
                RecordKind.EXPENSE -> "Expense, $title"
                RecordKind.INCOME -> "Income, $title"
                RecordKind.TRANSFER -> "Transfer from ${details.accountName} to ${details.toAccountName ?: "unknown account"}"
            },
        )
        add(Money.spoken(record.amount))
        if (subtitlePrefix != null) add(subtitlePrefix)
        if (showTime) add(record.occurredAt.atZone(zone).format(timeFormat))
        if (!isTransfer) add(details.accountName)
        if (record.note.isNotBlank()) add(record.note)
        if (record.recurringRuleId != null) add("repeats")
    }.joinToString(", ")
    ListRow(
        title = title,
        subtitle = parts.joinToString(" · "),
        icon = if (isTransfer) Icons.AutoMirrored.Outlined.CompareArrows else null,
        iconKey = if (isTransfer) null else details.categoryIconKey ?: "other",
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "Duplicate",
        spokenText = spoken,
        trailing = {
            when (record.kind) {
                RecordKind.EXPENSE -> AmountText(-record.amount, sign = Money.Sign.ALWAYS, color = MrTheme.colors.expense)
                RecordKind.INCOME -> AmountText(record.amount, sign = Money.Sign.ALWAYS, color = MrTheme.colors.positive)
                RecordKind.TRANSFER -> AmountText(record.amount, color = MrTheme.colors.transfer)
            }
        },
    )
}
