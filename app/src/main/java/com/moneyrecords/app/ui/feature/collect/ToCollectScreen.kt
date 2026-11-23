package com.moneyrecords.app.ui.feature.collect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.split.OpenPart
import com.moneyrecords.app.ui.components.AccountDropdown
import com.moneyrecords.app.ui.components.AmountText
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.MrTextField
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.rememberHaptics
import com.moneyrecords.app.ui.feature.record.friendly
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Who paid what back: one bill, or one person across their bills. */
private sealed interface Receiving {
    data class Part(val part: OpenPart) : Receiving
    data class Person(val name: String, val owed: Long, val payerAccountId: Long) : Receiving
}

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

/**
 * Money friends owe you. This month's bills come first, by person; earlier months keep what wasn't
 * paid back, and every bill can still be marked received at any time.
 */
@Composable
fun ToCollectScreen(
    onBack: () -> Unit,
    onMoved: (message: String, undo: () -> Unit) -> Unit,
    onEditBill: (Long) -> Unit,
    viewModel: ToCollectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var receiving by remember { mutableStateOf<Receiving?>(null) }
    var openMonths by rememberSaveable { mutableStateOf(setOf<String>()) }

    fun LazyListScope.people(groups: List<PersonGroup>, keyPrefix: String) {
        groups.forEach { group ->
            item(key = "$keyPrefix-g-${group.name}") {
                Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("${group.name} · ${Money.format(group.total)}", Modifier.weight(1f))
                    // One payment for several bills: spread over them, oldest first.
                    if (group.isNamed && group.parts.size > 1) {
                        TextButton(onClick = {
                            receiving = Receiving.Person(group.name, state.owedBy(group.name), group.parts.first().payerAccountId)
                        }) { Text("Received from ${group.name}", color = MaterialTheme.colorScheme.onBackground) }
                    }
                }
            }
            items(group.parts, key = { "$keyPrefix-p-${it.legId}" }) { part ->
                PartRow(part, onReceived = { receiving = Receiving.Part(part) }, onEdit = { onEditBill(part.expenseId) }, modifier = Modifier.animateItem())
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg)) {
        item { ScreenHeader(title = "To collect", subtitle = "Bills you paid for others", onBack = onBack) }
        if (state.loading) return@LazyColumn

        state.awaiting.forEach { move ->
            item(key = "move-${move.payerAccountId}-${move.heldInAccountId}") {
                MoveCard(move, onMove = { amount ->
                    viewModel.move(move, amount) { undo -> onMoved("Moved ${Money.format(amount)} to ${move.payerName}", undo) }
                })
                Spacer(Modifier.height(Space.md))
            }
        }

        item(key = "this-month") {
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("This month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AmountText(state.thisMonthTotal, style = MaterialTheme.typography.titleMedium, animate = true)
            }
        }
        if (state.thisMonth.isEmpty()) {
            item(key = "this-month-empty") {
                Text(
                    "Nothing owed for this month. When you pay for friends, tick Split on the record.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.sm),
                )
            }
        } else {
            people(state.thisMonth, "now")
        }

        if (state.earlier.isNotEmpty()) {
            item(key = "earlier") {
                Spacer(Modifier.height(Space.xl))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Earlier months", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Not paid back — still counted as spending. Mark received any time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AmountText(state.earlierTotal, style = MaterialTheme.typography.titleMedium)
                }
            }
            state.earlier.forEach { month ->
                val key = month.month.toString()
                val open = key in openMonths
                item(key = "m-$key") {
                    ListRow(
                        title = month.month.format(monthFormat),
                        subtitle = "${Money.format(month.total)} not paid back",
                        onClick = { openMonths = if (open) openMonths - key else openMonths + key },
                        trailing = {
                            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "Hide" else "Show")
                        },
                    )
                }
                if (open) people(month.people, key)
            }
        }

        if (state.thisMonth.isEmpty() && state.earlier.isEmpty() && state.awaiting.isEmpty()) {
            item { EmptyState(title = "Nothing to collect", message = "When you pay for friends, tick Split on the record — their part shows up here.") }
        }
        item { Spacer(Modifier.height(Space.xxl)) }
    }

    receiving?.let { r -> ReceiveSheet(r, state, viewModel, onDismiss = { receiving = null }) }
}

@Composable
private fun PartRow(part: OpenPart, onReceived: () -> Unit, onEdit: () -> Unit, modifier: Modifier) {
    val date = Instant.ofEpochMilli(part.occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().friendly()
    ListRow(
        title = part.title,
        subtitle = listOfNotNull(
            date,
            "paid from ${part.payerName}",
            part.billTotal?.let { "bill ${Money.format(it)}" },
            part.waiting.takeIf { it > 0 }?.let { "${Money.format(it)} already received" },
        ).joinToString(" · "),
        subtitleLines = 2,
        iconKey = part.iconKey ?: "other",
        modifier = modifier,
        onClick = onEdit,
        spokenText = "${part.person?.let { "$it owes" } ?: "Others owe"} ${Money.spoken(part.outstanding)} for ${part.title}, $date",
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                AmountText(part.outstanding, style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onReceived) { Text("Received", color = MaterialTheme.colorScheme.onBackground) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveSheet(r: Receiving, state: ToCollectState, viewModel: ToCollectViewModel, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val owed = when (r) { is Receiving.Part -> r.part.outstanding; is Receiving.Person -> r.owed }
    val payer = when (r) { is Receiving.Part -> r.part.payerAccountId; is Receiving.Person -> r.payerAccountId }
    var text by remember { mutableStateOf(owed.asInput()) }
    var accountId by remember { mutableStateOf(state.defaultDestination(payer)) }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = Money.parse(text)
    val valid = amount != null && amount in 1..owed && accountId != null
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = Space.screen).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                when (r) {
                    is Receiving.Part -> "Received" + (r.part.person?.let { " from $it" } ?: "")
                    is Receiving.Person -> "Received from ${r.name}"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                when (r) {
                    is Receiving.Part -> "${r.part.title} · ${Money.format(owed)} owed."
                    is Receiving.Person -> "${Money.format(owed)} owed across their bills — it's applied to the oldest first."
                } + " A repayment isn't income — it's your money coming back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MrTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' }; error = null },
                label = "Amount received",
                prefix = "₹ ",
                keyboardType = KeyboardType.Decimal,
                isError = amount == null || amount <= 0 || amount > owed,
                supportingText = when {
                    amount != null && amount > owed -> "Only ${Money.format(owed)} is owed — record anything extra as income"
                    amount != null && amount in 1 until owed -> "${Money.format(owed - amount)} will still be owed"
                    else -> null
                },
            )
            AccountDropdown("Arrived in", state.accounts, accountId, { accountId = it }, Modifier.fillMaxWidth())
            error?.let { Text(it, color = MrTheme.colors.danger, style = MaterialTheme.typography.bodyMedium) }
            PillButton(
                "Save",
                enabled = valid,
                onClick = {
                    val done: (String?) -> Unit = { e -> if (e == null) { haptics.confirm(); onDismiss() } else { haptics.reject(); error = e } }
                    when (r) {
                        is Receiving.Part -> viewModel.receive(r.part, amount!!, accountId!!, done)
                        is Receiving.Person -> viewModel.receiveFromPerson(r.name, amount!!, accountId!!, done)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.lg))
        }
    }
}
