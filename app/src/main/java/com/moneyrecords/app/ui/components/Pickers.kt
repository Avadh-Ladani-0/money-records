package com.moneyrecords.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.stateDescription
import com.moneyrecords.app.ui.icons.CategoryBadge
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

/** Segmented control: equal-width options with a pill that slides to the selected one. */
@Composable
fun <T> SegmentedPills(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Space.xs),
    ) {
        val cell = maxWidth / options.size
        val offset by animateDpAsState(cell * index, spring(dampingRatio = 0.85f, stiffness = 500f), label = "segment")
        // Sized to the row (which grows with large fonts); the pill fills its height.
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .offset(x = offset)
                    .width(cell)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground),
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { option ->
                val isSelected = option == selected
                val textColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "segmentText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(CircleShape)
                        .selectable(
                            selected = isSelected,
                            onClick = { if (!isSelected) haptics.select(); onSelect(option) },
                            role = Role.RadioButton,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(option), style = MaterialTheme.typography.labelLarge, color = textColor)
                }
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
    val haptics = rememberHaptics()
    val badge by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onBackground else MrTheme.colors.badge,
        label = "cellBadge",
    )
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { haptics.select(); onClick() },
                onLongClick = onLongClick?.let { { haptics.longPress(); it() } },
                onLongClickLabel = if (onLongClick != null) "Combine with another category" else null,
                role = Role.RadioButton,
            )
            .semantics {
                this.selected = selected
                if (category.children.isNotEmpty()) stateDescription = if (selected) "Selected, has subcategories" else "Has subcategories"
            }
            .padding(vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CategoryBadge(
                category.iconKey,
                size = 48.dp,
                background = if (selected) badge else null,
                tint = if (selected) MaterialTheme.colorScheme.background else null,
                modifier = if (marked) Modifier.border(2.dp, MrTheme.colors.accent, CircleShape) else Modifier,
            )
            if (category.children.isNotEmpty()) {
                // Small dot marks categories that have subcategories.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
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
            modifier = Modifier.padding(horizontal = Space.xs / 2),
        )
    }
}

/** Small tappable value chip, e.g. a date or time in an editor. */
@Composable
fun ValueChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
