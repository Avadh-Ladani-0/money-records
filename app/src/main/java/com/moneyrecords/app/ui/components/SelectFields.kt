package com.moneyrecords.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.view.HapticFeedbackConstants
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import com.moneyrecords.app.domain.repository.ComboIcon
import com.moneyrecords.app.ui.icons.CategoryIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.domain.model.Account
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.ui.icons.AppIcons
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

/** A field that looks like an outlined input but opens a picker when tapped. */
@Composable
fun PickerField(
    label: String,
    value: String?,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Choose",
    /** When set, the icon is drawn from this category icon key (supports combined icons). */
    iconKey: String? = null,
) {
    Surface(
        modifier = modifier.heightIn(min = 60.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (value == null) MaterialTheme.colorScheme.outline else MrTheme.colors.hairline),
        onClick = onClick,
    ) {
        Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            if (value != null && (iconKey != null || icon != null)) {
                if (iconKey != null) CategoryIcon(iconKey, size = 20.dp)
                else Icon(icon!!, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(Space.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Account picker as a dropdown menu: icon, name and current balance per account. */
@Composable
fun AccountDropdown(
    label: String,
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    Box(modifier) {
        PickerField(
            label = label,
            value = selected?.name,
            icon = selected?.let { AppIcons.forKey(it.iconKey) },
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            if (accounts.isEmpty()) {
                DropdownMenuItem(text = { Text("No accounts yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { expanded = false }, enabled = false)
            }
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(account.name, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.size(Space.lg))
                            AmountText(
                                account.balance,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (account.balance < 0) MrTheme.colors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(AppIcons.forKey(account.iconKey), contentDescription = null) },
                    trailingIcon = if (account.id == selectedId) ({ Icon(Icons.Outlined.Check, contentDescription = "Selected") }) else null,
                    onClick = { onSelect(account.id); expanded = false },
                )
            }
        }
    }
}

/**
 * Bottom-sheet category picker: search, recently used, and the full grid.
 * - Tap a category without subcategories to pick it; one with subcategories expands first.
 * - "+ New" (or "Create …" from search) adds a category and picks it.
 * - Long-press a main category, then tap another, to combine them (see [CombineCategoryDialog]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    title: String,
    categories: List<Category>,
    recentIds: List<Long>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    onCreate: (name: String, iconKey: String) -> Unit,
    onCombine: (firstId: Long, secondId: Long, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    var query by remember { mutableStateOf("") }
    val tiles = remember(categories) { pickerTiles(categories) }
    var expandedParent by remember {
        mutableStateOf(tiles.firstOrNull { p -> p.children.any { it.id == selectedId } }?.id)
    }
    var creatingName by remember { mutableStateOf<String?>(null) }
    var combineFirst by remember { mutableStateOf<Long?>(null) }
    var combineSecond by remember { mutableStateOf<Long?>(null) }
    val all = remember(categories) { categories.flatMap { p -> listOf(p to null) + p.children.map { it to p } } }
    val byId = remember(all) { all.associate { (c, _) -> c.id to c } }
    // Only main (top-level) categories that aren't already combined can be combined.
    val combinable = remember(categories) { categories.filterNot { ComboIcon.isCombo(it.iconKey) }.map { it.id }.toSet() }
    fun pick(id: Long) { onPick(id); onDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .padding(horizontal = Space.screen)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)

            val creating = creatingName
            when {
                creating != null -> QuickCategoryForm(
                    initialName = creating,
                    onCreate = { name, icon -> onCreate(name, icon); onDismiss() },
                    onCancel = { creatingName = null },
                )

                combineFirst != null -> {
                    val first = byId.getValue(combineFirst!!)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Combine “${first.name}” with… tap another category",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { combineFirst = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onBackground) }
                    }
                    CategoryGrid(
                        categories = categories.filter { it.id in combinable },
                        selectedId = null,
                        markedId = combineFirst,
                        onSelect = { id -> if (id != combineFirst) combineSecond = id },
                    )
                }

                else -> {
                    MrTextField(value = query, onValueChange = { query = it }, label = "Search categories")
                    if (query.isNotBlank()) {
                        val q = query.trim()
                        val matches = all.filter { (c, _) -> c.name.contains(q, ignoreCase = true) }
                        matches.forEach { (category, parent) ->
                            ListRow(
                                title = category.name,
                                subtitle = parent?.name?.let { "in $it" },
                                iconKey = category.iconKey,
                                onClick = { pick(category.id) },
                                trailing = if (category.id == selectedId) ({ Icon(Icons.Outlined.Check, contentDescription = "Selected") }) else null,
                            )
                        }
                        if (matches.none { (c, _) -> c.name.equals(q, ignoreCase = true) }) {
                            ListRow(
                                title = "Create “$q”",
                                subtitle = "Add it as a new category",
                                icon = Icons.Outlined.Add,
                                onClick = { creatingName = q },
                            )
                        }
                    } else {
                        val recents = recentIds.mapNotNull(byId::get).take(6)
                        if (recents.isNotEmpty()) {
                            SectionLabel("Recent")
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                recents.forEach { c ->
                                    ChoiceChip(
                                        c.name, selected = c.id == selectedId, onClick = { pick(c.id) },
                                        leading = { CategoryIcon(c.iconKey, size = 18.dp) },
                                    )
                                }
                            }
                        }
                        SectionLabel("All categories · long-press to combine")
                        CategoryGrid(
                            categories = tiles,
                            // While a parent is expanded, highlight it; otherwise highlight the current pick.
                            selectedId = expandedParent ?: selectedId,
                            onSelect = { id ->
                                val parent = tiles.firstOrNull { it.id == id }
                                when {
                                    parent != null && parent.children.isNotEmpty() && expandedParent != id -> expandedParent = id
                                    else -> pick(id)
                                }
                            },
                            onLongPress = { id ->
                                if (id in combinable) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    combineFirst = id
                                }
                            },
                            trailingCell = { modifier -> NewCategoryCell(onClick = { creatingName = "" }, modifier = modifier) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.lg))
        }
    }

    val first = combineFirst?.let(byId::get)
    val second = combineSecond?.let(byId::get)
    if (first != null && second != null) {
        CombineCategoryDialog(
            first = first,
            second = second,
            onSwap = { combineFirst = second.id; combineSecond = first.id },
            onConfirm = { name ->
                onCombine(first.id, second.id, name)
                combineSecond = null
                onDismiss()
            },
            onDismiss = { combineSecond = null },
        )
    }
}
