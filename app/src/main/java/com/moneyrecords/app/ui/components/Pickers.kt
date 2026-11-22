package com.moneyrecords.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.repository.ComboIcon
import com.moneyrecords.app.ui.icons.CategoryIcon
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

/** Segmented control: equal-width pills inside a rounded track. */
@Composable
fun <T> SegmentedPills(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .selectable(selected = isSelected, onClick = { onSelect(option) }, role = Role.Tab),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Arranges categories for pickers: each combined category ("Home-Food") becomes its own tile right
 * after its parent, and is removed from the parent's subcategory list so it isn't shown twice.
 * Ordinary subcategories stay inside their parent.
 */
fun pickerTiles(categories: List<Category>): List<Category> = categories.flatMap { parent ->
    val (combos, plain) = parent.children.partition { ComboIcon.isCombo(it.iconKey) }
    listOf(parent.copy(children = plain)) + combos
}

/**
 * Category icon grid. Selecting a category that has subcategories reveals them as chips
 * directly under its row; the parent itself stays selectable ("All").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryGrid(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    /** Long-press on a top-level cell (used to start combining). */
    onLongPress: ((Long) -> Unit)? = null,
    /** A cell outlined in the accent colour (the first pick while combining). */
    markedId: Long? = null,
    /** Extra cell appended after the categories, e.g. "+ New". */
    trailingCell: (@Composable (Modifier) -> Unit)? = null,
) {
    val expandedParent = categories.firstOrNull { parent ->
        parent.id == selectedId || parent.children.any { it.id == selectedId }
    }?.takeIf { it.children.isNotEmpty() }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        val cells: List<Category?> = categories + if (trailingCell != null) listOf(null) else emptyList()
        cells.chunked(columns).forEach { cellRow ->
            val row = cellRow.filterNotNull()
            Row(Modifier.fillMaxWidth()) {
                cellRow.forEach { category ->
                    if (category == null) {
                        trailingCell!!(Modifier.weight(1f))
                    } else {
                        val selected = category.id == selectedId || category.children.any { it.id == selectedId }
                        CategoryCell(
                            category, selected,
                            marked = category.id == markedId,
                            onClick = { onSelect(category.id) },
                            onLongClick = onLongPress?.let { { it(category.id) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(columns - cellRow.size) { Spacer(Modifier.weight(1f)) }
            }
            AnimatedVisibility(visible = expandedParent != null && expandedParent in row) {
                val parent = expandedParent ?: return@AnimatedVisibility
                FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    ChoiceChip("All ${parent.name}", selected = selectedId == parent.id, onClick = { onSelect(parent.id) })
                    parent.children.forEach { child ->
                        ChoiceChip(child.name, selected = selectedId == child.id, onClick = { onSelect(child.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    category: Category,
    selected: Boolean,
    marked: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .padding(vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape,
                )
                .then(if (marked) Modifier.border(2.dp, MrTheme.colors.accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            CategoryIcon(
                category.iconKey,
                tint = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
            )
            if (category.children.isNotEmpty()) {
                // Small accent dot marks categories that have subcategories.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .background(MrTheme.colors.accent, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            category.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Small tappable value chip, e.g. a date or time in an editor. */
@Composable
fun ValueChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
