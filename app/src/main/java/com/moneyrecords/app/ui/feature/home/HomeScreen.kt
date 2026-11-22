package com.moneyrecords.app.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.model.DueOccurrence
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.icons.CategoryIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material3.Icon
import com.moneyrecords.app.ui.components.AmountText
import com.moneyrecords.app.ui.components.DotProgress
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.HeroAmount
import com.moneyrecords.app.ui.components.MrCard
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.RecordItem
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.SettingsAction
import com.moneyrecords.app.ui.components.dotGrid
import com.moneyrecords.app.ui.feature.record.friendly
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onDuplicateRecord: (Long) -> Unit,
    onSeeAllRecords: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenBudgets: () -> Unit,
    onEditDue: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())) }
    val monthName = state.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val zone = remember { ZoneId.systemDefault() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
    ) {
        item { ScreenHeader(title = "MoneyRecords", eyebrow = today, actions = { SettingsAction(onOpenSettings) }) }

        item {
            MrCard {
                Column(Modifier.dotGrid(MrTheme.colors.dotGrid)) {
                    SectionLabel("Total balance")
                    Spacer(Modifier.height(Space.sm))
                    HeroAmount(
                        paise = state.totalBalance,
                        color = if (state.totalBalance < 0) MrTheme.colors.negative else MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(Space.xl))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("In · $monthName")
                            AmountText(state.summary.income, color = MrTheme.colors.positive)
                        }
                        Column(Modifier.weight(1f)) {
                            SectionLabel("Out · $monthName")
                            AmountText(state.summary.expense, color = MrTheme.colors.negative)
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "Net this month " + Money.format(state.summary.net, sign = Money.Sign.ALWAYS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.budget?.let { budget ->
            item {
                Spacer(Modifier.height(Space.md))
                MrCard(onClick = onOpenBudgets, contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Budget · $monthName", Modifier.weight(1f))
                        Text(
                            "${Money.format(budget.spent)} of ${Money.format(budget.budget.limit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (budget.isOver) MrTheme.colors.negative else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(Modifier.height(Space.sm))
                    DotProgress(budget.fraction, dotSize = 6.dp)
                }
            }
        }

        if (state.due.isNotEmpty()) {
            item {
                Spacer(Modifier.height(Space.xxl))
                SectionLabel("Due · recurring")
                Spacer(Modifier.height(Space.xs))
            }
            items(state.due, key = { "due-${it.details.rule.id}" }) { occurrence ->
                DueRow(
                    occurrence,
                    onAdd = { viewModel.addDue(occurrence) },
                    onSkip = { viewModel.skipDue(occurrence) },
                    onOpen = { onEditDue(occurrence.details.rule.id) },
                )
            }
        }

        item {
            Spacer(Modifier.height(Space.xxl))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel("Recent records")
                if (state.recent.isNotEmpty()) {
                    TextButton(onClick = onSeeAllRecords) { Text("See all", color = MaterialTheme.colorScheme.onBackground) }
                }
            }
        }

        when {
            state.loading -> Unit
            !state.hasAccounts -> item {
                EmptyState(
                    title = "Start with an account",
                    message = "Add Cash, a bank account or a card, then record your first income or expense.",
                    action = { PillButton("Add account", onClick = onAddAccount) },
                )
            }
            state.recent.isEmpty() -> item {
                EmptyState(title = "No records yet", message = "Tap + to add your first income or expense.")
            }
            else -> items(state.recent, key = { it.record.id }) { details ->
                RecordItem(
                    details = details,
                    onClick = { onOpenRecord(details.record.id) },
                    onLongClick = { onDuplicateRecord(details.record.id) },
                    showTime = false,
                    zone = zone,
                    subtitlePrefix = details.record.occurredAt.atZone(zone).toLocalDate().friendly(),
                )
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun DueRow(occurrence: DueOccurrence, onAdd: () -> Unit, onSkip: () -> Unit, onOpen: () -> Unit) {
    val d = occurrence.details
    ListRow(
        title = d.title,
        subtitle = "Due ${occurrence.date.friendly()} · ${d.accountName}",
        leading = {
            if (d.rule.kind == RecordKind.TRANSFER) Icon(Icons.AutoMirrored.Outlined.CompareArrows, contentDescription = null)
            else CategoryIcon(d.categoryIconKey)
        },
        onClick = onOpen,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    if (d.rule.kind == RecordKind.EXPENSE) -d.rule.amount else d.rule.amount,
                    sign = if (d.rule.kind == RecordKind.TRANSFER) Money.Sign.NONE else Money.Sign.ALWAYS,
                    color = when (d.rule.kind) {
                        RecordKind.EXPENSE -> MrTheme.colors.negative
                        RecordKind.INCOME -> MrTheme.colors.positive
                        RecordKind.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Row {
                    TextButton(onClick = onSkip) { Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = onAdd) { Text("Add", color = MrTheme.colors.accent) }
                }
            }
        },
    )
}
