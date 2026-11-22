package com.moneyrecords.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

data class BottomBarItem(val label: String, val icon: ImageVector)

/** Minimal bottom bar: icon + tiny label; the selected tab gets a red dot. */
@Composable
fun MrBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = 1.dp, color = MrTheme.colors.hairline)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .selectableGroup(),
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val tint by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tabTint",
                    )
                    val dot by animateColorAsState(
                        if (selected) MrTheme.colors.accent else Color.Transparent,
                        label = "tabDot",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = selected, onClick = { onSelect(index) }, role = Role.Tab),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterVertically),
                    ) {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        Text(item.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = tint)
                        Box(Modifier.size(4.dp).background(dot, CircleShape))
                    }
                }
            }
        }
    }
}
