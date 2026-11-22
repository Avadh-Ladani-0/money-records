package com.moneyrecords.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.domain.model.Category
import com.moneyrecords.app.domain.repository.ComboIcon
import com.moneyrecords.app.ui.icons.AppIcons
import com.moneyrecords.app.ui.icons.CategoryIcon
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

/**
 * Confirms combining [first] + [second]. The result lives inside [first] (counts toward it, not
 * toward [second]); [onSwap] flips the order. The name defaults to "First-Second" and is editable.
 */
@Composable
fun CombineCategoryDialog(
    first: Category,
    second: Category,
    onSwap: () -> Unit,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(first.id, second.id) { mutableStateOf("${first.name}-${second.name}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Combine categories") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    Box(
                        Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { CategoryIcon(ComboIcon.key(first.iconKey, second.iconKey), size = 26.dp) }
                    Column(Modifier.weight(1f)) {
                        Text("${first.name} + ${second.name}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Counts in ${first.name}, not in ${second.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MrTextField(value = name, onValueChange = { name = it.take(40) }, label = "Name")
                TextButton(onClick = onSwap) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                    Text("  Count in ${second.name} instead")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text("Create", color = MaterialTheme.colorScheme.onBackground)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Compact "new category" form used inside the category picker. */
@Composable
fun QuickCategoryForm(
    initialName: String,
    onCreate: (name: String, iconKey: String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf("other") }
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        SectionLabel("New category")
        MrTextField(value = name, onValueChange = { name = it.take(40) }, label = "Name")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            AppIcons.categoryKeys.forEach { key ->
                val selected = key == icon
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .selectable(selected = selected, onClick = { icon = key }, role = Role.RadioButton),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.forKey(key), contentDescription = AppIcons.label(key),
                        tint = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            GhostButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            PillButton("Create", onClick = { onCreate(name.trim(), icon) }, enabled = name.isNotBlank(), modifier = Modifier.weight(1f))
        }
    }
}

/** "+ New" cell matching the category grid's cells. */
@Composable
fun NewCategoryCell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = "New category")
            .padding(vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Add, contentDescription = null) }
        Spacer(Modifier.height(Space.xs))
        Text("New", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
