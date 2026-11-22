package com.moneyrecords.app.ui.feature.records

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import com.moneyrecords.app.ui.components.rememberHaptics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.ui.components.AmountText
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.Glyphs
import com.moneyrecords.app.ui.components.MrCard
import com.moneyrecords.app.ui.components.MrTextField
import com.moneyrecords.app.ui.components.RecordItem
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.SettingsAction
import com.moneyrecords.app.ui.feature.record.friendly
import com.moneyrecords.app.ui.feature.record.label
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecordsScreen(
    onOpenSettings: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onDuplicateRecord: (Long) -> Unit,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val q = state.query
    var searching by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val shortDate = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
    ) {
        item {
            ScreenHeader(
                title = "Records",
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) viewModel.setSearch("")
                    }) { Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, contentDescription = if (searching) "Close search" else "Search") }
                    Box {
                        val filtersLabel = if (q.filterCount > 0) "Filters, ${q.filterCount} active" else "Filters"
                        IconButton(onClick = { showFilters = true }) { Icon(Icons.Outlined.FilterList, contentDescription = filtersLabel) }
                        if (q.filterCount > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(Space.md)
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.onBackground, CircleShape),
                            )
                        }
                    }
                    SettingsAction(onOpenSettings)
                },
            )
        }

        item(key = "search") {
            AnimatedVisibility(visible = searching, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    MrTextField(value = q.search, onValueChange = viewModel::setSearch, label = "Search notes, categories, accounts")
                    Spacer(Modifier.height(Space.md))
                }
            }
        }

        // Period: month switcher, or the custom range with a clear button.
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (q.range == null) {
                    IconButton(onClick = viewModel::previousMonth) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month") }
                    // Slides in the direction you moved.
                    AnimatedContent(
                        targetState = state.shownMonth,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally { w -> dir * w / 3 } + fadeIn()).togetherWith(slideOutHorizontally { w -> -dir * w / 3 } + fadeOut())
                        },
                        modifier = Modifier.weight(1f),
                        label = "month",
                    ) { month ->
                        Text(
                            month.format(monthFormat),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                    IconButton(onClick = viewModel::nextMonth) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month") }
                } else {
                    Text(
                        "${q.range.start.format(shortDate)} – ${q.range.endInclusive.format(shortDate)} ${q.range.endInclusive.year}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.setRange(null) }) { Icon(Icons.Outlined.Close, contentDescription = "Clear date range") }
                }
            }
        }

        item {
            MrCard {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                    Total("In", state.income, MrTheme.colors.positive, Modifier.weight(1f))
                    Total("Out", state.expense, MrTheme.colors.expense, Modifier.weight(1f))
                    Total("Net", state.net, if (state.net >= 0) MrTheme.colors.positive else MrTheme.colors.expense, Modifier.weight(1f), signed = true)
                }
            }
            Spacer(Modifier.height(Space.md))
        }

        if (q.filterCount > 0) item(key = "active-filters") {
            ActiveFilters(state, onClearKind = { viewModel.setKind(null) }, onClearAccounts = viewModel::clearAccounts,
                onClearCategories = viewModel::clearCategories, onClearRange = { viewModel.setRange(null) }, onReset = viewModel::resetFilters)
            Spacer(Modifier.height(Space.sm))
        }

        if (!state.loading && state.days.isEmpty()) item {
            EmptyState(
                title = if (q.filterCount > 0 || q.search.isNotBlank()) "No matching records" else "Nothing recorded",
                message = if (q.filterCount > 0 || q.search.isNotBlank()) "Try a different search or clear some filters."
                else "Records for this period will appear here, grouped by day.",
                glyph = Glyphs.Empty,
            )
        }

        state.days.forEach { day ->
            item(key = "day-${day.date}") {
                Row(
                    // Lines up with the row amounts below (rows have Space.xs inner padding).
                    Modifier.animateItem().fillMaxWidth().padding(start = Space.xs, end = Space.xs, top = Space.lg, bottom = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel(day.date.friendly(), Modifier.weight(1f))
                    val net = day.income - day.expense
                    if (day.income != 0L || day.expense != 0L) {
                        Text(
                            Money.format(net, sign = Money.Sign.ALWAYS),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(day.records, key = { it.record.id }) { details ->
                RecordItem(
                    details = details,
                    onClick = { onOpenRecord(details.record.id) },
                    onLongClick = { haptics.longPress(); onDuplicateRecord(details.record.id) },
                    zone = zone,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item { Spacer(Modifier.height(Space.fabClearance)) }
    }

    if (showFilters) {
        FilterSheet(state = state, viewModel = viewModel, onDismiss = { showFilters = false })
    }
}

@Composable
private fun Total(label: String, paise: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier, signed: Boolean = false) {
    Column(modifier) {
        SectionLabel(label)
        AmountText(paise, color = color, sign = if (signed) Money.Sign.ALWAYS else Money.Sign.NEGATIVE_ONLY, style = MaterialTheme.typography.titleSmall, animate = true)
    }
}

@Composable
private fun ActiveFilters(
    state: RecordsState,
    onClearKind: () -> Unit,
    onClearAccounts: () -> Unit,
    onClearCategories: () -> Unit,
    onClearRange: () -> Unit,
    onReset: () -> Unit,
) {
    val q = state.query
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        q.kind?.let { FilterChipX(it.label(), onClearKind) }
        if (q.accountIds.isNotEmpty()) {
            val names = state.accounts.filter { it.id in q.accountIds }.map { it.name }
            FilterChipX(names.summary("accounts"), onClearAccounts)
        }
        if (q.categoryIds.isNotEmpty()) {
            val all = (state.expenseCategories + state.incomeCategories).flatMap { listOf(it) + it.children }
            FilterChipX(all.filter { it.id in q.categoryIds }.map { it.name }.summary("categories"), onClearCategories)
        }
        if (q.range != null) FilterChipX("Custom dates", onClearRange)
        if (q.filterCount > 1) FilterChipX("Clear all", onReset, showClose = false)
    }
}

private fun List<String>.summary(noun: String) = when (size) {
    0 -> "No $noun"
    1, 2 -> joinToString(", ")
    else -> "${take(1).first()} +${size - 1} $noun"
}

@Composable
private fun FilterChipX(text: String, onClear: () -> Unit, showClose: Boolean = true) {
    InputChip(
        selected = true,
        onClick = onClear,
        label = { Text(text) },
        trailingIcon = if (showClose) ({ Icon(Icons.Outlined.Close, contentDescription = "Remove filter", modifier = Modifier.size(16.dp)) }) else null,
        shape = CircleShape,
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.onBackground,
            selectedLabelColor = MaterialTheme.colorScheme.background,
            selectedTrailingIconColor = MaterialTheme.colorScheme.background,
        ),
    )
}
