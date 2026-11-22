package com.moneyrecords.app.ui.feature.budgets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moneyrecords.app.ui.components.EmptyState
import com.moneyrecords.app.ui.components.Glyphs
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SettingsAction
import com.moneyrecords.app.ui.theme.Space

@Composable
fun BudgetsScreen(onOpenSettings: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
    ) {
        item { ScreenHeader(title = "Budgets", eyebrow = "This month", actions = { SettingsAction(onOpenSettings) }) }
        item {
            EmptyState(
                title = "No budgets yet",
                message = "Set a monthly limit for a category and watch it fill up dot by dot.",
                glyph = Glyphs.Chart,
            )
        }
    }
}
