package com.moneyrecords.app.ui.feature.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.domain.model.RecurringRuleDetails
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.recurring.Recurrence
import com.moneyrecords.app.ui.components.AmountText
import com.moneyrecords.app.ui.components.EditorTopBar
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.GhostButton
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.MrTextField
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.Tag
import com.moneyrecords.app.ui.components.ValueChip
import com.moneyrecords.app.ui.feature.record.RepeatSheet
import com.moneyrecords.app.ui.feature.record.chipLabel
import com.moneyrecords.app.ui.feature.record.friendly
import com.moneyrecords.app.ui.icons.CategoryIcon
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import java.time.LocalDate

@Composable
fun RecurringScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, viewModel: RecurringListViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg)) {
        item { ScreenHeader(title = "Recurring", onBack = onBack) }
        val list = rules
        if (list != null && list.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing recurring yet",
                    message = "When adding a record, tap Repeat to make it monthly, weekly or yearly — rent, salary, subscriptions.",
                )
            }
        }
        items(list.orEmpty(), key = { it.rule.id }) { d -> RuleRow(d, onClick = { onOpen(d.rule.id) }) }
    }
}

@Composable
private fun RuleRow(d: RecurringRuleDetails, onClick: () -> Unit) {
    val r = d.rule
    val ended = !r.active && r.endDate != null && r.nextRunDate.isAfter(r.endDate)
    ListRow(
        title = d.title,
        subtitle = listOf(
            Recurrence.describe(r),
            when {
                ended -> "Ended"
                !r.active -> "Paused"
                else -> "Next ${r.nextRunDate.friendly()}"
            },
            d.accountName,
        ).joinToString(" · "),
        leading = {
            if (r.kind == RecordKind.TRANSFER) Icon(Icons.AutoMirrored.Outlined.CompareArrows, contentDescription = null)
            else CategoryIcon(d.categoryIconKey)
        },
        onClick = onClick,
        trailing = {
            if (!r.active) Tag(if (ended) "Ended" else "Paused") else AmountText(
                if (r.kind == RecordKind.EXPENSE) -r.amount else r.amount,
                sign = if (r.kind == RecordKind.TRANSFER) Money.Sign.NONE else Money.Sign.ALWAYS,
                color = when (r.kind) {
                    RecordKind.EXPENSE -> MrTheme.colors.negative
                    RecordKind.INCOME -> MrTheme.colors.positive
                    RecordKind.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditorScreen(onClose: () -> Unit, viewModel: RecurringEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickRepeat by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.done.collect { onClose() } }

    Column(Modifier.fillMaxSize().imePadding()) {
        EditorTopBar(title = "Recurring item", onClose = onClose, onDelete = { confirmDelete = true })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            state.details?.let { d ->
                ListRow(
                    title = d.title,
                    subtitle = if (d.rule.kind == RecordKind.TRANSFER) "${d.accountName} → ${d.toAccountName}" else d.accountName,
                    leading = { CategoryIcon(d.categoryIconKey) },
                )
            }
            MrTextField(
                value = state.amountText, onValueChange = viewModel::onAmount, label = "Amount", prefix = "₹ ",
                keyboardType = KeyboardType.Decimal, isError = state.amountText.isNotBlank() && state.amount == null,
            )
            MrTextField(value = state.note, onValueChange = viewModel::onNote, label = "Note")
            SectionLabel("Schedule")
            ValueChip(
                state.repeat?.copy(nextDate = state.nextDate).chipLabel(LocalDate.now()),
                Icons.Outlined.Repeat,
                onClick = { pickRepeat = true },
            )
            state.repeat?.endDate?.let {
                Text("Ends ${it.friendly()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "When it's due you'll get a notification to Add, Skip or Edit it. Changing this doesn't affect records already added.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GhostButton(if (state.active) "Pause" else "Resume", onClick = viewModel::togglePaused, modifier = Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MrTheme.colors.accent) }
            Spacer(Modifier.height(Space.lg))
        }
        PillButton("Save", onClick = viewModel::save, enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Space.screen))
    }

    if (pickRepeat) {
        RepeatSheet(
            current = state.repeat?.copy(nextDate = state.nextDate),
            start = LocalDate.now(),
            onDone = viewModel::onRepeat,
            onDismiss = { pickRepeat = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete recurring item?") },
            text = { Text("Records it already added stay as they are.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete", color = MrTheme.colors.accent) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
