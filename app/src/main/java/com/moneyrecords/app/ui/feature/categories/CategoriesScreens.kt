package com.moneyrecords.app.ui.feature.categories

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.rememberHaptics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.moneyrecords.app.ui.components.PickerField
import com.moneyrecords.app.ui.components.pickerTiles
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.model.CategoryKind
import com.moneyrecords.app.domain.model.DeleteOutcome
import com.moneyrecords.app.ui.components.ChoiceChip
import com.moneyrecords.app.ui.components.CombineCategoryDialog
import com.moneyrecords.app.domain.repository.ComboIcon
import com.moneyrecords.app.ui.icons.CategoryIcon
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Row
import com.moneyrecords.app.ui.components.EditorTopBar
import com.moneyrecords.app.ui.components.GhostButton
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.MrTextField
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.SegmentedPills
import com.moneyrecords.app.ui.components.Tag
import com.moneyrecords.app.ui.icons.AppIcons
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

private fun CategoryKind.label() = if (this == CategoryKind.EXPENSE) "Expense" else "Income"

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onAdd: (CategoryKind) -> Unit,
    onOpen: (Long) -> Unit,
    onMessage: (String) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    var combineFirst by remember { mutableStateOf<Long?>(null) }
    var combineSecond by remember { mutableStateOf<Long?>(null) }
    val haptics = rememberHaptics()
    LaunchedEffect(viewModel) { viewModel.messages.collect(onMessage) }
    val byId = state.active.associateBy { it.id }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg)) {
        item {
            ScreenHeader(
                title = "Categories",
                onBack = onBack,
                actions = { IconButton(onClick = { onAdd(state.kind) }) { Icon(Icons.Outlined.Add, contentDescription = "Add category") } },
            )
        }
        item {
            SegmentedPills(listOf(CategoryKind.EXPENSE, CategoryKind.INCOME), state.kind, { it.label() }, { combineFirst = null; viewModel.setKind(it) })
            Spacer(Modifier.height(Space.md))
            val first = combineFirst?.let(byId::get)
            AnimatedContent(targetState = first, label = "combineBanner") { picked ->
                if (picked != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(start = Space.md),
                    ) {
                        Text("Combine “${picked.name}” with… tap another main category", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { combineFirst = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onBackground) }
                    }
                } else {
                    Text("Long-press a main category to combine it with another.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(Space.md))
        }
        if (!state.loading && state.active.isEmpty()) item {
            EmptyState(
                title = "No ${state.kind.label().lowercase()} categories",
                message = "Tap + to add one — or archived ones can be restored below.",
            )
        }
        // Same order as the record picker: each combined category right after the category it counts in.
        items(pickerTiles(state.active), key = { it.id }) { parent ->
            val isCombo = ComboIcon.isCombo(parent.iconKey)
            val combinable = !isCombo
            Column(Modifier.animateItem()) {
                ListRow(
                    title = parent.name,
                    iconKey = parent.iconKey,
                    onLongClickLabel = if (combinable) "Combine with another category" else null,
                    subtitle = when {
                        combineFirst == parent.id -> "Selected to combine"
                        isCombo -> "Combined · counts in ${byId[parent.parentId]?.name ?: "its first category"}"
                        parent.children.isEmpty() -> null
                        else -> "${parent.children.size} subcategories"
                    },
                    onClick = {
                        when {
                            combineFirst == null -> onOpen(parent.id)
                            combinable && combineFirst != parent.id -> { haptics.select(); combineSecond = parent.id }
                        }
                    },
                    onLongClick = {
                        if (combinable) {
                            haptics.longPress()
                            combineFirst = parent.id
                        }
                    },
                )
                parent.children.forEach { child ->
                    ListRow(title = child.name, iconKey = child.iconKey, onClick = { if (combineFirst == null) onOpen(child.id) },
                        modifier = Modifier.padding(start = Space.xxl))
                }
            }
        }
        if (state.archived.isNotEmpty()) {
            item { Spacer(Modifier.height(Space.lg)); SectionLabel("Archived") }
            items(state.archived, key = { "a${it.id}" }) { c ->
                ListRow(title = c.name, iconKey = c.iconKey, onClick = { onOpen(c.id) }, trailing = { Tag("Archived") })
            }
        }
        item { Spacer(Modifier.height(Space.xxl)) }
    }

    val first = combineFirst?.let(byId::get)
    val second = combineSecond?.let(byId::get)
    if (first != null && second != null) {
        CombineCategoryDialog(
            first = first,
            second = second,
            onSwap = { combineFirst = second.id; combineSecond = first.id },
            onConfirm = { name -> viewModel.combine(first.id, second.id, name); combineFirst = null; combineSecond = null },
            onDismiss = { combineSecond = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorScreen(
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: CategoryEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CategoryEditorEvent.Saved -> onClose()
                is CategoryEditorEvent.Removed -> {
                    onMessage(if (event.outcome == DeleteOutcome.DELETED) "Category deleted" else "Category is in use, so it was archived instead")
                    onClose()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        EditorTopBar(
            title = (if (state.isNew) "New " else "Edit ") + state.kind.label().lowercase() + " category",
            onClose = onClose,
            onDelete = if (state.isNew) null else ({ confirmDelete = true }),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            if (state.isNew) {
                SegmentedPills(listOf(false, true), state.combineMode, { if (it) "Combined" else "Normal" }, viewModel::setCombineMode)
            }

            if (state.combineMode || state.isCombo) {
                CombinedSection(state, onFirst = viewModel::onFirst, onSecond = viewModel::onSecond, onSwap = viewModel::swap)
            }

            MrTextField(value = state.name, onValueChange = viewModel::onName, label = "Name")

            if (!state.combineMode && !state.isCombo && !state.hasChildren) {
                SectionLabel("Inside")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ChoiceChip("Top level", selected = state.parentId == null, onClick = { viewModel.onParent(null) })
                    state.parentOptions.forEach { p ->
                        ChoiceChip(p.name, selected = state.parentId == p.id, onClick = { viewModel.onParent(p.id) }, icon = AppIcons.forKey(p.iconKey))
                    }
                }
            }

            if (!state.combineMode && !state.isCombo) {
                SectionLabel("Icon")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    AppIcons.categoryKeys.forEach { key ->
                        val selected = key == state.iconKey
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .selectable(selected = selected, onClick = { viewModel.onIcon(key) }, role = Role.RadioButton),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AppIcons.forKey(key), contentDescription = AppIcons.label(key),
                                tint = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            if (!state.isNew) {
                GhostButton(if (state.archived) "Unarchive category" else "Archive category", onClick = viewModel::toggleArchived, modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { Text(it, color = MrTheme.colors.danger, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(Space.lg))
        }
        PillButton("Save", onClick = viewModel::save, enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Space.screen))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete category?") },
            text = { Text("If records, budgets or subcategories use it, it will be archived instead so nothing is lost.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete", color = MrTheme.colors.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Combined-category section of the editor. For a new one: pick First and Second (main categories),
 * swap, and see the split icon. For an existing one: show its icon and where it counts.
 */
@Composable
private fun CombinedSection(
    state: CategoryEditorState,
    onFirst: (Long) -> Unit,
    onSecond: (Long) -> Unit,
    onSwap: () -> Unit,
) {
    val first = state.first
    val second = state.second
    val previewKey = when {
        state.isCombo -> state.iconKey
        first != null && second != null -> ComboIcon.key(first.iconKey, second.iconKey)
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        if (!state.isCombo) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                MainCategoryDropdown("First", state.combinable, state.firstId, onFirst, Modifier.weight(1f))
                IconButton(onClick = onSwap, enabled = first != null || second != null) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = "Swap first and second")
                }
                MainCategoryDropdown("Second", state.combinable, state.secondId, onSecond, Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Box(
                Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (previewKey != null) CategoryIcon(previewKey, size = 28.dp)
                else Text("?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                when {
                    state.isCombo -> "Combined category · counts in ${state.comboParentName ?: "its first category"}"
                    first != null && second != null -> "Counts in ${first.name}, not in ${second.name}"
                    else -> "Pick two main categories. The new one counts in the first."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MainCategoryDropdown(
    label: String,
    options: List<com.moneyrecords.app.domain.model.Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    Box(modifier) {
        PickerField(
            label = label,
            value = selected?.name,
            icon = null,
            iconKey = selected?.iconKey,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            options.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name) },
                    leadingIcon = { CategoryIcon(c.iconKey) },
                    onClick = { onSelect(c.id); expanded = false },
                )
            }
        }
    }
}
