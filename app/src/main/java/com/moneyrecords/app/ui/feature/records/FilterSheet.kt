package com.moneyrecords.app.ui.feature.records

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
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.model.RecordKind
import com.moneyrecords.app.ui.components.ChoiceChip
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.ValueChip
import com.moneyrecords.app.ui.feature.record.label
import com.moneyrecords.app.ui.icons.AppIcons
import com.moneyrecords.app.ui.icons.CategoryIcon
import com.moneyrecords.app.ui.components.pickerTiles
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.ui.theme.Space
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Bottom sheet with every Records filter. Changes apply immediately; "Done" just closes. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(state: RecordsState, viewModel: RecordsViewModel, onDismiss: () -> Unit) {
    val q = state.query
    var pickRange by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (q.filterCount > 0) TextButton(onClick = viewModel::resetFilters) { Text("Reset", color = MaterialTheme.colorScheme.onBackground) }
            }

            SectionLabel("Dates")
            ValueChip(
                text = q.range?.let { "${it.start.format(fmt)} – ${it.endInclusive.format(fmt)}" } ?: "Whole month · choose a custom range",
                icon = Icons.Outlined.DateRange,
                onClick = { pickRange = true },
            )

            SectionLabel("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                ChoiceChip("All", selected = q.kind == null, onClick = { viewModel.setKind(null) })
                listOf(RecordKind.EXPENSE, RecordKind.INCOME, RecordKind.TRANSFER).forEach { kind ->
                    ChoiceChip(kind.label(), selected = q.kind == kind, onClick = { viewModel.setKind(kind) })
                }
            }

            SectionLabel("Accounts · any of")
            if (state.accounts.isEmpty()) {
                Text("No accounts yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                state.accounts.forEach { account ->
                    ChoiceChip(
                        text = account.name + if (account.archived) " (archived)" else "",
                        selected = account.id in q.accountIds,
                        onClick = { viewModel.toggleAccount(account.id) },
                        icon = AppIcons.forKey(account.iconKey),
                    )
                }
            }

            CategorySection("Expense categories · any of", state.expenseCategories, q.categoryIds, viewModel::toggleCategory)
            CategorySection("Income categories · any of", state.incomeCategories, q.categoryIds, viewModel::toggleCategory)

            Spacer(Modifier.height(Space.sm))
            PillButton("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.lg))
        }
    }

    if (pickRange) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = q.range?.start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = q.range?.endInclusive?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis!!.toUtcDate()
                        val end = pickerState.selectedEndDateMillis?.toUtcDate() ?: start
                        viewModel.setRange(start..end)
                        pickRange = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(title: String, tree: List<Category>, selected: Set<Long>, onToggle: (Long) -> Unit) {
    if (tree.isEmpty()) return
    SectionLabel(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        // Same arrangement as the record picker: combined categories as their own chips after their parent.
        pickerTiles(tree).forEach { tile ->
            val parentSelected = tile.parentId != null && tile.parentId in selected
            ChoiceChip(
                tile.name,
                selected = tile.id in selected || parentSelected,
                onClick = { onToggle(tile.id) },
                leading = { CategoryIcon(tile.iconKey, size = 18.dp) },
            )
            tile.children.forEach { child ->
                ChoiceChip("↳ ${child.name}", selected = child.id in selected || tile.id in selected, onClick = { onToggle(child.id) })
            }
        }
    }
}

private fun Long.toUtcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
