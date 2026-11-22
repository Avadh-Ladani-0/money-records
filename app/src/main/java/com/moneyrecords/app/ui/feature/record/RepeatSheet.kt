package com.moneyrecords.app.ui.feature.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.moneyrecords.app.ui.components.rememberHaptics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moneyrecords.app.domain.model.Frequency
import com.moneyrecords.app.domain.recurring.Recurrence
import com.moneyrecords.app.ui.components.ChoiceChip
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.ValueChip
import com.moneyrecords.app.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** "Monthly on the 5th · next 5 Dec", "Once on 15 Mar". */
fun RepeatSpec.describe(recordDate: LocalDate): String {
    val next = nextFrom(recordDate)
    return if (once) "Once on ${Recurrence.shortDate(next)}"
    else Recurrence.describe(frequency, interval, if (nextDate != null) next else recordDate) + " · next ${Recurrence.shortDate(next)}"
}

/** Text for a Repeat chip (used in Settings → Recurring). */
fun RepeatSpec?.chipLabel(start: LocalDate): String = this?.describe(start) ?: "Repeat"

/**
 * Choose how a record comes back: once on a date, or every N days / weeks / months / years.
 * "Next due" defaults to the natural next date and can be moved; later dates follow from it.
 * [start] is the record's date. Items ask before they're added (a notification when due).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RepeatSheet(current: RepeatSpec?, start: LocalDate, onDone: (RepeatSpec?) -> Unit, onDismiss: () -> Unit) {
    var once by remember { mutableStateOf(current?.once == true) }
    var frequency by remember { mutableStateOf(current?.frequency ?: Frequency.MONTHLY) }
    var interval by remember { mutableStateOf(current?.interval ?: 1) }
    var endDate by remember { mutableStateOf(current?.endDate) }
    // Null = use the natural next date (follows the choices above).
    var nextDate by remember { mutableStateOf(current?.nextDate) }
    var picking by remember { mutableStateOf<String?>(null) } // "next" | "end"
    val haptics = rememberHaptics()

    val spec = RepeatSpec(frequency, interval, if (once) null else endDate, nextDate, once)
    val next = spec.nextFrom(start)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = Space.screen).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text("Repeat", style = MaterialTheme.typography.headlineSmall)
            SectionLabel("How often")
            // One row that scrolls sideways — no lone chip wrapping onto a second line.
            Row(
                Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                listOf(Frequency.DAILY to "Daily", Frequency.WEEKLY to "Weekly", Frequency.MONTHLY to "Monthly", Frequency.YEARLY to "Yearly")
                    .forEach { (f, label) -> ChoiceChip(label, selected = !once && frequency == f, onClick = { once = false; frequency = f }) }
                ChoiceChip("Once on a date", selected = once, onClick = { once = true })
            }

            if (!once) {
                SectionLabel("Every")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (interval > 1) { haptics.tick(); interval-- } else haptics.reject()
                    }) { Icon(Icons.Outlined.Remove, contentDescription = "Decrease interval") }
                    Text(
                        "$interval",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    IconButton(onClick = {
                        if (interval < 99) { haptics.tick(); interval++ } else haptics.reject()
                    }) { Icon(Icons.Outlined.Add, contentDescription = "Increase interval") }
                    val unit = when (frequency) { Frequency.DAILY -> "day"; Frequency.WEEKLY -> "week"; Frequency.MONTHLY -> "month"; Frequency.YEARLY -> "year" }
                    Text(if (interval == 1) unit else "${unit}s", style = MaterialTheme.typography.bodyLarge)
                }
            }

            SectionLabel(if (once) "On" else "Next due")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                ValueChip(next.friendly(), Icons.Outlined.EventRepeat, onClick = { picking = "next" })
                if (nextDate != null && !once) {
                    TextButton(onClick = { nextDate = null }) { Text("Reset", color = MaterialTheme.colorScheme.onBackground) }
                }
            }

            if (!once) {
                SectionLabel("Ends")
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ChoiceChip("Never", selected = endDate == null, onClick = { endDate = null })
                    ValueChip(endDate?.friendly() ?: "On a date…", Icons.Outlined.Event, onClick = { picking = "end" })
                }
            }

            Text(
                spec.describe(start) + ". When it's due you'll get a notification to Add, Skip or Edit it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PillButton("Done", onClick = { haptics.confirm(); onDone(spec); onDismiss() }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.lg))
        }
    }

    when (picking) {
        "next" -> DatePickDialog(
            initial = next,
            earliest = start.plusDays(1),
            onPick = { nextDate = it; if (endDate != null && endDate!!.isBefore(it)) endDate = null },
            onDismiss = { picking = null },
        )
        "end" -> DatePickDialog(
            initial = endDate ?: next.plusMonths(12),
            earliest = next,
            onPick = { endDate = it },
            onDismiss = { picking = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(initial: LocalDate, earliest: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    if (!d.isBefore(earliest)) onPick(d)
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}
