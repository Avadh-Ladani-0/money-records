package com.moneyrecords.app.ui.feature.record

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.BaselineShift
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.ui.components.rememberHaptics
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
    onDeleted: (Record, com.moneyrecords.app.domain.split.SplitSpec?, Boolean) -> Unit,
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
    var pickSplit by remember { mutableStateOf(false) }
    var cantDelete by remember { mutableStateOf<String?>(null) }
    val askNotificationPermission = rememberNotificationPermissionRequest()
    // While the system keyboard is up (typing a note) the keypad steps aside.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val haptics = rememberHaptics()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RecordEditorEvent.Saved -> { haptics.confirm(); onClose() }
                is RecordEditorEvent.Deleted -> {
                    haptics.confirm()
                    onDeleted(event.record, if (event.canUndo) event.split else null, event.canUndo)
                    onClose()
                }
                is RecordEditorEvent.CantDelete -> { haptics.reject(); cantDelete = event.reason }
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
        val details: @Composable (Modifier) -> Unit = { mod ->
        Column(
            mod
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
            if ((state.mode == EditorMode.NEW || state.mode == EditorMode.DUPLICATE) && state.split == null) {
                RepeatRow(
                    repeat = state.repeat,
                    recordDate = state.date,
                    onTick = { checked -> if (checked) pickRepeat = true else viewModel.onRepeat(null) },
                    onEdit = { pickRepeat = true },
                )
            }
            // Paid for a group: split the bill (expenses only; not with Repeat).
            if (state.kind == RecordKind.EXPENSE && state.repeat == null && state.mode != EditorMode.RECURRING_DUE) {
                SplitRow(
                    state = state,
                    onTick = { checked -> if (checked) pickSplit = true else viewModel.onSplit(null) },
                    onEdit = { pickSplit = true },
                )
            }

            MrTextField(value = state.note, onValueChange = viewModel::onNote, label = "Note")
            state.error?.let { Text(it, color = MrTheme.colors.danger, style = MaterialTheme.typography.bodyMedium) }
        }
        }

        // Entry: amount right above the keypad, then save.
        val entry: @Composable (Modifier, androidx.compose.ui.unit.Dp) -> Unit = { mod, keyHeight ->
        Column(
            mod
                .navigationBarsPadding()
                .padding(horizontal = Space.screen, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            AmountDisplay(state)
            AnimatedVisibility(visible = !imeVisible) {
                AmountKeypad(onKey = viewModel::onKey, accepts = viewModel::accepts, keyHeight = keyHeight)
            }
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
                accent = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        }

        // Portrait: details above, amount + keypad below. Landscape: side by side, so nothing is
        // pushed off a short screen.
        if (landscape) {
            Row(Modifier.weight(1f)) {
                details(Modifier.weight(1f))
                entry(Modifier.weight(1f).verticalScroll(rememberScrollState()), 40.dp)
            }
        } else {
            details(Modifier.weight(1f))
            entry(Modifier, 54.dp)
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
    cantDelete?.let { reason ->
        AlertDialog(
            onDismissRequest = { cantDelete = null },
            title = { Text("Can't delete yet") },
            text = { Text(reason) },
            confirmButton = { TextButton(onClick = { cantDelete = null }) { Text("OK") } },
        )
    }
    if (pickSplit) {
        val total = state.amountPaise
        if (total == null || total <= 0) {
            LaunchedEffect(Unit) { pickSplit = false }
        } else {
            SplitSheet(
                total = total,
                payerName = state.accounts.firstOrNull { it.id == state.accountId }?.name,
                current = state.split,
                people = state.people,
                onDone = viewModel::onSplit,
                onDismiss = { pickSplit = false },
            )
        }
    }
    if (pickRepeat) {
        RepeatSheet(current = state.repeat, start = state.date, onDone = viewModel::onRepeat, onDismiss = { pickRepeat = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this record?") },
            text = { Text("You can undo right after deleting.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete", color = MrTheme.colors.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (pickDate) DateDialog(state.date, onDismiss = { pickDate = false }, onPick = { viewModel.onDate(it); pickDate = false })
    if (pickTime) TimeDialog(state.time, onDismiss = { pickTime = false }, onPick = { viewModel.onTime(it); pickTime = false })
}

/**
 * The amount being typed — the answer this screen exists for, so it's the biggest thing on it.
 * Read aloud as it changes ("Expense amount, 250 rupees").
 */
@Composable
private fun AmountDisplay(state: RecordEditorState) {
    val sign = when (state.kind) {
        RecordKind.EXPENSE -> "−₹"
        RecordKind.INCOME -> "+₹"
        RecordKind.TRANSFER -> "₹"
    }
    val color = when (state.kind) {
        RecordKind.EXPENSE -> MrTheme.colors.expense
        RecordKind.INCOME -> MrTheme.colors.positive
        RecordKind.TRANSFER -> MrTheme.colors.transfer
    }
    val empty = state.amountText.isEmpty()
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = InterFamily, fontSize = 0.45.em, baselineShift = BaselineShift(0.3f))) { append("$sign ") }
        append(state.amountText.ifEmpty { "0" })
    }
    val spokenAmount = state.amountPaise?.let { Money.spoken(it) } ?: if (empty) "empty" else state.amountText
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${state.kind.label()} amount, $spokenAmount" + (state.amountPaise?.takeIf { state.preview != null }?.let { ", equals ${Money.spoken(it)}" } ?: "")
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.End,
    ) {
        // Big by default; steps down only as the figure gets long (auto-size shrank short amounts).
        val chars = state.amountText.length
        val size = when {
            chars <= 6 -> 72.sp
            chars <= 9 -> 56.sp
            chars <= 13 -> 40.sp
            else -> 28.sp
        }
        BasicText(
            text = text,
            style = MaterialTheme.typography.displayLarge.copy(
                // An empty amount is a clear, readable placeholder — not a faint dotted zero.
                color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else color,
                textAlign = TextAlign.End,
                fontSize = size,
                lineHeight = size * 1.1f,
            ),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(visible = state.preview != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                state.preview.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The tick box and its label are one control; the details next to it are a separate one.
        Row(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .toggleable(
                    value = repeat != null,
                    role = Role.Checkbox,
                    onValueChange = { haptics.toggle(it); onTick(it) },
                )
                .heightIn(min = 48.dp)
                .padding(end = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = repeat != null, onCheckedChange = null)
            Text("Repeat", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Space.xs))
        }
        AnimatedVisibility(visible = repeat != null, modifier = Modifier.weight(1f), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClickLabel = "Edit repeat", role = Role.Button, onClick = onEdit)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    repeat?.describe(recordDate).orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.sm).size(20.dp),
                )
            }
        }
    }
}

/**
 * "☐ Split" — the bill was paid for a group. Ticking opens the split; once set it reads
 * "Split · ₹150 to collect". After a repayment it's locked and just says what's left.
 */
@Composable
private fun SplitRow(state: RecordEditorState, onTick: (Boolean) -> Unit, onEdit: () -> Unit) {
    val haptics = rememberHaptics()
    val spec = state.splitSpec
    if (state.splitLocked) {
        Text(
            "Split bill · some of it has been paid back, so the amount and split can't change.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Space.xs),
        )
        return
    }
    val canSplit = (state.amountPaise ?: 0) > 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .toggleable(
                    value = state.split != null,
                    enabled = canSplit || state.split != null,
                    role = Role.Checkbox,
                    onValueChange = { haptics.toggle(it); onTick(it) },
                )
                .heightIn(min = 48.dp)
                .padding(end = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = state.split != null, onCheckedChange = null, enabled = canSplit || state.split != null)
            Text("Split", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Space.xs))
        }
        val detail = when {
            spec == null && !canSplit -> "Enter the amount first"
            spec == null -> "Paid for friends too?"
            spec.problem != null -> "Shares don't add up — tap to fix"
            else -> "${Money.format(spec.myShare)} yours · ${Money.format(spec.toCollect)} to collect"
        }
        Row(
            Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .then(if (spec != null) Modifier.clickable(onClickLabel = "Edit split", role = Role.Button, onClick = onEdit) else Modifier)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = if (spec?.problem != null) MrTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (spec != null) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.sm).size(20.dp))
            }
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
