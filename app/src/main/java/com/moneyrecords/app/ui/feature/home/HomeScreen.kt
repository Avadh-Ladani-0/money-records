package com.moneyrecords.app.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.insights.CategoryShare
import com.moneyrecords.app.domain.model.DueOccurrence
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.RecurringRuleDetails
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.ui.components.AmountText
import com.moneyrecords.app.ui.components.DotProgress
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.GhostButton
import com.moneyrecords.app.ui.components.HeroAmount
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.MrCard
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.SettingsAction
import com.moneyrecords.app.ui.components.dotGrid
import com.moneyrecords.app.ui.components.rememberHaptics
import com.moneyrecords.app.ui.feature.budgets.label
import com.moneyrecords.app.ui.feature.budgets.statusColor
import com.moneyrecords.app.ui.feature.record.friendly
import com.moneyrecords.app.ui.feature.collect.MoveCard
import com.moneyrecords.app.ui.icons.categoryColor
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Home answers "how am I doing this month?": the balance and this month's in/out first, then the
 * budget, what's due and coming up, and where the money went. Every record lives in the Records tab.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onSeeAllRecords: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenBudgets: () -> Unit,
    onEditDue: (Long) -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenToCollect: () -> Unit,
    onMoved: (message: String, undo: () -> Unit) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())) }
    val monthName = state.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
    ) {
        item { ScreenHeader(title = "MoneyRecords", subtitle = today, actions = { SettingsAction(onOpenSettings) }) }

        item(key = "balance") {
            BalanceCard(state, monthName, onSeeAllRecords)
        }

        if (state.loading) return@LazyColumn

        if (!state.hasAccounts) {
            item(key = "start") {
                EmptyState(
                    title = "Start with an account",
                    message = "Add Cash, a bank account or a card, then record your first income or expense.",
                    action = { PillButton("Add account", onClick = onAddAccount) },
                )
            }
            return@LazyColumn
        }

        // Paid for friends: what's owed, and collected money waiting to go back to UPI Lite.
        if (state.toCollect > 0 || state.toCollectEarlier > 0 || state.awaiting.isNotEmpty()) {
            item(key = "collect-label") { Section("To collect", action = "All" to onOpenToCollect) }
            state.awaiting.forEach { move ->
                item(key = "move-${move.payerAccountId}-${move.heldInAccountId}") {
                    MoveCard(
                        move,
                        onMove = { amount -> viewModel.move(move, amount) { undo -> onMoved("Moved ${Money.format(amount)} to ${move.payerName}", undo) } },
                        modifier = Modifier.animateItem().padding(bottom = Space.sm),
                    )
                }
            }
            if (state.toCollect > 0 || state.toCollectEarlier > 0) item(key = "collect") {
                MrCard(onClick = onOpenToCollect, modifier = Modifier.animateItem(), contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Owed to you this month", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (state.toCollectBills == 0) "Nothing for this month"
                                else "${state.toCollectBills} ${if (state.toCollectBills == 1) "bill" else "bills"} · tap to mark received",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AmountText(state.toCollect, style = MaterialTheme.typography.titleMedium, animate = true)
                    }
                    if (state.toCollectEarlier > 0) {
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "${Money.format(state.toCollectEarlier)} not paid back from earlier months",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "budget-label") { Section("Budget · $monthName") }
        item(key = "budget") {
            BudgetBlock(state, onOpenBudgets, Modifier.animateItem())
        }

        if (state.due.isNotEmpty()) {
            item(key = "due-label") { Section("Due now") }
            items(state.due, key = { "due-${it.details.rule.id}" }) { occurrence ->
                DueRow(
                    occurrence,
                    onAdd = { haptics.confirm(); viewModel.addDue(occurrence) },
                    onSkip = { haptics.tick(); viewModel.skipDue(occurrence) },
                    onOpen = { onEditDue(occurrence.details.rule.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item(key = "upcoming-label") { Section("Coming up", action = "All" to onOpenRecurring) }
        if (state.upcoming.isEmpty()) {
            item(key = "upcoming-empty") {
                Hint("No recurring items yet. Tick Repeat when you add a record to set one up.")
            }
        } else {
            items(state.upcoming, key = { "up-${it.rule.id}" }) { d ->
                UpcomingRow(d, onClick = onOpenRecurring, modifier = Modifier.animateItem())
            }
        }

        item(key = "where-label") { Section("Where it went · $monthName", action = "Records" to onSeeAllRecords) }
        if (state.topCategories.isEmpty()) {
            item(key = "where-empty") { Hint("Nothing spent yet this month.") }
        } else {
            items(state.topCategories, key = { "cat-${it.categoryId}" }) { share ->
                CategoryShareRow(share, onClick = onSeeAllRecords, modifier = Modifier.animateItem())
            }
        }
        item { Spacer(Modifier.height(Space.fabClearance)) }
    }
}

@Composable
private fun Section(title: String, action: Pair<String, () -> Unit>? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.xxl, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title, Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = action.second) { Text(action.first, color = MaterialTheme.colorScheme.onBackground) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Space.sm, horizontal = Space.xs),
    )
}

@Composable
private fun BalanceCard(state: HomeState, monthName: String, onSeeAllRecords: () -> Unit) {
    MrCard {
        Column(Modifier.dotGrid(MrTheme.colors.dotGrid)) {
            SectionLabel("Total balance")
            Spacer(Modifier.height(Space.sm))
            if (state.loading) {
                // Nothing until the real figure is known — no "₹0.00" flash.
                Spacer(Modifier.height(64.dp))
            } else {
                HeroAmount(
                    paise = state.totalBalance,
                    color = if (state.totalBalance < 0) MrTheme.colors.negative else MaterialTheme.colorScheme.onBackground,
                    animate = true,
                )
            }
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                Figure("In · $monthName", state.summary.income, MrTheme.colors.positive, state.loading, Modifier.weight(1f))
                Figure("Out · $monthName", state.summary.expense, MrTheme.colors.expense, state.loading, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val net = state.summary.net
                Text("Net this month ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!state.loading) {
                    AmountText(
                        net,
                        sign = Money.Sign.ALWAYS,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (net >= 0) MrTheme.colors.positive else MrTheme.colors.expense,
                        animate = true,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSeeAllRecords) { Text("All records", color = MaterialTheme.colorScheme.onBackground) }
            }
        }
    }
}

@Composable
private fun Figure(label: String, paise: Long, color: androidx.compose.ui.graphics.Color, loading: Boolean, modifier: Modifier) {
    Column(modifier) {
        SectionLabel(label)
        if (loading) Spacer(Modifier.height(24.dp))
        else AmountText(paise, color = color, animate = true)
    }
}

@Composable
private fun BudgetBlock(state: HomeState, onOpenBudgets: () -> Unit, modifier: Modifier) {
    val budget = state.budget
    MrCard(onClick = onOpenBudgets, modifier = modifier, contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.lg)) {
        when {
            budget != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Overall", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${Money.format(budget.spent)} of ${Money.format(budget.budget.limit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor(budget),
                    )
                }
                Spacer(Modifier.height(Space.sm))
                DotProgress(
                    budget.fraction,
                    dotSize = 6.dp,
                    activeColor = statusColor(budget),
                    spoken = "${Money.spoken(budget.spent)} of ${Money.spoken(budget.budget.limit)}",
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    when {
                        budget.isOver -> "Over by ${Money.format(-budget.remaining)}"
                        state.budgetPerDay != null -> "${Money.format(budget.remaining)} left · ${Money.format(state.budgetPerDay)} a day"
                        else -> "${Money.format(budget.remaining)} left"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (budget.isOver || budget.isNearLimit) statusColor(budget) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.tightest?.let { TightestLine(it) }
            }
            state.hasAnyBudget -> {
                Text("No overall budget", style = MaterialTheme.typography.titleMedium)
                state.tightest?.let { TightestLine(it) }
                    ?: Text("Your budgets are all comfortably under their limits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("No budget yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${Money.format(state.summary.expense)} spent this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Space.md))
                    GhostButton("Set a budget", onClick = onOpenBudgets)
                }
            }
        }
    }
}

@Composable
private fun TightestLine(p: com.moneyrecords.app.domain.model.BudgetProgress) {
    Spacer(Modifier.height(Space.md))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(p.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (p.isOver) "Over by ${Money.format(-p.remaining)}" else "${(p.fraction * 100).toInt()}% used",
            style = MaterialTheme.typography.bodySmall,
            color = if (p.isOver || p.isNearLimit) statusColor(p) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(Space.xs))
    DotProgress(
        p.fraction,
        dotSize = 4.dp,
        activeColor = statusColor(p),
        spoken = "${p.label()}, ${Money.spoken(p.spent)} of ${Money.spoken(p.budget.limit)}",
    )
}

private fun amountFor(kind: RecordKind, amount: Long) = if (kind == RecordKind.EXPENSE) -amount else amount

@Composable
private fun kindColor(kind: RecordKind) = when (kind) {
    RecordKind.EXPENSE -> MrTheme.colors.expense
    RecordKind.INCOME -> MrTheme.colors.positive
    RecordKind.TRANSFER -> MrTheme.colors.transfer
}

@Composable
private fun DueRow(occurrence: DueOccurrence, onAdd: () -> Unit, onSkip: () -> Unit, onOpen: () -> Unit, modifier: Modifier) {
    val d = occurrence.details
    val isTransfer = d.rule.kind == RecordKind.TRANSFER
    ListRow(
        title = d.title,
        subtitle = "Due ${occurrence.date.friendly()} · ${d.accountName}",
        icon = if (isTransfer) Icons.AutoMirrored.Outlined.CompareArrows else null,
        iconKey = if (isTransfer) null else d.categoryIconKey ?: "other",
        onClick = onOpen,
        modifier = modifier,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    amountFor(d.rule.kind, d.rule.amount),
                    sign = if (isTransfer) Money.Sign.NONE else Money.Sign.ALWAYS,
                    color = kindColor(d.rule.kind),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row {
                    TextButton(onClick = onSkip) { Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = onAdd) { Text("Add", color = MaterialTheme.colorScheme.onBackground) }
                }
            }
        },
    )
}

@Composable
private fun UpcomingRow(d: RecurringRuleDetails, onClick: () -> Unit, modifier: Modifier) {
    val isTransfer = d.rule.kind == RecordKind.TRANSFER
    ListRow(
        title = d.title,
        subtitle = "${d.rule.nextRunDate.friendly()} · ${d.accountName}",
        icon = if (isTransfer) Icons.AutoMirrored.Outlined.CompareArrows else null,
        iconKey = if (isTransfer) null else d.categoryIconKey ?: "other",
        onClick = onClick,
        modifier = modifier,
        spokenText = "${d.title}, ${Money.spoken(d.rule.amount)}, on ${d.rule.nextRunDate.friendly()}",
        trailing = {
            AmountText(
                amountFor(d.rule.kind, d.rule.amount),
                sign = if (isTransfer) Money.Sign.NONE else Money.Sign.ALWAYS,
                color = kindColor(d.rule.kind),
                style = MaterialTheme.typography.titleSmall,
            )
        },
    )
}

@Composable
private fun CategoryShareRow(share: CategoryShare, onClick: () -> Unit, modifier: Modifier) {
    val percent = (share.share * 100).toInt().coerceAtLeast(1)
    ListRow(
        title = share.name,
        iconKey = share.iconKey,
        onClick = onClick,
        modifier = modifier,
        spokenText = "${share.name}, ${Money.spoken(share.spent)}, $percent percent of spending",
        trailing = {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(120.dp)) {
                AmountText(share.spent, style = MaterialTheme.typography.titleSmall, animate = true)
                Spacer(Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DotProgress(
                        share.share,
                        dotSize = 4.dp,
                        activeColor = categoryColor(share.iconKey),
                        modifier = Modifier.weight(1f).semantics { contentDescription = "" },
                    )
                    Text(
                        " $percent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
