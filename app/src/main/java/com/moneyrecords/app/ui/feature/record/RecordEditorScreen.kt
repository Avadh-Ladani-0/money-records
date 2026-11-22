package com.moneyrecords.app.ui.feature.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import com.moneyrecords.app.domain.recurring.Recurrence
import com.moneyrecords.app.domain.model.RecurringRuleDetails
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.model.Record
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.ui.components.AccountDropdown
import com.moneyrecords.app.ui.components.AmountKeypad
import com.moneyrecords.app.ui.components.CategoryPickerSheet
import com.moneyrecords.app.ui.components.EditorTopBar
import com.moneyrecords.app.ui.components.MrCard
import com.moneyrecords.app.ui.components.MrTextField
import com.moneyrecords.app.ui.components.PickerField
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.SegmentedPills
import com.moneyrecords.app.ui.components.ValueChip
import com.moneyrecords.app.ui.components.rememberNotificationPermissionRequest
import com.moneyrecords.app.ui.theme.InterFamily
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Layout, top to bottom: type · category/account · date/time · note · amount · keypad · save.
 * The amount sits directly above the keypad so you see the number where you type it.
 */
@Composable
fun RecordEditorScreen(
    onClose: () -> Unit,
    onDeleted: (Record) -> Unit,
    onAddAccount: () -> Unit,
    onOpenRecurring: (Long) -> Unit,
    viewModel: RecordEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickRepeat by remember { mutableStateOf(false) }
    val askNotificationPermission = rememberNotificationPermissionRequest()
    // While the system keyboard is up (typing a note) the keypad steps aside.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RecordEditorEvent.Saved -> onClose()
                is RecordEditorEvent.Deleted -> { onDeleted(event.record); onClose() }
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        EditorTopBar(
            title = when (state.mode) {
                EditorMode.NEW -> "New record"
                EditorMode.EDIT -> "Edit record"
                EditorMode.DUPLICATE -> "Duplicate record"
                EditorMode.RECURRING_DUE -> "Recurring record"
            },
            onClose = onClose,
            onDelete = if (state.mode == EditorMode.EDIT) ({ confirmDelete = true }) else null,
        )

        // Details. Scrolls only if the screen is very short.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            SegmentedPills(
                options = listOf(RecordKind.EXPENSE, RecordKind.INCOME, RecordKind.TRANSFER),
                selected = state.kind,
                label = { it.label() },
                onSelect = viewModel::onKind,
            )

            if (state.needsAccount) {
                MrCard {
                    Text("Add an account first", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Records belong to an account — Cash, Bank, Card or Wallet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.md))
                    PillButton("Add account", onClick = onAddAccount)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (state.kind == RecordKind.TRANSFER) {
                    AccountDropdown("From", state.accounts, state.accountId, viewModel::onAccount, Modifier.weight(1f))
                    AccountDropdown("To", state.accounts, state.toAccountId, viewModel::onToAccount, Modifier.weight(1f))
                } else {
                    val category = state.selectedCategory
                    PickerField(
                        label = "Category",
                        value = category?.name,
                        icon = null,
                        iconKey = category?.iconKey,
                        onClick = { pickCategory = true },
                        modifier = Modifier.weight(1f),
                    )
                    AccountDropdown("Account", state.accounts, state.accountId, viewModel::onAccount, Modifier.weight(1f))
                }
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                ValueChip(state.date.friendly(), Icons.Outlined.CalendarToday, onClick = { pickDate = true })
                ValueChip(state.time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())), Icons.Outlined.Schedule, onClick = { pickTime = true })
            }
            state.linkedRule?.let { linked ->
                LinkedRepeatRow(linked, onOpen = { onOpenRecurring(linked.rule.id) })
            }
            if (state.mode == EditorMode.NEW || state.mode == EditorMode.DUPLICATE) {
                RepeatRow(
                    repeat = state.repeat,
                    recordDate = state.date,
                    onTick = { checked -> if (checked) pickRepeat = true else viewModel.onRepeat(null) },
                    onEdit = { pickRepeat = true },
                )
            }

            MrTextField(value = state.note, onValueChange = viewModel::onNote, label = "Note")
            state.error?.let { Text(it, color = MrTheme.colors.accent, style = MaterialTheme.typography.bodyMedium) }
        }

        // Entry: amount right above the keypad, then save.
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.screen, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            AmountDisplay(state)
            AnimatedVisibility(visible = !imeVisible) { AmountKeypad(onKey = viewModel::onKey) }
            PillButton(
                text = when (state.mode) {
                    EditorMode.EDIT -> "Save changes"
                    EditorMode.RECURRING_DUE -> "Add"
                    else -> "Save"
                },
                onClick = {
                    // Repeating records notify when due, so make sure notifications are allowed.
                    if (state.repeat != null) askNotificationPermission()
                    viewModel.save()
                },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (pickCategory) {
        CategoryPickerSheet(
            title = if (state.kind == RecordKind.INCOME) "Income category" else "Expense category",
            categories = state.categories,
            recentIds = state.recentCategoryIds,
            selectedId = state.categoryId,
            onPick = viewModel::onCategory,
            onCreate = viewModel::createCategory,
            onCombine = viewModel::combineCategories,
            onDismiss = { pickCategory = false },
        )
    }
    if (pickRepeat) {
        RepeatSheet(current = state.repeat, start = state.date, onDone = viewModel::onRepeat, onDismiss = { pickRepeat = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this record?") },
            text = { Text("You can undo right after deleting.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete", color = MrTheme.colors.accent) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (pickDate) DateDialog(state.date, onDismiss = { pickDate = false }, onPick = { viewModel.onDate(it); pickDate = false })
    if (pickTime) TimeDialog(state.time, onDismiss = { pickTime = false }, onPick = { viewModel.onTime(it); pickTime = false })
}

@Composable
private fun AmountDisplay(state: RecordEditorState) {
    val sign = when (state.kind) {
        RecordKind.EXPENSE -> "−₹"
        RecordKind.INCOME -> "+₹"
        RecordKind.TRANSFER -> "₹"
    }
    val color = when (state.kind) {
        RecordKind.EXPENSE -> MrTheme.colors.negative
        else -> MaterialTheme.colorScheme.onBackground
    }
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = InterFamily, fontSize = 0.5.em)) { append("$sign ") }
        append(state.amountText.ifEmpty { "0" })
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        BasicText(
            text = text,
            style = MaterialTheme.typography.displayMedium.copy(
                color = if (state.amountText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else color,
                textAlign = TextAlign.End,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = 44.sp, stepSize = 2.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        state.preview?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    // DatePicker works in UTC midnight millis.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

fun RecordKind.label(): String = when (this) {
    RecordKind.EXPENSE -> "Expense"
    RecordKind.INCOME -> "Income"
    RecordKind.TRANSFER -> "Transfer"
}

/** "Today", "Yesterday", or "Sat, 21 Nov" (with year if not this year). */
fun LocalDate.friendly(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> format(DateTimeFormatter.ofPattern(if (year == today.year) "EEE, d MMM" else "d MMM yyyy", Locale.getDefault()))
}

/**
 * "☐ Repeat" tick box. Ticking opens the recurrence window; once set, the details show beside it
 * ("Monthly on the 5th · next 5 Dec") and tapping them edits. Unticking makes it a one-time record.
 */
@Composable
private fun RepeatRow(repeat: RepeatSpec?, recordDate: LocalDate, onTick: (Boolean) -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = repeat != null, role = Role.Checkbox, onValueChange = { onTick(it) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = repeat != null, onCheckedChange = null)
        Text("Repeat", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Space.xs))
        if (repeat != null) {
            Text(
                " · " + repeat.describe(recordDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClick = onEdit),
            )
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit repeat") }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * Editing a record that a repeat created: "↻ Repeats · Monthly on the 22nd · next 22 Dec ›".
 * One tap opens that repeat's editor. Changing the repeat never changes this record.
 */
@Composable
private fun LinkedRepeatRow(linked: RecurringRuleDetails, onOpen: () -> Unit) {
    val rule = linked.rule
    val ended = !rule.active && rule.endDate != null && rule.nextRunDate.isAfter(rule.endDate)
    val status = when {
        ended -> "ended"
        !rule.active -> "paused"
        Recurrence.isOnce(rule) -> null
        else -> "next ${Recurrence.shortDate(rule.nextRunDate)}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.EventRepeat, contentDescription = null, modifier = Modifier.padding(horizontal = Space.md))
        Column(Modifier.weight(1f)) {
            Text("Repeats", style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(Recurrence.describe(rule), status).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Edit repeat")
    }
}
